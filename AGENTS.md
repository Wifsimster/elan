# AGENTS.md — conventions pour les agents

Élan 2.0 est une application Android **native** (Kotlin + Jetpack Compose),
projet Gradle à la racine du dépôt. Tout ce qui suit s'applique à n'importe
quel agent de code (Claude Code, Codex, Copilot…) qui touche ce dépôt.

## Le produit en trois lignes

Suivi sportif **local-first** : sorties GPS (vélo, course, marche) et
musculation, sans backend ni compte. Toute donnée vit dans la base Room
`elan.db`. Les seuls accès réseau sont **opt-in et désactivés par défaut** :
sauvegarde S3 auto-hébergée et fond de carte MapLibre. Ne pas ajouter de
dépendance réseau/cloud, de télémétrie ni de synchronisation tierce sans
demande explicite. L'import Strava est **par fichier**, jamais par API.

## Commandes

```bash
./gradlew testDebugUnitTest assembleDebug   # tests JVM + APK debug — la commande de référence
./gradlew testDebugUnitTest --tests 'ovh.battistella.elan.domain.*'   # un paquet
./gradlew assembleRelease                   # APK release (R8) → app/build/outputs/apk/release/
./gradlew bundleRelease                     # AAB Play Store (clé d'upload requise, voir docs/PUBLISHING.md)
./scripts/check-16kb-alignment.sh <aab|apk>          # alignement 16 Ko des .so
./scripts/check-deprecated-edge-to-edge.sh <apk|aab> # aucune API bord à bord obsolète
./scripts/check-bundle-size.sh <aab> [Mo]           # taille de téléchargement arm64 (seuil 15 Mo)
```

Tous les tests tournent sur la **JVM hôte** (JUnit 4, Robolectric `sdk=34`,
MockK, Compose UI test, MockWebServer) : aucun émulateur nécessaire. Une PR
n'est finie que si `./gradlew testDebugUnitTest assembleDebug` passe.

## Langue

- **Interface** (`app/src/main/res/values/strings*.xml`) et **commentaires**
  en **français**. Les ressources par défaut sont en français
  (`res/resources.properties` → `unqualifiedResLocale=fr-FR`) ; il n'y a pas
  d'autre locale.
- Messages de commit en français (voir plus bas).
- Noms de code (classes, fonctions, variables) en anglais, comme le SDK.

## Structure (`app/src/main/java/ovh/battistella/elan/`)

