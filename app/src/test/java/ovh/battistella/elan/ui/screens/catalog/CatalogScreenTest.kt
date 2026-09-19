package ovh.battistella.elan.ui.screens.catalog

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
import ovh.battistella.elan.domain.CATALOG
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CatalogScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int) = context.getString(res)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `catalogue autonome - en-tête, compteur, fiche avec le profil des réglages, ajout vers la séance`() {
        // Profil force à 80 kg : goblet squat 0,3 × 80 × 1,15 = 27,6 → 28 kg, 5 × 3-6, repos 180 s.
        runBlocking { repos.settings.saveProfile(Profile(weightKg = 80.0, goal = TrainingGoal.FORCE)) }
        var added: String? = null
        var backs = 0
        compose.setContent {
            ElanTheme {
                CatalogScreen(
                    contentPadding = PaddingValues(),
                    viewModel = CatalogViewModel(repos.settings),
                    onBack = { backs++ },
                    onAddToSession = { added = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.catalog_title)).assertIsDisplayed()
        compose.onNodeWithText("${CATALOG.size} exercices").assertIsDisplayed()

        compose.onNodeWithContentDescription("Rechercher un exercice, un muscle").performTextInput("goblet")
        compose.waitForIdle()
        compose.onNodeWithText("5 × 3-6 ·").assertIsDisplayed()
        compose.onNodeWithText("28 kg").assertIsDisplayed()
        compose.onNodeWithText("Goblet squat").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("CONSEILLÉ · OBJECTIF FORCE").assertIsDisplayed()
        compose.onNodeWithText("Repos conseillé ~180 s entre les séries.").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.catalog_add_to_session)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("goblet-squat", added)

        compose.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        assertEquals(1, backs)
    }
}
