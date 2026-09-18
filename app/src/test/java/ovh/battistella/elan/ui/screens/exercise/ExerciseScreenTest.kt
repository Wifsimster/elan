package ovh.battistella.elan.ui.screens.exercise

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
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
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.adviceLabel
import ovh.battistella.elan.domain.ProgressionAdvice
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ExerciseScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
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

    private fun vm(name: String) = ExerciseViewModel(SavedStateHandle(mapOf("name" to name)), repos.sessions)

    private fun seed(exercise: String, startedAt: Long, sets: List<Pair<Int, Double>>, difficulty: Difficulty?): Long = runBlocking {
        repos.sessions.saveMuscuSession(
            id = null,
            startedAt = startedAt,
            patch = SessionUpdate { endedAt = startedAt + 1_800_000; durationSec = 1800 },
            sets = sets.mapIndexed { i, (reps, kg) -> TestSupport.muscuSetInput(exercise, i + 1, reps, kg, difficulty) },
        )
    }

    @Test
    fun `sans séance - guide du programme et état vide`() {
        compose.setContent { ElanTheme { ExerciseScreen(contentPadding = PaddingValues(), viewModel = vm("Goblet squat")) } }
        compose.waitForIdle()

        compose.onNodeWithText("Goblet squat").assertIsDisplayed()
        compose.onNodeWithText("Quadriceps").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_execution).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_empty_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_record)).assertDoesNotExist()
    }

    @Test
    fun `avec historique - résumé, 1RM, conseil, courbe et séances`() {
        seed("Goblet squat", now - 20 * day, listOf(10 to 20.0), Difficulty.MOYEN)
        val middleId = seed("Goblet squat", now - 10 * day, listOf(6 to 25.0, 8 to 22.5), Difficulty.FACILE)
        seed("Goblet squat", now - 3 * day, listOf(8 to 22.5), Difficulty.FACILE)
        var opened: Long? = null
        compose.setContent {
            ElanTheme { ExerciseScreen(contentPadding = PaddingValues(), viewModel = vm("Goblet squat"), onOpenSession = { opened = it }) }
        }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.exercise_record)).performScrollTo().assertIsDisplayed()
        // Record 25 kg (aussi en étiquette de barre), actuel 22,5 kg, +2,5 kg depuis le début.
        compose.onAllNodesWithText("25 kg").onFirst().assertIsDisplayed()
        compose.onNodeWithText("+2,5 kg").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_1rm_record)).performScrollTo().assertIsDisplayed()
        // 1RM record : 25 × (1 + 6/30) = 30 ; actuel : 22,5 × (1 + 8/30) = 28,5 → 29.
        compose.onNodeWithText("30 kg").assertIsDisplayed()
        compose.onNodeWithText("29 kg").assertIsDisplayed()
        compose.onNodeWithText(adviceLabel(ProgressionAdvice.AUGMENTE)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_last_session, "Facile")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_max_per_session)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_sessions)).performScrollTo().assertIsDisplayed()

        // Séances : la plus récente d'abord ; la séance médiane a 2 séries, ≈ 30 kg 1RM, 330 kg vol.
        compose.onNodeWithText("22,5 kg × 8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25 kg × 6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_1rm_approx, 30)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_sets_count_plural, 2)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_volume, 330)).assertIsDisplayed()
        compose.onNodeWithText("25 kg × 6").performClick()
        assertEquals(middleId, opened)
        compose.onNodeWithText(text(R.string.exercise_empty_title)).assertDoesNotExist()
    }

    @Test
    fun `sans ressenti - invitation à noter`() {
        seed("Mon exo perso", now - 2 * day, listOf(12 to 10.0), null)
        compose.setContent { ElanTheme { ExerciseScreen(contentPadding = PaddingValues(), viewModel = vm("Mon exo perso")) } }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.exercise_rate_hint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.exercise_execution).uppercase()).assertDoesNotExist()
    }
}
