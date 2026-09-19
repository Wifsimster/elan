package ovh.battistella.elan.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class BarChartTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun exposesOneSummaryForTheWholeChart() {
        val data = listOf(BarPoint("L", 12.0), BarPoint("M", 0.0), BarPoint("M", 30.0))
        compose.setContent {
            ElanTheme { BarChart(data = data, formatValue = { "${it.toInt()} min" }) }
        }

        compose.onNodeWithContentDescription("Histogramme : L 12 min, M 0 min, M 30 min").assertIsDisplayed()
    }

    @Test
    fun emptyChartSaysSo() {
        compose.setContent { ElanTheme { BarChart(data = emptyList()) } }

        compose.onNodeWithContentDescription("Histogramme vide").assertExists()
    }

    @Test
    fun summaryRoundsWithTheDefaultFormat() {
        assertEquals("L 1, M 3", barChartSummary(listOf(BarPoint("L", 1.4), BarPoint("M", 2.6))) { Math.round(it).toString() })
    }
}
