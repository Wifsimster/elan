package ovh.battistella.elan.ui.screens.weight

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.theme.ElanTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class WeightScreenTest {

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

    private fun vm() = WeightViewModel(repos.bodyWeight, repos.settings, Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()))

    @Test
    fun `journal vide - état vide, saisie invalide puis pesée enregistrée`() {
        compose.setContent { ElanTheme { WeightScreen(contentPadding = PaddingValues(), viewModel = vm()) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.weight_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.weight_new)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.weight_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.weight_current)).assertDoesNotExist()

        val field = compose.onNodeWithContentDescription(text(R.string.weight_input_a11y))
        field.performTextClearance()
        field.performTextInput("12")
        compose.onNodeWithText(text(R.string.weight_save)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.weight_invalid)).assertIsDisplayed()

        field.performTextClearance()
        field.performTextInput("76,4")
        compose.onNodeWithText(text(R.string.weight_save)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.weight_invalid)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.weight_empty_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.weight_current)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.weight_entries)).assertIsDisplayed()
        assertEquals(76.4, runBlocking { repos.bodyWeight.latestBodyMeasurement() }?.weightKg)
    }

    @Test
    fun `métriques, courbe, deltas et suppression confirmée`() {
        runBlocking {
            repos.bodyWeight.logBodyWeight(82.0, now - 90 * day)
            repos.bodyWeight.logBodyWeight(80.0, now - 40 * day)
            repos.bodyWeight.logBodyWeight(78.5, now - 10 * day)
            repos.bodyWeight.logBodyWeight(77.2, now)
        }
        compose.setContent { ElanTheme { WeightScreen(contentPadding = PaddingValues(), viewModel = vm()) } }
        compose.waitForIdle()

        compose.onAllNodesWithText("77,2 kg").assertCountEquals(2) // métrique « Actuel » + ligne du journal
        compose.onNodeWithText("−2,8 kg").assertIsDisplayed() // 30 jours : vs la pesée d'il y a 40 j
        compose.onNodeWithText("−4,8 kg").assertIsDisplayed() // depuis le début
        compose.onNodeWithText(text(R.string.weight_since_start)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.weight_evolution)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Poids : minimum 77,2, maximum 82", substring = true).assertExists()

        // Ligne de journal : delta vs la pesée précédente (−0,8) et suppression.
        compose.onNodeWithText("−1,3 kg").assertExists()
        compose.onNodeWithContentDescription(text(R.string.weight_delete_a11y, formatDateTime(now, true))).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.weight_delete_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.onNodeWithText(text(R.string.weight_delete_title)).assertDoesNotExist()
        assertEquals(4, runBlocking { repos.bodyWeight.listBodyMeasurements() }.size)

        compose.onNodeWithContentDescription(text(R.string.weight_delete_a11y, formatDateTime(now, true))).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.common_delete)).performClick()
        compose.waitForIdle()
        assertEquals(3, runBlocking { repos.bodyWeight.listBodyMeasurements() }.size)
        compose.onAllNodesWithText("78,5 kg").assertCountEquals(2)
    }
}
