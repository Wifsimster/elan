# Conventions Ondes — modèle pour Élan 2.0

Extrait de `~/ondes` (namespace `ovh.battistella.ondes`), l'app sœur en Kotlin + Compose. Tout ce qui suit est à reproduire dans `ovh.battistella.elan`, sauf mention contraire.

## 1. Build

- `build.gradle.kts` racine : `alias(...) apply false` pour android-application, kotlin-android, kotlin-compose, ksp, hilt.
- `settings.gradle.kts` : `pluginManagement` avec filtre `google { content { includeGroupByRegex(...) } }`, `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, `include(":app")`.
- `gradle.properties` : `-Xmx2048m`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `android.useAndroidX`, `android.nonTransitiveRClass`, `kotlin.code.style=official`, **`VERSION_NAME=x.y.z` = unique source de version**. Wrapper Gradle 8.11.1.
- `gradle/libs.versions.toml` : agp 8.9.3, kotlin 2.0.21, ksp 2.0.21-1.0.28, hilt 2.52 (+ hiltNavigationCompose 1.2.0, hiltWork 1.2.0), coreKtx 1.15.0, lifecycle 2.8.7, activityCompose 1.9.3, composeBom 2025.10.01, material3 **1.5.0-alpha14** (pin pour les API Expressive ; alpha15+ exige AGP 9.1), navigationCompose 2.8.5, room 2.6.1, coil 2.7.0, okhttp 4.12.0, coroutines 1.9.0, work 2.10.0, datastore 1.1.1, junit 4.13.2, robolectric 4.14.1, mockk 1.13.13, androidxTestExtJunit 1.2.1. Alias plats en kebab-case ; artefacts Compose sans `version.ref` (BOM) sauf material3.
- `app/build.gradle.kts` : `compileSdk 36`, `minSdk 26`, `targetSdk 36`, Java 17, `-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`, `buildFeatures { compose; buildConfig }`, `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }`, `androidResources { generateLocaleConfig = true }` (+ `res/resources.properties`), `debug { applicationIdSuffix = ".debug" }`, `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }`.
- versionCode depuis le semver :
  ```kotlin
  val appVersionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "0.0.0"
  val appVersionCode: Int = appVersionName.substringBefore("-").split(".").let { parts ->
      val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
      val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
      val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
      major * 1_000_000 + minor * 1_000 + patch
  }
  ```
- Signature : `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword) → variables d'environnement `KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD` → repli clé debug. `keystore.properties.example` commité, `*.jks` et `keystore.properties` ignorés.
- Schémas Room exportés + tests :
  ```kotlin
  sourceSets {
      getByName("test").resources.srcDir(layout.projectDirectory.dir("schemas"))
      getByName("androidTest").assets.srcDir(layout.projectDirectory.dir("schemas"))
  }
  testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }
  ksp { arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path) }
  ```
- `app/proguard-rules.pro` minimal et commenté : keeps uniquement sur crash constaté, `-assumenosideeffects class android.util.Log { v/d/i }`.

## 2. Architecture (packages)

```
OndesApp.kt              @HiltAndroidApp, Configuration.Provider (HiltWorkerFactory), canaux notif + tâches de démarrage dans onCreate
MainActivity.kt          @AndroidEntryPoint, enableEdgeToEdgeCompat(), POST_NOTIFICATIONS, onboarding gate, deep link
MainViewModel.kt         settings + showOnboarding (StateFlow<Boolean?>, null = chargement → rien dessiné)
common/SnackbarController.kt   bus snackbar + undo @Singleton
di/AppModule.kt          module Hilt unique : dispatcher IO injecté, OkHttp, Room + DAOs, migrations publiques (val MIGRATION_x_y)
data/local/              XDatabase.kt (+Converters), Entities.kt, Daos.kt ; schémas dans app/schemas/<fqcn>/<v>.json ; jamais fallbackToDestructiveMigration
data/settings/           SettingsRepository.kt : enum ThemeMode, data class XSettings (défauts), object Keys, Flow<XSettings>, setX(), snapshot()/restore()
data/backup/BackupManager.kt   JSON org.json avec "version" ; import = parser + valider tout (opt*), sanitiser les URL, puis db.withTransaction, réglages hors transaction
data/repository/         @Singleton class XRepository @Inject constructor(...) : Flow depuis DAO, suspend one-shot, withContext(io) + runCatching → Result<T>, db.withTransaction pour multi-tables
sync/                    Worker @HiltWorker (CancellationException relancée, runAttemptCount < MAX), Scheduler object (enable/disable périodique), Notifier object (createChannel(context) early-return < 26, notify, hasPermission)
ui/EdgeToEdge.kt         enableEdgeToEdgeCompat() + Window.setSystemBarsAppearance(darkTheme)  ← reprendre tel quel
ui/theme/Theme.kt        MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive()), dynamic color 31+, 3 CompositionLocals
ui/theme/Tokens.kt       @Immutable data class XColors/XSpacing/XShapes + staticCompositionLocalOf ; object XTheme { val colors/spacing/shapes @Composable @ReadOnlyComposable get() }
ui/navigation/           Routes (object + builders Uri.encode), enum TopLevelDestination(route, labelRes, icon), un seul Scaffold racine (snackbar host, bottom bar, contentWindowInsets ∪ displayCutout), innerPadding passé en contentPadding ; tabs : popUpTo(findStartDestination) saveState + launchSingleTop + restoreState
ui/components/           composants réutilisables
ui/screens/<feature>/    <Feature>Screen.kt + <Feature>ViewModel.kt
util/WebUrl.kt           allowlist de schémas pour les URL importées
```

