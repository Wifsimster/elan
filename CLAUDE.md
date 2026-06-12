# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

**Expo SDK 56 — APIs changed.** Read the versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any Expo/React Native code; do not rely on memory of older SDKs.

## Commands

```bash
npm install
npx expo run:android   # build + run the development build on device/emulator
npm run android        # same (expo start --android)
npx tsc --noEmit       # type check
npx expo lint          # lint (eslint flat config, eslint-config-expo)
npm test               # jest (préset jest-expo) — fonctions pures de src/lib/ uniquement
```

Tests live in `__tests__/lib/` and only cover the framework-agnostic `src/lib/` layer (no components, no hooks); `expo-sqlite` is mocked over `better-sqlite3` where needed.

### Development build is mandatory

Bluetooth (`react-native-ble-plx`) does **not** work in Expo Go — you must run a development build (`npx expo run:android`, requires Android SDK, or `eas build --profile development --platform android`). GPS and SQLite also work in the dev build. Primary target is **Android**; iOS is configured but secondary.

## Architecture

A local-first, offline activity tracker for cycling (GPS) and weight training. **No backend, no accounts** — all data lives in a local SQLite DB. The only network access is **opt-in and off by default**: a self-hosted S3 backup (`lib/s3.ts` + `lib/backup.ts`) and an online MapLibre basemap (`lib/map.ts` — OpenFreeMap by default once enabled, or a self-hosted style URL); both stay off until the user turns them on, and both degrade gracefully when absent. Strava import is **file-based** (GPX/TCX picked from disk), not an API sync. Keep it this way: do not add new network/cloud dependencies, telemetry, or third-party API sync without an explicit request.

### Layering

- `src/app/` — Expo Router routes (file-based, typed routes enabled). Screens orchestrate UI + call into `lib/`.
- `src/lib/` — framework-agnostic domain logic: `db.ts` (all SQLite access), `ble.ts` (BLE manager + Heart Rate frame parsing), `geo.ts` (haversine + route decimation), `gps-filter.ts` (GPS consolidation: accuracy gating, outlier rejection, Kalman smoothing, anchor-based distance, elevation hysteresis), `gps-task.ts` (background location task: expo-task-manager + Android foreground service), `calories.ts` (MET estimate + HR zones), `route-projection.ts` (geo→SVG screen projection), `program.ts` (built-in workout templates), `wheel-sizes.ts` (cadence→speed gear tables), `s3.ts` (pure-JS S3 client, AWS SigV4 via `js-sha256`), `backup.ts` (snapshot export/import + S3 sync), `coach-export.ts` (builds a curated Markdown training report + raw JSON export, shared via the OS share sheet — for feeding a Claude Code project), `map.ts` (MapLibre style-URL setting), `health-connect.ts` (opt-in export of finished sessions to Android Health Connect — on-device, no cloud), `strava/` (`parse.ts` regex GPX/TCX parser + `import.ts` normalization/dedup), `format.ts`, `time.ts`, `activity.ts`, `haptics.ts`, `types.ts` (domain types).
- `src/hooks/` — stateful React glue: `use-heart-rate.tsx` (BLE HR context), `use-cadence-speed.tsx` (BLE CSC sensor context), `use-backup.tsx` (S3 backup context), `use-data-export.tsx` (writes the coach report/JSON to cache + opens the share sheet), `use-gps-tracker.ts`, `use-stopwatch.ts`, `use-strava-import.tsx`, `use-theme.ts`, `use-color-scheme.ts`.
- `src/components/` — reusable UI, themed via `useTheme()` + inline `StyleSheet`-style objects (see below).

Path aliases: `@/*` → `src/*`, `@/assets/*` → `assets/*`.

### Navigation (`src/app/_layout.tsx`)

Root Stack wraps everything in `GestureHandlerRootView` → `ThemeProvider` → **`HeartRateProvider`** → **`BackupProvider`**. The `(tabs)` group (Accueil / Historique / Réglages) is the anchor. Live-session screens `velo` and `muscu` are presented as `fullScreenModal`. `session/[id]` is the detail page; `exercise/[name]` and `progression` are the muscu drill-down pages.

### Heart-rate BLE is a single global connection

`HeartRateProvider` (mounted once at the root) owns the **one** BLE connection, shared by every screen via `useHeartRate()`. It scans by the standard Heart Rate service UUID, persists the last device under the `hr_device` setting, and **auto-reconnects on launch**. Never open a second `BleManager` connection from a screen — consume the context. The cycling cadence/speed (BLE CSC) sensor is handled the same way via `use-cadence-speed.tsx`; `lib/wheel-sizes.ts` converts wheel revolutions to speed.

### Data flow for a live session

A session screen (`velo.tsx` / `muscu.tsx`) accumulates samples in memory during the workout, then on stop: `createSession()` → `updateSession()` with aggregates → bulk-insert detail rows (`insertTrackPoints` / `replaceMuscuSets`) → `router.replace()` to `session/[id]`. Sessions with `endedAt IS NULL` are "in progress" and are excluded from history/stats queries.

GPS recording (`use-gps-tracker.ts`) runs through an **Android foreground service** (`lib/gps-task.ts`, persistent notification — no background-location permission needed) so fixes keep arriving with the screen off; `watchPositionAsync` is only a fallback (web / service refused). Every fix goes through `lib/gps-filter.ts` (`GpsConsolidator`) before touching distance/elevation aggregates — don't accumulate raw fixes in screens, and keep the filter free of React imports.

### SQLite (`src/lib/db.ts`)

