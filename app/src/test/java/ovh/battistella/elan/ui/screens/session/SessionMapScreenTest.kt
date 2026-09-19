package ovh.battistella.elan.ui.screens.session

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import ovh.battistella.elan.R
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class SessionMapScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int) = context.getString(res)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val t0 = 1_700_000_000_000L

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(id: Long) = SessionMapViewModel(SavedStateHandle(mapOf("id" to id)), repos.sessions)

    @Test
    fun `tracé interactif plein écran et retour flottant`() {
        val id = runBlocking {
            val id = db.sessionDao().insert(TestSupport.session(type = ActivityType.COURSE, startedAt = t0))
            db.trackPointDao().insertAll(
                listOf(
                    TestSupport.trackPoint(id, ts = t0),
                    TestSupport.trackPoint(id, ts = t0 + 10_000, lat = 48.858, lon = 2.353),
                ),
            )
            id
        }
        var backs = 0
        compose.setContent { ElanTheme { SessionMapScreen(contentPadding = PaddingValues(), viewModel = vm(id), onBack = { backs++ }) } }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_empty)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.map_back)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `sans tracé - message vide`() {
        val id = runBlocking { db.sessionDao().insert(TestSupport.session(type = ActivityType.MUSCU, startedAt = t0)) }
        compose.setContent { ElanTheme { SessionMapScreen(contentPadding = PaddingValues(), viewModel = vm(id)) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.map_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertDoesNotExist()
    }
}
