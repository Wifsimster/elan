package ovh.battistella.elan.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.assertCountEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class LineChartTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun rendersNothingUnderTwoPoints() {
        compose.setContent {
            ElanTheme { LineChart(data = listOf(ChartPoint(0.0, 12.0)), color = Color.Red, label = "Vitesse") }
        }

        compose.onRoot().onChildren().assertCountEquals(0)
    }

    @Test
    fun exposesAMinMaxAverageSummary() {
        val data = listOf(ChartPoint(0.0, 10.0), ChartPoint(1.0, 30.0), ChartPoint(2.0, 20.0))
        compose.setContent {
            ElanTheme { LineChart(data = data, color = Color.Red, avg = 20.0, label = "Vitesse") }
        }

        compose.onNodeWithContentDescription("Vitesse : minimum 10, maximum 30, moyenne 20").assertIsDisplayed()
    }

    @Test
    fun summaryOmitsAverageAndLabelWhenAbsent() {
        val data = listOf(ChartPoint(0.0, 1.4), ChartPoint(1.0, 2.6))
        assertEquals("minimum 1, maximum 3", lineChartSummary(data, null, null) { Math.round(it).toString() })
    }
}
