package ovh.battistella.elan.ui.screens.progression

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.formatDateShort
import ovh.battistella.elan.domain.isoWeekKey
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.theme.ElanTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ProgressionScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm() = ProgressionViewModel(repos.sessions, repos.settings, Clock.fixed(Instant.ofEpochMilli(now), zone))

    private fun seed(exercise: String, startedAt: Long, weightKg: Double, difficulty: Difficulty?) = runBlocking {
        repos.sessions.saveMuscuSession(
            id = null,
            startedAt = startedAt,
            patch = SessionUpdate { endedAt = startedAt + 1_800_000; durationSec = 1800 },
            sets = listOf(TestSupport.muscuSetInput(exercise, 1, 10, weightKg, difficulty)),
        )
    }

    @Test
    fun `état vide - invitation à démarrer une séance`() {
        var starts = 0
        compose.setContent { ElanTheme { ProgressionScreen(contentPadding = PaddingValues(), viewModel = vm(), onStartMuscu = { starts++ }) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.progression_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.progression_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.progression_empty_action)).performClick()
        assertEquals(1, starts)
        compose.onNodeWithText(text(R.string.progression_changes_title)).assertDoesNotExist()
    }

    @Test
    fun `liste des exercices et carte des changements de la semaine`() {
        seed("Goblet squat", now - 10 * day, 20.0, Difficulty.MOYEN)
        seed("Goblet squat", now - 3 * day, 22.5, Difficulty.FACILE)
        seed("Tractions", now - 5 * day, 0.0, Difficulty.DUR)
        runBlocking {
            repos.settings.setAutoProgressionState(
                AutoProgressionState(
                    week = isoWeekKey(now, zone),
                    changes = listOf(
                        ProgressionChange("Goblet squat", ProgressKind.LOAD, 22.5, 25.0, Direction.UP),
                        ProgressionChange("Gainage planche", ProgressKind.TIME, 30.0, 25.0, Direction.DOWN),
                    ),
                ),
            )
        }
        var opened: String? = null
        compose.setContent { ElanTheme { ProgressionScreen(contentPadding = PaddingValues(), viewModel = vm(), onOpenExercise = { opened = it }) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.progression_changes_title)).assertIsDisplayed()
        compose.onNodeWithText("Goblet squat 22,5 → 25 kg").assertIsDisplayed()
        compose.onNodeWithText("Gainage planche 30 → 25 s").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.progression_changes_text, "1 exercice renforcé · 1 allégé")).assertIsDisplayed()

        compose.onNodeWithText("Goblet squat").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.progression_sessions_plural, 2, formatDateShort(now - 3 * day))).assertIsDisplayed()
        compose.onNodeWithText("22,5 kg").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.progression_easy_a11y)).assertIsDisplayed()
        compose.onNodeWithText("Tractions").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.progression_sessions, 1, formatDateShort(now - 5 * day))).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.progression_hard_a11y)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.progression_empty_title)).assertDoesNotExist()

        compose.onNodeWithText("Tractions").performClick()
        assertEquals("Tractions", opened)
    }
}
