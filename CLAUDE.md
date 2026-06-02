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
```

There is **no test suite** in this repo.

### Development build is mandatory

Bluetooth (`react-native-ble-plx`) does **not** work in Expo Go — you must run a development build (`npx expo run:android`, requires Android SDK, or `eas build --profile development --platform android`). GPS and SQLite also work in the dev build. Primary target is **Android**; iOS is configured but secondary.

## Architecture

A 100% local, offline activity tracker for cycling (GPS) and weight training. **No backend, no accounts, no network** — all data lives in a local SQLite DB. Keep it that way: do not add network/cloud dependencies without an explicit request.

### Layering

- `src/app/` — Expo Router routes (file-based, typed routes enabled). Screens orchestrate UI + call into `lib/`.
- `src/lib/` — framework-agnostic domain logic: `db.ts` (all SQLite access), `ble.ts` (BLE manager + Heart Rate frame parsing), `geo.ts`, `calories.ts`, `format.ts`, `time.ts`, `activity.ts`, `types.ts` (domain types).
- `src/hooks/` — stateful React glue: `use-heart-rate.tsx` (BLE context), `use-gps-tracker.ts`, `use-stopwatch.ts`, `use-theme.ts`.
- `src/components/` — reusable UI, themed via `useTheme()` + inline `StyleSheet`-style objects (see below).

Path aliases: `@/*` → `src/*`, `@/assets/*` → `assets/*`.

### Navigation (`src/app/_layout.tsx`)

Root Stack wraps everything in `GestureHandlerRootView` → `ThemeProvider` → **`HeartRateProvider`**. The `(tabs)` group (Accueil / Historique / Réglages) is the anchor. Live-session screens `velo` and `muscu` are presented as `fullScreenModal`. `session/[id]` is the detail page.

### Heart-rate BLE is a single global connection

`HeartRateProvider` (mounted once at the root) owns the **one** BLE connection, shared by every screen via `useHeartRate()`. It scans by the standard Heart Rate service UUID, persists the last device under the `hr_device` setting, and **auto-reconnects on launch**. Never open a second `BleManager` connection from a screen — consume the context.

### Data flow for a live session

A session screen (`velo.tsx` / `muscu.tsx`) accumulates samples in memory during the workout, then on stop: `createSession()` → `updateSession()` with aggregates → bulk-insert detail rows (`insertTrackPoints` / `replaceMuscuSets`) → `router.replace()` to `session/[id]`. Sessions with `endedAt IS NULL` are "in progress" and are excluded from history/stats queries.

### SQLite (`src/lib/db.ts`)

Single lazily-opened connection (`getDb()`), WAL mode, foreign keys on. Schema is versioned via `PRAGMA user_version` inside `migrate()` — **to change the schema, add a new `if (version < N)` block and bump the pragma; never edit an existing migration block.** Tables: `sessions`, `track_points` and `muscu_sets` (both `ON DELETE CASCADE` from `sessions`), and a key/value `settings` table (profile + paired device are JSON values there). All DB access goes through this module's exported functions — don't write raw SQL in screens.

### Styling & theming

The app follows the **PULSE design system — see `DESIGN.md`** (read it before touching UI). Not NativeWind despite a `global.css` (that file only declares CSS font variables for web). Components style with plain inline style objects and pull every value from `src/constants/theme.ts`: colors via `useTheme()` (→ `Colors[light|dark]`, follows the system scheme), and `Radius` / `Elevation` / `Type` / `Motion` / `Gradients` via static imports — **never hard-code a color, font size, radius or shadow in a screen.** Interactive elements use `<PressableScale>` (spring press + haptic, `lib/haptics`); primary actions use the gradient `Button`; gradients render via the local `<Gradient>` (react-native-svg, no network). Icons are `@expo/vector-icons` MaterialCommunityIcons.

### Web caveats (`metro.config.js`)

`expo-sqlite` on web needs the `.wasm` asset ext and COOP/COEP headers (for `SharedArrayBuffer`) — both are wired in `metro.config.js`. Don't remove them if web output is kept.

## Conventions

- React Compiler is enabled (`app.json` → `experiments.reactCompiler`); avoid manual memoization patterns that fight it.
- Code comments and UI copy are in **French** — match that.
- TypeScript is `strict`.
