package ovh.battistella.elan.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseColors
import ovh.battistella.elan.ui.theme.PulseGradients

@RunWith(RobolectricTestRunner::class)
class PulseButtonTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun clickInvokesCallback() {
        var clicks = 0
        compose.setContent { ElanTheme { PulseButton(title = "Démarrer", onClick = { clicks++ }) } }

        compose.onNodeWithText("Démarrer").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun disabledButtonIgnoresClicks() {
        var clicks = 0
        compose.setContent {
            ElanTheme { PulseButton(title = "Démarrer", enabled = false, onClick = { clicks++ }) }
        }

        compose.onNodeWithText("Démarrer").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun loadingHidesTheLabel() {
        compose.setContent { ElanTheme { PulseButton(title = "Sauvegarder", loading = true, onClick = {}) } }

        compose.onNodeWithText("Sauvegarder").assertDoesNotExist()
    }

    @Test
    fun gradientIsInferredFromActivityColour() {
        val c = PulseColors.Light
        assertEquals(PulseGradients.velo, gradientFor(c.velo, c))
        assertEquals(PulseGradients.muscu, gradientFor(c.muscu, c))
        assertEquals(PulseGradients.course, gradientFor(c.course, c))
        assertEquals(PulseGradients.marche, gradientFor(c.marche, c))
        assertEquals(PulseGradients.heart, gradientFor(c.heart, c))
        assertEquals(PulseGradients.fire, gradientFor(c.warning, c))
        assertEquals(PulseGradients.accent, gradientFor(c.accent, c))
        assertEquals(PulseGradients.accent, gradientFor(null, c))
    }
}
