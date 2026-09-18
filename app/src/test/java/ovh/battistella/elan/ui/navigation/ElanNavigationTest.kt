package ovh.battistella.elan.ui.navigation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
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
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.screens.FakeOutingPort
import ovh.battistella.elan.ui.screens.TestViewModelFactory
import ovh.battistella.elan.ui.screens.outing.OutingPhase
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ElanNavigationTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun label(res: Int) = context.getString(res)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val outing = FakeOutingPort()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        // Pas d'onboarding par-dessus les écrans dans ces tests.
        runBlocking { repos.settings.setOnboardingDone(true) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setRoot() {
        compose.setContent { ElanTheme { ElanRoot(viewModelFactory = TestViewModelFactory(db, outing = outing)) } }
        compose.waitForIdle()
    }

    @Test
    fun rootShowsTheThreeTabs() {
        setRoot()

        // L'accueil est la destination de départ : son en-tête et les trois onglets.
        compose.onNodeWithText(label(R.string.home_greeting)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.nav_home)).assertCountEquals(1)
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(1)
        compose.onAllNodesWithText(label(R.string.nav_settings)).assertCountEquals(1)
    }

    @Test
    fun tappingATabOpensItsScreen() {
        setRoot()

        compose.onNodeWithText(label(R.string.nav_history)).performClick()
        compose.waitForIdle()

        // Titre de l'écran + onglet ; l'accueil n'est plus affiché.
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(2)
        compose.onNodeWithText(label(R.string.home_greeting)).assertDoesNotExist()
    }

    @Test
    fun startingAnOutingHidesTheTabsAndBackReturnsHome() {
        setRoot()

        compose.onNodeWithText("Vélo").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(label(R.string.outing_start)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(0)

        // Sortie jamais démarrée : la croix quitte sans confirmation.
        compose.onNodeWithContentDescription(label(R.string.outing_quit)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.home_greeting)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(1)
    }

    @Test
    fun pendingRouteOpensTheOutingOnceAndIsConsumed() {
        var pending by mutableStateOf<String?>(null)
        var consumed = 0
        compose.setContent {
            ElanTheme {
                ElanRoot(
                    viewModelFactory = TestViewModelFactory(db, outing = outing),
                    pendingRoute = pending,
                    onPendingRouteConsumed = { consumed++; pending = null },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.home_greeting)).assertIsDisplayed()
        assertEquals(0, consumed)

        // Notification de sortie : l'écran de sortie s'ouvre, la route est consommée.
        pending = Routes.outing(ActivityType.VELO)
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.outing_start)).assertIsDisplayed()
        assertEquals(1, consumed)

        // Nouvel appui alors que la sortie est déjà ouverte : pas d'empilement,
        // la croix ramène directement à l'accueil.
        pending = Routes.outing(ActivityType.VELO)
        compose.waitForIdle()
        assertEquals(2, consumed)
        compose.onNodeWithContentDescription(label(R.string.outing_quit)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.home_greeting)).assertIsDisplayed()
    }

    @Test
    fun savedOutingReplacesItselfWithTheSessionDetail() {
        val id = runBlocking { db.sessionDao().insert(TestSupport.session(type = ActivityType.COURSE, startedAt = 1_700_000_000_000L)) }
        setRoot()

        compose.onNodeWithText("Course").performScrollTo().performClick()
        compose.waitForIdle()
        outing.update { copy(phase = OutingPhase.Idle, savedSessionId = id) }
        compose.waitForIdle()

        // Détail de la séance course (titre + corps), puis retour direct à l'accueil.
        compose.onAllNodesWithText("Course à pied").assertCountEquals(2)
        compose.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.home_greeting)).assertIsDisplayed()
    }

    @Test
    fun recentSessionOpensDetailThenMapAndBack() {
        val id = runBlocking {
            val id = db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, startedAt = 1_700_000_000_000L, distanceM = 15_000.0))
            db.trackPointDao().insertAll(
                listOf(
                    TestSupport.trackPoint(id, ts = 1_700_000_000_000L),
                    TestSupport.trackPoint(id, ts = 1_700_000_010_000L, lat = 48.858, lon = 2.353),
                ),
            )
            id
        }
        setRoot()

        // La ligne récente est en fin de page : on fait défiler jusqu'au bout pour
        // qu'elle sorte de sous la barre d'onglets avant de la toucher.
        compose.onNodeWithText("15,0 km").performScrollTo()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("15,0 km")))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1000f) }
        compose.waitForIdle()
        compose.onNodeWithText("15,0 km").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(label(R.string.session_expand_map)).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription(label(R.string.map_back)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.map_back)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(label(R.string.session_expand_map)).assertExists()
    }

    @Test
    fun catalogAndMuscuAreReachable() {
        setRoot()

        // Catalogue autonome, puis retour.
        compose.onNodeWithText(label(R.string.home_catalog_title)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.catalog_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        compose.waitForIdle()

        // Séance muscu plein écran (pas de barre d'onglets), sélecteur de programme visible.
        compose.onNodeWithText("Muscu").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.muscu_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.muscu_load_program)).assertIsDisplayed()
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(0)

        // Séance vide : la croix ramène à l'accueil sans dialogue.
        compose.onNodeWithContentDescription(label(R.string.muscu_quit)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(label(R.string.home_greeting)).assertIsDisplayed()
    }
}
