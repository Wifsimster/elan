package ovh.battistella.elan.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.bestInk
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanColors
import ovh.battistella.elan.ui.theme.ElanGradients
import ovh.battistella.elan.ui.theme.toHex6

@RunWith(RobolectricTestRunner::class)
class ElanChipTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun selectedInkIsTheMostReadableOnTheTint() {
        // Teintes claires (lime de la marche, teal du vélo) → encre sombre ;
        // teintes profondes (accent, muscu) → blanc.
        val light = ElanColors.Light
        val dark = ElanColors.Dark
        assertEquals(ElanGradients.OnBright, chipInk(dark.marche))
        assertEquals(ElanGradients.OnBright, chipInk(dark.velo))
        assertEquals(Color.White, chipInk(light.accent))
        assertEquals(Color.White, chipInk(light.muscu))
    }

    @Test
    fun chipInkMatchesTheDomainContrastHelper() {
        for (tint in listOf(ElanColors.Light.marche, ElanColors.Dark.accent, ElanColors.Light.course)) {
            val expected = bestInk(tint.toHex6(), listOf(ElanGradients.OnBright.toHex6(), "#FFFFFF"))
            assertTrue(chipInk(tint).toHex6().equals(expected, ignoreCase = true))
        }
    }

    @Test
    fun clickTogglesSelectionThroughTheCallback() {
        var selected = false
        compose.setContent {
            ElanTheme {
                var state by remember { mutableStateOf(false) }
                ElanChip(label = "Vélo", selected = state, onClick = { state = !state; selected = state })
            }
        }

        compose.onNodeWithText("Vélo").assertIsNotSelected().performClick()
        compose.onNodeWithText("Vélo").assertIsSelected()
        assertTrue(selected)
    }
}
