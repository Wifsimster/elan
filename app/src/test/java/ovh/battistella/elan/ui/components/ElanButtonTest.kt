package ovh.battistella.elan.ui.components

import androidx.compose.ui.graphics.Color
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
import ovh.battistella.elan.ui.theme.ElanColors
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class ElanButtonTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun clickInvokesCallback() {
        var clicks = 0
        compose.setContent { ElanTheme { ElanButton(title = "Démarrer", onClick = { clicks++ }) } }

        compose.onNodeWithText("Démarrer").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun disabledButtonIgnoresClicks() {
        var clicks = 0
        compose.setContent {
            ElanTheme { ElanButton(title = "Démarrer", enabled = false, onClick = { clicks++ }) }
        }

        compose.onNodeWithText("Démarrer").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun loadingHidesTheLabel() {
        compose.setContent { ElanTheme { ElanButton(title = "Sauvegarder", loading = true, onClick = {}) } }

        compose.onNodeWithText("Sauvegarder").assertDoesNotExist()
    }

    @Test
    fun accentAndDefaultFillWithTheBrandVolt() {
        for (c in listOf(ElanColors.Light, ElanColors.Dark)) {
            assertEquals(c.brand, fillFor(null, c))
            assertEquals(c.brand, fillFor(c.accent, c))
            assertEquals(c.brand, fillFor(c.link, c))
            assertEquals(c.velo, fillFor(c.velo, c))
            assertEquals(c.danger, fillFor(c.danger, c))
        }
    }

    @Test
    fun inkOnTheVoltIsTheBrandInk() {
        for (c in listOf(ElanColors.Light, ElanColors.Dark)) {
            assertEquals(c.onBrand, inkOn(c.brand, c))
        }
        // Teintes profondes du thème clair : blanc ; teintes vives du sombre : encre.
        assertEquals(Color.White, inkOn(ElanColors.Light.muscu, ElanColors.Light))
        assertEquals(ElanColors.Dark.onBrand, inkOn(ElanColors.Dark.velo, ElanColors.Dark))
    }
}
