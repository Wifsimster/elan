package ovh.battistella.elan.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class StatTileTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun rendersLabelValueUnitAndTrend() {
        compose.setContent {
            ElanTheme {
                StatTile(
                    label = "Distance",
                    value = "12,4",
                    unit = "km",
                    icon = MdiIcons.MapMarkerDistance,
                    trend = Trend("+8 % vs semaine passée", Tone.Positive),
                )
            }
        }

        compose.onNodeWithText("Distance").assertIsDisplayed()
        compose.onNodeWithText("12,4").assertIsDisplayed()
        compose.onNodeWithText("km").assertIsDisplayed()
        compose.onNodeWithText("+8 % vs semaine passée").assertIsDisplayed()
    }

    @Test
    fun withoutTrendNoTrendRowIsRendered() {
        compose.setContent { ElanTheme { StatTile(label = "Séances", value = "3", hero = true) } }

        compose.onNodeWithText("Séances").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("vs", substring = true).assertDoesNotExist()
    }

    @Test
    fun readsAsASingleNodeForScreenReaders() {
        compose.setContent {
            ElanTheme {
                StatTile(label = "Distance", value = "12,4", unit = "km", trend = Trend("+8 % vs semaine passée", Tone.Positive))
            }
        }

        compose.onNodeWithContentDescription("Distance : 12,4 km, +8 % vs semaine passée").assertIsDisplayed()
    }

    @Test
    fun descriptionOmitsUnitAndTrendWhenAbsent() {
        compose.setContent { ElanTheme { StatTile(label = "Séances", value = "3") } }

        compose.onNodeWithContentDescription("Séances : 3").assertIsDisplayed()
    }
}
