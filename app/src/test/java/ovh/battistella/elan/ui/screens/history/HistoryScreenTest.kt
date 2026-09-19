package ovh.battistella.elan.ui.screens.history

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ovh.battistella.elan.R
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.theme.ElanTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HistoryScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int) = context.getString(res)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm() = HistoryViewModel(repos.sessions, Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()))

    @Test
    fun `historique vide - état vide avec import Strava`() {
        var settings = 0
        compose.setContent { ElanTheme { HistoryScreen(contentPadding = PaddingValues(), viewModel = vm(), onOpenSettings = { settings++ }) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.nav_history)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_progression_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_import_strava)).performClick()
        assertEquals(1, settings)
    }

    @Test
    fun `lignes puis filtre type sans résultat`() {
        runBlocking {
            db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, startedAt = now - 3_600_000L, distanceM = 25_000.0))
            db.sessionDao().insert(TestSupport.session(type = ActivityType.COURSE, startedAt = now - 7_200_000L, distanceM = 8_000.0))
        }
        var opened: Long? = null
        compose.setContent { ElanTheme { HistoryScreen(contentPadding = PaddingValues(), viewModel = vm(), onOpenSession = { opened = it }) } }
        compose.waitForIdle()

        compose.onNodeWithText("25,0 km").assertIsDisplayed()
        compose.onNodeWithText("8,0 km").assertIsDisplayed()
        compose.onNodeWithText("Course à pied").performClick()
        assertTrue(opened != null)

        // Filtre « Muscu » : aucune séance ne correspond → état vide filtré, sans import.
        compose.onNodeWithText("Muscu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.history_empty_filtered_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_import_strava)).assertDoesNotExist()
        compose.onNodeWithText("25,0 km").assertDoesNotExist()
    }

    @Test
    fun `recherche filtre après debounce et la croix efface`() {
        runBlocking {
            db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, startedAt = now - 3_600_000L, notes = "Galibier"))
            db.sessionDao().insert(TestSupport.session(type = ActivityType.COURSE, startedAt = now - 7_200_000L, notes = "lac"))
        }
        compose.setContent { ElanTheme { HistoryScreen(contentPadding = PaddingValues(), viewModel = vm()) } }
        compose.waitForIdle()
        compose.onNodeWithText("Course à pied").assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.history_search_placeholder)).performTextInput("gali")
        // Sous le délai de 200 ms, la liste ne bouge pas.
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
        compose.onNodeWithText("Course à pied").assertIsDisplayed()

        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
        compose.onNodeWithText("Course à pied").assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.history_search_clear)).performClick()
        ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.history_search_clear)).assertDoesNotExist()
        compose.onNodeWithText("Course à pied").assertIsDisplayed()
    }
}