| Paquet | Contenu | Règle |
|--------|---------|-------|
| `domain/` | Logique **pure JVM** : `Activity`, `Calories`, `GpsFilter` (`GpsConsolidator`), `HrZones`, `MovingTime`, `Goals`, `Program`, `Exercises`, `AutoProgression`, `SessionRetype`, `Privacy`, `strava/` (décodeurs GPX/TCX/FIT)… | Aucun import Android ni Compose. Constantes et formules reproduites de l'app 1.x (`docs/port-spec/01-domaine.md`) ; un changement de seuil est un changement de comportement à tester. |
| `data/local/` | `ElanDatabase` (Room, `VERSION = 8`), `Entities.kt`, `Daos.kt`, `SessionQueryBuilder` | Jamais `fallbackToDestructiveMigration`. Schéma exporté dans `app/schemas/<fqcn>/<version>.json` (sur le classpath des tests). |
| `data/settings/` | `SettingsRepository` (table `settings` clé/valeur, `Keys`, `ElanSettings`), `SettingsJson`, `SettingsModels` | Pas de DataStore : les réglages voyagent avec la sauvegarde. Clés identiques à l'app 1.x. |
| `data/repository/` | `SessionRepository`, `BodyWeightRepository`, `SnapshotRepository` | `Flow` depuis les DAO, `suspend` one-shot, `db.withTransaction` pour le multi-tables. |
| `data/backup/`, `data/remote/`, `data/secrets/` | `BackupManager`, `BackupSnapshotCodec` (format 1), `BackupQr`, `S3Client`/`S3Signer` (SigV4 maison, OkHttp), `KeystoreSecretStore` | Secrets hors table `settings` et hors instantané ; `Keys.BACKUP_EXCLUDED` filtre à l'export et à l'import. |
| `data/export/` | `CoachExporter`, `GpxExporter`, `StravaImporter`, `FileShare` | Fichiers dans `cache/share/` (seul chemin exposé par le `FileProvider`). |
| `data/legacy/` | Reprise de `suivi-sport.db` (Expo) : `LegacyDatabaseImporter`, `LegacyDbReader`, `LegacySecretsReader`, `MigrationGate` | Voir `docs/MIGRATION-1.x.md`. Ne pas modifier sans mettre à jour `LegacyFixture` et les tests. |
| `tracking/` | `TrackingService` (premier plan, type `location`), `TrackingController` (phases, flush 20 s), `GpsSource`, `SessionRecovery`, `SessionFinalizer`, `LiveNotification`, `Stopwatch` | Une sortie survit au plantage : ligne créée `endedAt NULL`, flush périodique, finalisation atomique, récupération au démarrage. Pas de localisation en arrière-plan. |
| `sensors/ble/` | `HeartRateManager`, `CadenceSpeedManager` (singletons), `BleScanner`, `GattLink`, `GattFrames`, `BlePermissions` | Une seule connexion par type de capteur, partagée par les écrans via des ports. `BLUETOOTH_SCAN` reste `neverForLocation`. |
| `health/` | `HealthConnectManager`, `HealthConnectGateway`, `HealthRecords` | Opt-in, écriture seule, `clientRecordId` idempotents. |
| `maps/` | `MapStyle` (`OPENFREEMAP_STYLE_URL`, HTTPS seul), `MapLibreRouteView`, `MapSnapshots` | Fond de carte **off par défaut** ; attribution OSM obligatoire quand un fond est affiché ; le rendu hors-ligne (`RouteCanvas`) doit rester fonctionnel. |
| `sync/` | `BackupWorker`/`BackupScheduler` (WorkManager), `ReminderScheduler`/`ReminderReceiver`/`BootReceiver` (AlarmManager inexact), `AutoProgressionRunner`, `ProgressionNotifier` | Workers `@HiltWorker` ; canaux de notification créés tôt. |
| `ui/theme/` | `Tokens.kt` (PULSE : `PulseColors`, `PulseGradients`, `Radius`, `Spacing`, `Elevation`, `Motion`), `Theme.kt` (`ElanTheme`, `MaterialExpressiveTheme`), `Type.kt` (`PulseType`) | **Aucune couleur, taille ou rayon en dur dans un écran** : lire `DESIGN.md` avant toute UI. |
| `ui/components/` | Composants PULSE (`PulseButton`, `PulseCard`, `PulseChip`, `StatTile`, `RouteMap`, `Modifier.pressableScale()`…) | Tout élément interactif passe par `pressableScale` + haptique. |
| `ui/navigation/` | `Routes`, `ElanNavigation` (`ElanRoot`, un seul `Scaffold`), `OpenRoute` (liens `elan://`) | Les écrans reçoivent `contentPadding` et des callbacks, **jamais** le `NavController`. |
| `ui/screens/<feature>/` | `<Feature>Screen.kt` + `<Feature>ViewModel.kt` (`@HiltViewModel`), ports dans `common/` et `settings/SettingsPorts.kt` | `viewModel = hiltViewModel()` en paramètre par défaut (injectable en test) ; les écrans dépendent d'interfaces (ports) liées dans `ScreensModule`/`SettingsModule`. |
| `ui/icons/` | `MdiIcons` généré par `scripts/gen-mdi-icons.mjs` | Ne pas éditer à la main : ajouter le nom MDI au script et le relancer. |
| `di/` | `AppModule` (dispatcher, scope, `Clock`, OkHttp, Room + DAO, futures `MIGRATION_x_y`), `TrackingModule`, `BackupModule`, `HealthModule` | Un module par capacité ; `@BindsOptionalOf` pour les ports optionnels. |
| `ElanApp`, `MainActivity`, `StartupTasks` | Hilt + WorkManager, barrière de migration, tâches de démarrage | L'interface n'est montée qu'une fois `MigrationGate` en `Ready`. |

Tests : `app/src/test/java/…` miroir du paquet principal, un `XViewModelTest`
et un `XScreenTest` par écran ; échafaudage dans `testing/MainDispatcherRule.kt`
et `testing/TestSupport.kt` (vraie base Room en mémoire, vrais dépôts) ;
`data/local/MigrationTest.kt` et `SchemaTest.kt` gardent le schéma.

## Base de données et schéma Room

- `ElanDatabase.VERSION` vaut **8** (le schéma v7 de l'app 1.x, +1). Il n'y a
  pas encore de migration Room.
- Pour changer le schéma : incrémenter `VERSION`, ajouter une
  `val MIGRATION_8_9 = object : Migration(8, 9)` dans `di/AppModule.kt` et la
  brancher via `.addMigrations(...)`, laisser KSP exporter
  `app/schemas/ovh.battistella.elan.data.local.ElanDatabase/9.json`
  (**à committer**), ajouter un cas à `MigrationTest` qui ouvre le JSON de la
  version précédente, exécute la vraie migration et vérifie les données.
- Ne jamais modifier un JSON de schéma déjà committé.
- Les tables et colonnes portent les noms de l'app 1.x (`sessions`,
  `track_points`, `muscu_sets`, `body_measurements`, `settings`) : la
  sauvegarde S3 (format 1) et l'import legacy en dépendent.

## Design system PULSE

Source de vérité : `ui/theme/Tokens.kt`, `Theme.kt`, `Type.kt`, documentés dans
`DESIGN.md` (§ 7 pour la correspondance token → Kotlin). Couleurs via
`ElanTheme.colors`, dégradés via `PulseGradients`, texte via `PulseType`,
rayons/espacements/élévations via `Radius`/`Spacing`/`Elevation`, ressorts via
`Motion`. Une teinte d'activité = un sens (`velo`, `muscu`, `course`, `marche`,
`heart` réservé aux données cardio). Si un token manque, l'ajouter à
`Tokens.kt` plutôt que d'écrire un littéral.