Single lazily-opened connection (`getDb()`), WAL mode, foreign keys on. Schema is versioned via `PRAGMA user_version` inside `migrate()` (**currently at version 5**) — **to change the schema, add a new `if (version < N)` block and bump the pragma; never edit an existing migration block.** Migration history: v1 = base tables; v2 = cadence/speed columns (`avgCadence`/`maxCadence` on `sessions`, `cadence` on `track_points`); v3 = Strava import (`source`/`externalId` on `sessions` + a unique partial index on `externalId` for idempotent re-import); v4 = body-weight journal (`body_measurements` table — `logBodyWeight()` mirrors the latest entry into the profile's `weightKg`, which stays the single source consumed by calories/recommendations); v5 = `movingTimeSec` on `sessions` (temps « en mouvement » vélo hors arrêts, façon Strava — `lib/moving-time.ts` ; le bloc rétro-calcule la colonne et la vitesse moyenne « en mouvement » des sorties existantes depuis leurs points GPS). Tables: `sessions`, `track_points` and `muscu_sets` (both `ON DELETE CASCADE` from `sessions`), `body_measurements` (weight journal, drives the `/poids` page), and a key/value `settings` table (profile, paired devices, backup config, map style URL are all JSON values there). All DB access goes through this module's exported functions — don't write raw SQL in screens.

### Maps: MapLibre with an SVG fallback

Route display goes through `components/route-map.tsx`, which is an orchestrator: if a MapLibre style URL is configured (`lib/map.ts` → `map_style_url` setting) and there are ≥2 points, it renders `components/maplibre-route.tsx` (`@maplibre/maplibre-react-native`, route as a GeoJSON LineString + start/end markers); otherwise it falls back to a pure-SVG polyline projected via `lib/route-projection.ts` — **no network, no API key**. The online basemap is **opt-in and off by default** (`getMapStyleUrl()` returns `''` until enabled): in Réglages the user toggles it on (defaulting to the free, open-source, keyless **OpenFreeMap** style, `OPENFREEMAP_STYLE_URL`) or pastes their own self-hosted style URL. When a basemap is shown, `maplibre-route.tsx` must render the **mandatory OSM attribution** (`mapAttribution()`). Keep the SVG fallback working — and the off-by-default behaviour — when touching maps; do not hardcode a personal or third-party tile server as the silent default.

### Backup: opt-in self-hosted S3

`lib/s3.ts` is a dependency-light S3 client implementing AWS Signature V4 by hand (HMAC via `js-sha256`, no AWS SDK, path-style URLs — works with MinIO/SeaweedFS). `lib/backup.ts` wraps `db.exportAll()`/`importAll()` into a versioned snapshot (`format: 1`) and PUTs/GETs it. `BackupProvider` (`use-backup.tsx`) exposes config/status and the manual `backupNow()`/`restore()` actions used by the settings screen; `autoBackup()` is a fire-and-forget call after a session ends. Config + last-run status live in `settings` (`backup_s3`, `backup_last`) and are **excluded from the snapshot** so credentials never round-trip.

### Health Connect: opt-in on-device export (Android only)

`lib/health-connect.ts` mirrors finished sessions into **Android Health Connect** (the on-device health store — no network, consistent with the project rule). Off by default: the `health_connect` setting only turns on from the Réglages toggle, which is also the only place permissions are requested (`enableHealthConnect()`). After a session is saved, `velo.tsx`/`muscu.tsx` call `exportSessionToHealthConnect()` fire-and-forget (same contract as `autoBackup()`: never throws, never blocks navigation). `buildHealthRecords()` is pure and unit-tested. The native module (`react-native-health-connect`) is lazy-loaded and everything no-ops on iOS/web or when Health Connect is absent — the settings card simply disappears off-Android. Manifest bits come from the lib's own plugin (permission-rationale intent filter) plus `plugins/withHealthConnect.js` (Android 14+ `ViewPermissionUsageActivity` alias); health `WRITE_*` permissions are declared in `app.json`, and `expo-build-properties` pins `minSdkVersion` 26 (Health Connect requirement).

### Strava import (file-based)

`use-strava-import.tsx` drives: file picker → `lib/strava/parse.ts` (regex-based GPX/TCX parsing, no XML lib) → `lib/strava/import.ts` (normalize, sanity-filter, compute elevation gain/calories, derive a stable `externalId`) → `db.insertImportedSession()` which returns `'imported' | 'duplicate'`. This is **not** an OAuth/API sync, intentionally — treat a request to add live Strava sync as a network/cloud change requiring explicit confirmation.

### Styling & theming

The app follows the **PULSE design system — see `DESIGN.md`** (read it before touching UI). Not NativeWind despite a `global.css` (that file only declares CSS font variables for web). Components style with plain inline style objects and pull every value from `src/constants/theme.ts`: colors via `useTheme()` (→ `Colors[light|dark]`, follows the system scheme), and `Radius` / `Elevation` / `Type` / `Motion` / `Gradients` via static imports — **never hard-code a color, font size, radius or shadow in a screen.** Interactive elements use `<PressableScale>` (spring press + haptic, `lib/haptics`); primary actions use the gradient `Button`; gradients render via the local `<Gradient>` (react-native-svg, no network). Icons are `@expo/vector-icons` MaterialCommunityIcons.

### Web caveats (`metro.config.js`)

`expo-sqlite` on web needs the `.wasm` asset ext and COOP/COEP headers (for `SharedArrayBuffer`) — both are wired in `metro.config.js`. Don't remove them if web output is kept.

## Conventions

- React Compiler is enabled (`app.json` → `experiments.reactCompiler`); avoid manual memoization patterns that fight it.
- Code comments and UI copy are in **French** — match that.
- TypeScript is `strict`.
