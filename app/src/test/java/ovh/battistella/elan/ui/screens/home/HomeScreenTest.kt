package ovh.battistella.elan.ui.screens.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.elan.R
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.screens.FakeHeartRatePort
import ovh.battistella.elan.ui.theme.ElanTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val zone: ZoneId = ZoneId.systemDefault()
    private val wednesday = LocalDate.of(2026, 9, 16).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    private val monday = LocalDate.of(2026, 9, 14).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(now: Long = wednesday, heart: FakeHeartRatePort = FakeHeartRatePort()) = HomeViewModel(
        repos.sessions, repos.settings, heart, Clock.fixed(Instant.ofEpochMilli(now), zone), SnackbarController(), context,
    )

    @Test
    fun `onboarding visible sans marqueur et masqué après C'est parti`() {
        compose.setContent { ElanTheme { HomeScreen(contentPadding = androidx.compose.foundation.layout.PaddingValues(), viewModel = vm()) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.onboarding_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_go)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.onboarding_title)).assertDoesNotExist()
        assertEquals("1", runBlocking { repos.settings.getSetting("onboarding_done") })
    }

    @Test
    fun `accueil vide - en-tête repos grille et état vide`() {
        runBlocking { repos.settings.setOnboardingDone(true) }
        var started: ActivityType? = null
        compose.setContent {
            ElanTheme {
                HomeScreen(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    viewModel = vm(),
                    onStartOuting = { started = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.home_greeting)).assertIsDisplayed()
        compose.onNodeWithText("Connecter").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_today, "mercredi")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_rest)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_rest_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_empty_title)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Vélo").performScrollTo().performClick()
        assertEquals(ActivityType.VELO, started)
    }

    @Test
    fun `séance du jour planifiée - bouton Démarrer et grille Reprendre avec brouillon`() {
        runBlocking {
            repos.settings.setOnboardingDone(true)
            repos.settings.setSetting("muscu_draft", """{"version":1,"startedAt":1,"elapsedSec":10,"exercises":[]}""")
            db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, startedAt = monday - 86_400_000L, distanceM = 12_000.0))
        }
        var startedOuting: ActivityType? = null
        compose.setContent {
            ElanTheme {
                HomeScreen(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    viewModel = vm(now = monday), // lundi : « Vélo 1h » au planning par défaut
                    onStartOuting = { startedOuting = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Vélo 1h").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_active_recovery)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_last_session, "hier")).assertIsDisplayed()
        // Le brouillon muscu transforme « Muscu » en « Reprendre » dans la grille.
        compose.onNodeWithText(text(R.string.home_resume)).assertIsDisplayed()
        compose.onNodeWithText("Muscu").assertDoesNotExist()

        compose.onNodeWithText(text(R.string.home_start_session)).performClick()
        assertEquals(ActivityType.VELO, startedOuting)
        // Séance récente listée.
        compose.onNodeWithText("12,0 km").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `objectifs et pastille cardio connectée`() {
        runBlocking {
            repos.settings.setOnboardingDone(true)
            repos.settings.setGoals(listOf(Goal("g1", GoalMetric.SESSIONS, GoalPeriod.WEEK, 4.0, GoalActivity.ALL)))
            db.sessionDao().insert(TestSupport.session(type = ActivityType.MUSCU, startedAt = wednesday - 3_600_000L))
        }
        compose.setContent {
            ElanTheme {
                HomeScreen(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    viewModel = vm(heart = FakeHeartRatePort(bpm = 128, connected = true)),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Fréquence cardiaque 128 battements par minute").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_goals_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4 séances / semaine").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25 %").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_goal_done, "1")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `planning personnalisé muscu - Démarrer ouvre le template`() {
        runBlocking {
            repos.settings.setOnboardingDone(true)
            repos.settings.setCustomWeekPlan(List(7) { PlannedSession.Muscu("Dos", ovh.battistella.elan.domain.TemplateId.DOS_LOMBAIRE) })
        }
        var template: ovh.battistella.elan.domain.TemplateId? = null
        compose.setContent {
            ElanTheme {
                HomeScreen(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    viewModel = vm(),
                    onStartMuscu = { template = it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Dos").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_start_session)).performClick()
        assertEquals(ovh.battistella.elan.domain.TemplateId.DOS_LOMBAIRE, template)
    }
}