## 3. Patrons

- ViewModel : `@HiltViewModel` + injection constructeur ; état lecture seule `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), défaut)` ; état UI local `MutableStateFlow(...).asStateFlow()` ; événements `MutableSharedFlow(extraBufferCapacity = N)` ; `SavedStateHandle` pour ce qui survit à la rotation ; `@ApplicationContext Context` pour résoudre les chaînes des snackbars. `MainViewModel` utilise `SharingStarted.Eagerly`.
- Screen : `viewModel: XViewModel = hiltViewModel()` en paramètre par défaut (injectable en test), `contentPadding: PaddingValues`, callbacks `onX: () -> Unit` — jamais de `NavController` dans un écran. `collectAsStateWithLifecycle()`.
- Permissions : `registerForActivityResult(RequestPermission())` gardé par SDK ; chaque poster de notification revérifie `checkSelfPermission`.
- Localisation : `values/` + `values-xx`, `generateLocaleConfig`, plurals via `getQuantityString`.
- Onboarding : `settings.onboardingDone` → `showOnboarding: StateFlow<Boolean?>` → `when (null → Unit; true → Onboarding; false → Root)`.

## 4. Tests et CI

- Tout sur JVM hôte : `app/src/test/java/...` miroir du package principal, un `XViewModelTest` + un `XScreenTest` par écran.
- `app/src/test/resources/robolectric.properties` → `sdk=34`.
- `testing/MainDispatcherRule.kt` (`TestWatcher`, `Dispatchers.setMain(StandardTestDispatcher())`, `.dispatcher` exposé, `runTest(mainDispatcher.dispatcher)`).
- `testing/TestSupport.kt` : `inMemoryDb()` (Room in-memory avec **Executor direct** pour requêtes et transactions), `repository(db, io, réseau mocké)` = vrai repository sur vraie base, fixtures d'entités avec défauts.
- ViewModel tests : `@RunWith(RobolectricTestRunner::class)`, `@get:Rule val mainDispatcher = MainDispatcherRule()`, construction manuelle (pas de Hilt), `@After` ferme la base, collecte gardée vivante via `backgroundScope.launch { vm.flow.collect {} }` + `advanceUntilIdle()`.
- Compose tests : `createComposeRule()`, `setContent { XTheme(dynamicColor = false) { XScreen(viewModel = vm) } }`, `onNodeWithText(context.getString(R.string.…))`.
- `MigrationTest` : base brute créée depuis le schéma JSON exporté (classpath test), migration réelle `AppModule.MIGRATION_*`, réouverture par Room = validation, assertions sur les données utilisateur.
- `.github/workflows/build.yml` : à chaque push, JDK 17 Temurin, `android-actions/setup-android@v4`, cache Gradle (clé `**/*.gradle*` + `libs.versions.toml`), tests d'abord (fail fast) → APK debug → APK release → AAB (décodage `KEYSTORE_BASE64` en `KEYSTORE_FILE` si secret) → `scripts/check-16kb-alignment.sh` → sur `main`, `dist/` + `sha256sum` + release roulante `latest` (softprops/action-gh-release@v2).
- `.github/workflows/release.yml` : sur `main`, `concurrency: release`, `fetch-depth: 0`, `npx --yes -p semantic-release@24 -p @semantic-release/exec -p @semantic-release/git semantic-release`.
- `.releaserc.json` : commit-analyzer + release-notes-generator ; `exec.prepareCmd = bash scripts/set-version.sh ${nextRelease.version} && ./gradlew assembleRelease && sha256sum` ; `@semantic-release/git` commite `gradle.properties` avec `chore(release): v${version} [skip ci]` ; `@semantic-release/github` attache APK + `SHA256SUMS.txt`. Pour Élan : ajouter `@semantic-release/changelog` avec les sections FR.
- Scripts : `set-version.sh` (sed `VERSION_NAME`), `check-16kb-alignment.sh` (readelf PT_LOAD ≥ 16384 sur chaque .so), `setup-upload-keystore.sh` (keytool + impression du bloc keystore.properties et des `gh secret set`).
- Docs : `SIGNING_SETUP.md`, `PLAY_STORE_CHECKLIST.md`, `store-listing.md`, `privacy-policy.md`, README (accroche + badges + téléchargement + captures + fonctionnalités + stack + arborescence).

## 5. Réutilisable tel quel (renommage de package)

`ui/EdgeToEdge.kt`, `ui/theme/Tokens.kt` + `Theme.kt` (changer les couleurs), la forme de `SettingsRepository`, `common/SnackbarController.kt`, le patron `BackupManager`, le montage WorkManager + Hilt (`Application : Configuration.Provider`, `HiltWorkerFactory`, retrait de `WorkManagerInitializer` dans le manifeste), la forme des helpers de notification, `MainDispatcherRule`/`TestSupport`/`robolectric.properties`/recette `MigrationTest`, `build.yml`/`release.yml`/`.releaserc.json`/scripts, le bloc signature + versionCode de `app/build.gradle.kts`, `util/WebUrl.kt`.

Ne pas copier : `playback/`, `download/`, `data/remote/` (RSS), le pin material3 alpha si les API Expressive ne servent pas, les keeps media3.
