package ovh.battistella.elan.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class SettingStepperTest {

    @get:Rule val compose = createComposeRule()

    private fun content(initial: Int, onValue: (Int) -> Unit) {
        compose.setContent {
            ElanTheme {
                var value by remember { mutableStateOf(initial) }
                SettingStepper(
                    label = "Poids",
                    value = value,
                    unit = "kg",
                    step = 5,
                    min = 30,
                    max = 200,
                    onChange = { value = it; onValue(it) },
                )
            }
        }
    }

    @Test
    fun stepsUpAndDownByStep() {
        var last = -1
        content(70) { last = it }

        compose.onNodeWithContentDescription("Augmenter Poids").performClick()
        assertEquals(75, last)
        compose.onNodeWithContentDescription("Poids : 75 kg").assertIsDisplayed()

        compose.onNodeWithContentDescription("Diminuer Poids").performClick()
        compose.onNodeWithContentDescription("Diminuer Poids").performClick()
        assertEquals(65, last)
    }

    @Test
    fun clampsAtTheBounds() {
        var last = -1
        content(198) { last = it }

        compose.onNodeWithContentDescription("Augmenter Poids").performClick()
        assertEquals(200, last)
        // Au plafond, le bouton « plus » est inerte.
        compose.onNodeWithContentDescription("Augmenter Poids").assertIsNotEnabled().performClick()
        assertEquals(200, last)
        compose.onNodeWithContentDescription("Poids : 200 kg").assertIsDisplayed()
    }

    @Test
    fun minusIsInertAtTheFloor() {
        var calls = 0
        content(30) { calls++ }

        compose.onNodeWithContentDescription("Diminuer Poids").assertIsNotEnabled().performClick()

        assertEquals(0, calls)
    }
}
