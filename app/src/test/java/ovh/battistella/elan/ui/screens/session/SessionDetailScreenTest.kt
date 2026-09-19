package ovh.battistella.elan.ui.screens.session

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.screens.settings.FakeExportPort
import ovh.battistella.elan.tracking.SessionFinalizer
import java.util.Optional
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SessionDetailScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val t0 = 1_700_000_000_000L
    private val finalizer = SessionFinalizer(Optional.empty(), Optional.empty(), CoroutineScope(Dispatchers.Unconfined))
    private val export = FakeExportPort()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(id: Long) = SessionDetailViewModel(SavedStateHandle(mapOf("id" to id)), repos.sessions, repos.settings, SnackbarController(), context, finalizer, export)

    private fun seedVelo(movingTimeSec: Int? = 3000, points: Int = 3): Long = runBlocking {
        val id = db.sessionDao().insert(
            TestSupport.session(
                type = ActivityType.VELO, startedAt = t0, durationSec = 3600, movingTimeSec = movingTimeSec,
                distanceM = 30_000.0, avgSpeedKmh = 25.0, elevationGainM = 400.0, calories = 600.0, notes = "Belle sortie",
            ),
        )
        db.trackPointDao().insertAll(
            List(points) { i ->
                TestSupport.trackPoint(id, ts = t0 + i * 10_000L, lat = 48.85 + i * 0.001, lon = 2.35 + i * 0.001, hr = 120.0 + i, speedKmh = 20.0 + i)
            },
        )
        id
    }

    private fun seedMuscu(): Long = runBlocking {
        val id = db.sessionDao().insert(TestSupport.session(type = ActivityType.MUSCU, startedAt = t0, durationSec = 2400))
        db.muscuSetDao().insertAll(
            listOf(
                TestSupport.muscuSet(id, "Goblet squat", 1, 10, 20.0, Difficulty.FACILE),
                TestSupport.muscuSet(id, "Goblet squat", 2, 9, 22.5, Difficulty.FACILE),
                TestSupport.muscuSet(id, "Gainage planche", 1, 30, 0.0, null),
            ),
        )
        id
    }

    @Test
    fun `sortie vélo - en-tête, records, tracé, stats, graphes, type et actions`() {
        val id = seedVelo()
        var map: Long? = null
        compose.setContent { ElanTheme { SessionDetailScreen(contentPadding = PaddingValues(), viewModel = vm(id), onOpenMap = { map = it }) } }
        compose.waitForIdle()

        // En-tête + corps + puce de type.
        compose.onAllNodesWithText("Vélo").assertCountEquals(3)
        compose.onNodeWithText(text(R.string.records_personal)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.session_expand_map)).performClick()
        assertEquals(id, map)

        // Temps en mouvement (50:00) et temps total (1:00:00) car l'écart dépasse 60 s.
        compose.onNodeWithText(text(R.string.session_moving)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("50:00").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_total_time)).assertIsDisplayed()
        compose.onNodeWithText("1:00:00").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_avg_speed)).assertIsDisplayed()
        compose.onNodeWithText("25,0").assertIsDisplayed()
        compose.onNodeWithText("400").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_effort)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(text(R.string.session_chart_speed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_chart_elevation)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_chart_hr)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zones cardiaques").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Belle sortie").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_type_title).uppercase()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_export_gpx)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_share)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_sets)).assertDoesNotExist()
    }

    @Test
    fun `temps total absent sous 60 s d'écart, pas d'export sans tracé`() {
        val id = seedVelo(movingTimeSec = 3570, points = 1)
        compose.setContent { ElanTheme { SessionDetailScreen(contentPadding = PaddingValues(), viewModel = vm(id)) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.session_moving)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_total_time)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.session_expand_map)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.session_export_gpx)).assertDoesNotExist()
    }

    @Test
    fun `séance muscu - séries, exercices, détail par exercice, pas de type`() {
        val id = seedMuscu()
        var exercise: String? = null
        compose.setContent { ElanTheme { SessionDetailScreen(contentPadding = PaddingValues(), viewModel = vm(id), onOpenExercise = { exercise = it }) } }
        compose.waitForIdle()

        compose.onAllNodesWithText("Musculation").assertCountEquals(2)
        compose.onNodeWithText(text(R.string.session_sets)).assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_exercises)).assertIsDisplayed()
        compose.onAllNodesWithText("2").assertCountEquals(2) // exercices + index de la 2e série
        compose.onNodeWithText(text(R.string.session_moving)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.session_type_title).uppercase()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.session_export_gpx)).assertDoesNotExist()

        compose.onNodeWithText("Goblet squat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Facile").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_volume, 403)).assertIsDisplayed()
        compose.onNodeWithText("22,5 kg").assertIsDisplayed()
        compose.onNodeWithText("Goblet squat").performClick()
        assertEquals("Goblet squat", exercise)
    }

    @Test
    fun `changement de type - confirmation détaillée puis chips mises à jour`() {
        val id = seedVelo()
        compose.setContent { ElanTheme { SessionDetailScreen(contentPadding = PaddingValues(), viewModel = vm(id)) } }
        compose.waitForIdle()

        compose.onNodeWithText("Course").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.session_retype_title, "course à pied")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_retype_text, "course à pied", text(R.string.session_retype_cadence))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.session_retype_confirm)).performClick()
        compose.waitForIdle()

        compose.onAllNodesWithText("Course à pied").assertCountEquals(2)
        assertEquals(ActivityType.COURSE, runBlocking { repos.sessions.getSession(id) }?.type)
    }

    @Test
    fun `suppression - confirmation puis retour`() {
        val id = seedMuscu()
        var backs = 0
        compose.setContent { ElanTheme { SessionDetailScreen(contentPadding = PaddingValues(), viewModel = vm(id), onBack = { backs++ }) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.session_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.session_delete_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(0, backs)

        compose.onNodeWithContentDescription(text(R.string.session_delete)).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        assertNull(runBlocking { repos.sessions.getSession(id) })
    }

    @Test
    fun `séance introuvable`() {
        compose.setContent { ElanTheme { SessionDetailScreen(contentPadding = PaddingValues(), viewModel = vm(404)) } }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.session_not_found)).assertIsDisplayed()
    }
}
