package ovh.battistella.elan.ui

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.elan.R
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.domain.LatLon
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.navigation.ElanRoot
import ovh.battistella.elan.ui.navigation.Routes
import ovh.battistella.elan.ui.screens.FakeHeartRatePort
import ovh.battistella.elan.ui.screens.FakeOutingPort
import ovh.battistella.elan.ui.screens.TestViewModelFactory
import ovh.battistella.elan.ui.screens.outing.OutingPhase
import ovh.battistella.elan.ui.screens.outing.OutingUi
import ovh.battistella.elan.ui.theme.ElanTheme
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visite guidée en images : rend l'application entière ([ElanRoot], vraie
 * base Room en mémoire peuplée d'une semaine type) écran par écran, en sombre
 * et en clair, et écrit un PNG par écran dans `app/build/screenshots/`.
 *
 * Désactivée par défaut (rendu natif lent) ; à lancer à la main pour
 * régénérer `docs/screenshots/` ou revoir le design system :
 *
 *     ELAN_SCREENSHOTS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTourTest*'
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTourTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: sombre={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val zone: ZoneId = ZoneId.systemDefault()
    /** Mercredi 16 septembre 2026, 18 h. */
    private val now = LocalDate.of(2026, 9, 16).atStartOfDay(zone).plusHours(18).toInstant().toEpochMilli()
    private val day = 86_400_000L
    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private var veloId = 0L

    @Before
    fun setUp() {
        assumeTrue("ELAN_SCREENSHOTS absent : visite guidée ignorée", System.getenv("ELAN_SCREENSHOTS") != null)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        seed()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    /** Boucle de ~24 km autour d'un point, légèrement irrégulière. */
    private fun loop(n: Int = 160): List<LatLon> = List(n) { i ->
        val t = 2 * PI * i / n
        val r = 1 + 0.18 * sin(3 * t) + 0.07 * cos(7 * t)
        LatLon(45.90 + 0.030 * r * sin(t), 6.13 + 0.045 * r * cos(t) + 0.012 * sin(2 * t))
    }

    private fun seed() = runBlocking {
        repos.settings.setOnboardingDone(true)
        repos.settings.setProfile(Profile(weightKg = 72.0, heightCm = 178.0, maxHr = 188.0))
        repos.settings.setGoals(
            listOf(
                Goal("g1", GoalMetric.SESSIONS, GoalPeriod.WEEK, 4.0, GoalActivity.ALL),
                Goal("g2", GoalMetric.DISTANCE, GoalPeriod.WEEK, 60_000.0, GoalActivity.ALL),
            ),
        )
        val dao = db.sessionDao()
        // Semaine passée (tendances) et semaine en cours.
        dao.insert(TestSupport.session(type = ActivityType.VELO, startedAt = now - 8 * day, durationSec = 3900, movingTimeSec = 3700, distanceM = 26_400.0, avgSpeedKmh = 25.7, elevationGainM = 310.0, calories = 640.0))
        dao.insert(TestSupport.session(type = ActivityType.COURSE, startedAt = now - 9 * day, durationSec = 1900, movingTimeSec = 1850, distanceM = 5_300.0, avgSpeedKmh = 10.3, elevationGainM = 40.0, calories = 380.0))
        dao.insert(TestSupport.session(type = ActivityType.MARCHE, startedAt = now - 2 * day - 5 * 3_600_000L, durationSec = 2700, movingTimeSec = 2500, distanceM = 3_900.0, avgSpeedKmh = 5.6, elevationGainM = 60.0, calories = 190.0))
        dao.insert(TestSupport.session(type = ActivityType.COURSE, startedAt = now - 1 * day - 10 * 3_600_000L, durationSec = 2280, movingTimeSec = 2220, distanceM = 6_800.0, avgSpeedKmh = 11.0, elevationGainM = 55.0, calories = 470.0))
        val muscu = dao.insert(TestSupport.session(type = ActivityType.MUSCU, startedAt = now - 1 * day - 1 * 3_600_000L, durationSec = 2700, calories = 260.0))
        db.muscuSetDao().insertAll(
            listOf(
                TestSupport.muscuSet(muscu, "Goblet squat", 1, 10, 22.0, Difficulty.FACILE),
                TestSupport.muscuSet(muscu, "Goblet squat", 2, 10, 22.0, Difficulty.FACILE),
                TestSupport.muscuSet(muscu, "Goblet squat", 3, 9, 22.0, Difficulty.DUR),
                TestSupport.muscuSet(muscu, "Développé couché haltères", 1, 10, 14.0, Difficulty.FACILE),
                TestSupport.muscuSet(muscu, "Développé couché haltères", 2, 8, 14.0, Difficulty.DUR),
                TestSupport.muscuSet(muscu, "Gainage planche", 1, 45, 0.0, null),
            ),
        )
        val t0 = now - 3 * 3_600_000L
        veloId = dao.insert(
            TestSupport.session(
                type = ActivityType.VELO, startedAt = t0, durationSec = 3960, movingTimeSec = 3780,
                distanceM = 24_300.0, avgSpeedKmh = 23.1, elevationGainM = 285.0, calories = 610.0,
                notes = "Tour du lac, vent de face au retour.",
            ),
        )
        val route = loop()
        db.trackPointDao().insertAll(
            route.mapIndexed { i, p ->
                TestSupport.trackPoint(
                    veloId, ts = t0 + i * 24_000L, lat = p.lat, lon = p.lon,
                    altitude = 450.0 + 40 * sin(i / 18.0), speedKmh = 23.0 + 6 * sin(i / 9.0),
                    hr = 138.0 + 14 * sin(i / 14.0), cadence = 84.0 + 6 * cos(i / 7.0),
                )
            },
        )
    }

    private fun shoot(
        name: String,
        route: String? = null,
        outing: FakeOutingPort = FakeOutingPort(),
        bpm: Int? = 132,
        /** Texte amené à l'écran avant la capture (bas d'un écran défilant). */
        scrollTo: String? = null,
    ) {
        val factory = TestViewModelFactory(
            db,
            clock = Clock.fixed(Instant.ofEpochMilli(now), zone),
            outing = outing,
            heart = FakeHeartRatePort(bpm = bpm, connected = bpm != null),
        )
        // La route n'est posée qu'une fois le graphe de navigation construit.
        var pending by mutableStateOf<String?>(null)
        compose.setContent {
            ElanTheme(darkTheme = dark) {
                ElanRoot(viewModelFactory = factory, pendingRoute = pending, onPendingRouteConsumed = { pending = null })
            }
        }
        compose.waitForIdle()
        if (route != null) {
            pending = route
            compose.waitForIdle()
        }
        if (scrollTo != null) {
            compose.onNodeWithText(scrollTo).performScrollTo()
            compose.waitForIdle()
        }
        // Les animations infinies (halo GPS, pulsation) empêcheraient l'attente
        // d'inactivité : l'horloge est avancée à la main puis figée.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        // Dessin logiciel de la fenêtre entière (captureToImage attend un
        // rendu matériel que Robolectric ne signale pas).
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name-${if (dark) "sombre" else "clair"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun accueil() = shoot("01-accueil")

    @Test fun historique() = shoot("02-historique", Routes.HISTORY)

    @Test fun seanceVelo() = shoot("03-seance-velo", Routes.session(veloId))

    @Test fun seanceVeloGraphes() = shoot("03b-seance-velo-graphes", Routes.session(veloId), scrollTo = app.getString(R.string.session_notes).uppercase())

    @Test fun sortieEnCours() = shoot(
        "04-sortie-en-cours",
        Routes.outing(ActivityType.VELO),
        FakeOutingPort(
            OutingUi(
                phase = OutingPhase.Active, type = ActivityType.VELO, elapsedSec = 2_537, distanceM = 16_420.0,
                speedKmh = 27.4, maxSpeedKmh = 41.2, elevationGainM = 184.0, pointCount = 110, accuracyM = 4.0,
                gpsStatus = GpsStatus.TRACKING, livePath = loop().take(110), bpm = 146, cadenceRpm = 88, caloriesLive = 402.0,
            ),
        ),
    )

    @Test fun muscu() = shoot("05-muscu", Routes.muscu(TemplateId.FULLBODY_A))

    @Test fun progression() = shoot("06-progression", Routes.PROGRESSION)

    @Test fun reglages() = shoot("07-reglages", Routes.SETTINGS, bpm = null)
}