## Dépendances

- Versions centralisées dans `gradle/libs.versions.toml` ; `material3` est
  épinglé en `1.5.0-alpha14` (API Expressive ; alpha15+ exige AGP 9.1).
- **Ne jamais ajouter une dépendance embarquant du code natif (`.so`) sans
  vérifier l'alignement 16 Ko** : `./gradlew bundleRelease` puis
  `./scripts/check-16kb-alignment.sh app/build/outputs/bundle/release/app-release.aab`.
  La CI échoue sinon, et Play refuse l'app.
- Surveiller la taille : `./scripts/check-bundle-size.sh` (arm64 < 15 Mo).
- Pas de Play Services au-delà du lecteur de codes (`play-services-code-scanner`),
  pas de Firebase, pas d'analytics.
- `app/proguard-rules.pro` reste minimal : un `-keep` n'est ajouté que sur un
  crash release constaté, avec un commentaire qui l'explique.

## Bord à bord

`enableEdgeToEdgeCompat()` (`ui/EdgeToEdge.kt`) remplace `enableEdgeToEdge()` :
ne pas appeler `Window.setStatusBarColor` / `setNavigationBarColor` ni leurs
accesseurs (avertissement Play Console). `scripts/check-deprecated-edge-to-edge.sh`
le vérifie sur le dex final.

## Commits et versions

- **Conventional Commits**, sujets en **français** : `feat(muscu): …`,
  `fix(sauvegarde): …`, `perf(gps): …`, `refactor:`, `docs:`, `test:`, `ci:`,
  `chore:`. `feat` → version mineure, `fix`/`perf` → patch, un pied
  `BREAKING CHANGE:` → majeure.
- **semantic-release** tourne sur `main` (`.github/workflows/release-native.yml`,
  `.releaserc.json`) : il calcule la version, écrit `VERSION_NAME` dans
  `gradle.properties` via `scripts/set-version.sh`, met à jour `CHANGELOG.md`
  (sections ✨ Fonctionnalités / 🐛 Corrections / ⚡ Performances / ⏪
  Annulations / ♻️ Refactoring), crée le tag et la release GitHub avec l'APK.
- **Ne jamais modifier `VERSION_NAME` ni `CHANGELOG.md` à la main** dans une
  PR ordinaire. `versionCode` dérive du semver dans `app/build.gradle.kts`
  (`major * 1_000_000 + minor * 1_000 + patch`).
- Ne jamais committer `*.jks`, `keystore.properties`, `local.properties`.

## Scripts (`scripts/`)

| Script | Rôle |
|--------|------|
| `set-version.sh <version>` | Écrit `VERSION_NAME` dans `gradle.properties` (appelé par semantic-release) |
| `setup-upload-keystore.sh [chemin]` | Génère la clé d'upload et imprime `keystore.properties` + `gh secret set` |
| `check-16kb-alignment.sh <aab\|apk>` | Vérifie l'alignement 16 Ko de chaque `.so` |
| `check-deprecated-edge-to-edge.sh <apk\|aab>` | Vérifie l'absence d'API bord à bord obsolètes dans le dex |
| `check-bundle-size.sh <aab> [Mo]` | Mesure la taille de téléchargement par ABI via bundletool |
| `gen-mdi-icons.mjs` | Génère les vector drawables `mdi_*.xml` et `ui/icons/MdiIcons.kt` (Node + réseau) |
| `feature-graphic.sh` | Génère le feature graphic Play Store (ImageMagick) |

`app-json-updater.cjs`, `sync-android-version.cjs` et `reset-project.js`
appartiennent à l'outillage Expo 1.x et disparaissent avec lui.

## Vie privée — invariants à ne pas casser

Aucun compte, serveur, publicité, télémétrie ni SDK tiers. Réseau opt-in
seulement (S3 HTTPS SigV4, fond de carte HTTPS ; `map_style_url` jamais
restaurée d'une sauvegarde). Localisation au premier plan seulement. BLE
`neverForLocation`. Health Connect opt-in, écriture seule. Suppression : par
séance, « Effacer toutes les séances », « Tout réinitialiser », désinstallation
(`allowBackup="false"`). `Log.v/d/i` supprimés en release.

## Documentation à tenir à jour

`README.md`, `DESIGN.md`, `docs/*.md` (`CAPTEURS`, `IMPORT`, `SAUVEGARDE`,
`EXPORT-COACH`, `MIGRATION-1.x`, `PUBLISHING`, `DATA_SAFETY`,
`LICENSES-ASSETS`), `PRIVACY.md`. Une permission ajoutée au manifeste se
reflète dans `README.md`, `docs/DATA_SAFETY.md` et `PRIVACY.md`. Les
`docs/port-spec/*.md` sont la référence de portage depuis la 1.x (lecture
seule).
