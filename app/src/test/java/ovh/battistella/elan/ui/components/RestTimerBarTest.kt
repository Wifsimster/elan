package ovh.battistella.elan.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RestTimerBarTest {

    @get:Rule val compose = createComposeRule()

    private val start = 1_800_000_000_000L

    /** Horloge pilotée par le test et état hôte (fin de repos) comme dans l'écran muscu. */
    private inner class Host(initialEndsAt: Long?) {
        var now = start
        var endsAt by mutableStateOf(initialEndsAt)
        val changes = mutableListOf<Long?>()
        val deltas = mutableListOf<Int>()

        @androidx.compose.runtime.Composable
        fun Content() {
            ElanTheme {
                RestTimerBar(
                    endsAt = endsAt,
                    onChange = {
                        changes += it
                        endsAt = it
                    },
                    onAdjustPreference = { deltas += it },
                    now = { now },
                )
            }
        }
    }

    @Test
    fun `décompte, état terminé à zéro puis fermeture automatique`() {
        compose.mainClock.autoAdvance = false
        val host = Host(start + 90_000)
        compose.setContent { host.Content() }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("REPOS").assertIsDisplayed()
        compose.onNodeWithText("1:30").assertIsDisplayed()

        // 89,2 s plus tard : il reste 0,8 s → « 0:01 ».
        host.now = start + 89_200
        compose.mainClock.advanceTimeBy(REST_TICK_MS + 50)
        compose.onNodeWithText("0:01").assertIsDisplayed()

        // À zéro : « Repos terminé », 0:00, pas encore fermé.
        host.now = start + 90_000
        compose.mainClock.advanceTimeBy(REST_TICK_MS + 50)
        compose.onNodeWithText("REPOS TERMINÉ").assertIsDisplayed()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        assertTrue(host.changes.isEmpty())

        // Après le délai de présence : fermeture (onChange(null)).
        host.now = start + 90_000 + REST_LINGER_MS
        compose.mainClock.advanceTimeBy(REST_TICK_MS + 50)
        assertEquals(listOf<Long?>(null), host.changes)
        assertNull(host.endsAt)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("REPOS TERMINÉ").assertDoesNotExist()
    }

    @Test
    fun `±15 décale la fin et remonte le delta, la croix ferme`() {
        val host = Host(start + 60_000)
        compose.setContent { host.Content() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Allonger le repos de 15 secondes").performClick()
        compose.waitForIdle()
        assertEquals(listOf<Long?>(start + 75_000), host.changes)
        assertEquals(listOf(15), host.deltas)

        compose.onNodeWithContentDescription("Réduire le repos de 15 secondes").performClick()
        compose.waitForIdle()
        assertEquals(start + 60_000, host.changes.last())
        assertEquals(listOf(15, -15), host.deltas)

        compose.onNodeWithContentDescription("Fermer le minuteur de repos").performClick()
        compose.waitForIdle()
        assertNull(host.changes.last())
        assertNull(host.endsAt)
    }

    @Test
    fun `−15 sous la seconde restante ne passe jamais sous now + 1 s`() {
        val host = Host(start + 5_000)
        compose.setContent { host.Content() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Réduire le repos de 15 secondes").performClick()
        compose.waitForIdle()
        assertEquals(start + 1_000, host.changes.single())
        assertEquals(listOf(-15), host.deltas)
    }

    @Test
    fun `sans repos programmé rien n'est rendu`() {
        val host = Host(null)
        compose.setContent { host.Content() }
        compose.waitForIdle()
        compose.onNodeWithText("REPOS").assertDoesNotExist()
    }
}
