package ovh.battistella.elan.ui.screens.strength

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ovh.battistella.elan.R
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.MuscuDraft
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.tracking.SessionFinalizer
import ovh.battistella.elan.ui.screens.FakeHeartRatePort
import ovh.battistella.elan.ui.theme.ElanTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StrengthScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        // Pas de demande de permission par-dessus l'écran dans ces tests.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(template: String? = null, heart: FakeHeartRatePort = FakeHeartRatePort()) = StrengthViewModel(
        SavedStateHandle(mapOf("template" to template, "add" to null)),
        repos.sessions,
        repos.settings,
        TestSupport.progressionRunner(repos),
        SessionFinalizer(Optional.empty(), Optional.empty(), CoroutineScope(Dispatchers.Unconfined)),
        heart,
        Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()),
        context,
    )

    private fun show(vm: StrengthViewModel, onExit: () -> Unit = {}, onSaved: (Long) -> Unit = {}) {
        compose.setContent { ElanTheme { StrengthScreen(contentPadding = PaddingValues(), viewModel = vm, onExit = onExit, onSaved = onSaved) } }
        compose.waitForIdle()
    }

    @Test
    fun `séance vide - résumé, sélecteur de programme, carte d'ajout, contrôles`() {
        show(vm())

        compose.onNodeWithText(text(R.string.muscu_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_duration)).assertIsDisplayed()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.onNodeWithText("0 kg").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_load_program)).assertIsDisplayed()
        compose.onNodeWithText("Full-body A").assertIsDisplayed()
        compose.onNodeWithText("Cervicales / nuque").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_add_exercise)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_browse_catalog)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Développé couché").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_pause)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_finish)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.muscu_feeling)).assertDoesNotExist()
    }

    @Test
    fun `séance active - programme chargé, séries cochables, repos, ressenti, sélecteur masqué`() {
        val vm = vm()
        show(vm)

        compose.onNodeWithText("Full-body A").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.muscu_load_program)).assertDoesNotExist()
        compose.onNodeWithText("Goblet squat").assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.muscu_target, "3 × 8-12"))[0].assertIsDisplayed()
        compose.onNodeWithText("0/15").assertIsDisplayed()

        // Cocher la première série démarre le repos (90 s par défaut) et met à jour le résumé.
        compose.onAllNodesWithContentDescription(text(R.string.muscu_set_toggle_a11y, 1))[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1/15").assertIsDisplayed()
        compose.onNodeWithText("REPOS").assertIsDisplayed()
        compose.onNodeWithText("1:30").assertIsDisplayed()
        assertEquals(now + 90_000, vm.ui.value.restEndsAt)

        // Ressenti sur le premier exercice.
        compose.onAllNodesWithText(text(R.string.muscu_feeling))[0].assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.muscu_how_to_a11y, "Goblet squat")).assertIsDisplayed()

        // Brouillon en base.
        assertNotNull(runBlocking { repos.settings.snapshot().muscuDraft })
    }

    @Test
    fun `quitter - dialogue à trois choix, Mettre en pause conserve le brouillon et sort`() {
        var exits = 0
        val vm = vm()
        show(vm, onExit = { exits++ })

        // Défilement jusqu'en bas : la carte d'ajout passe au-dessus de la barre de contrôle flottante.
        compose.onNodeWithText("Squat").performScrollTo()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Squat").performClick()
        compose.waitForIdle()
        assertEquals(1, vm.ui.value.exercises.size)
        // La croix est remontée hors écran par le défilement : on la ramène d'abord.
        compose.onNodeWithContentDescription(text(R.string.muscu_quit)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(StrengthDialog.Exit, vm.ui.value.dialog)

        compose.onNode(hasText(text(R.string.muscu_exit_title)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.common_continue)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.muscu_exit_abandon)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.muscu_exit_pause)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertEquals(1, exits)
        val draft = runBlocking { repos.settings.snapshot().muscuDraft }
        assertNotNull(draft)
        assertEquals("Squat", exercisesFromJson(draft!!.exercises).single().name)
    }

    @Test
    fun `quitter une séance vide sort sans dialogue`() {
        var exits = 0
        show(vm(), onExit = { exits++ })
        compose.onNodeWithContentDescription(text(R.string.muscu_quit)).performClick()
        compose.waitForIdle()
        assertEquals(1, exits)
        compose.onNodeWithText(text(R.string.muscu_exit_title)).assertDoesNotExist()
    }

    @Test
    fun `reprise d'un brouillon - Reprendre affiché et exercices restaurés`() {
        runBlocking {
            repos.settings.setMuscuDraft(
                MuscuDraft(
                    startedAt = now - 60_000,
                    elapsedSec = 125.0,
                    exercises = exercisesToJson(listOf(exerciseFromName("Tractions"))),
                    hrSamples = emptyList(),
                ),
            )
        }
        show(vm())
        compose.onNodeWithText(text(R.string.muscu_resume)).assertIsDisplayed()
        compose.onNodeWithText("2:05").assertIsDisplayed()
        // Carte d'exercice + chip rapide du même nom.
        compose.onAllNodesWithText("Tractions").assertCountEquals(2)
        compose.onNodeWithText(text(R.string.muscu_load_program)).assertDoesNotExist()
    }

    @Test
    fun `terminer - confirmation puis navigation vers le détail`() {
        var saved: Long? = null
        val vm = vm()
        show(vm, onSaved = { saved = it })

        compose.onNodeWithContentDescription(text(R.string.muscu_exercise_name)).performScrollTo().performTextInput("Rowing")
        compose.onNodeWithContentDescription(text(R.string.muscu_add_exercise_a11y)).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Rowing").assertCountEquals(2)

        compose.onNodeWithText(text(R.string.muscu_finish)).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.muscu_finish_title)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.muscu_finish)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertNotNull(saved)
        val sets = runBlocking { repos.sessions.getMuscuSets(saved!!) }
        assertEquals(listOf("Rowing"), sets.map { it.exercise })
        assertTrue(runBlocking { repos.settings.snapshot().muscuDraft } == null)
    }
}
