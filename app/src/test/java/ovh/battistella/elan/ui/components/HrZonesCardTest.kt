package ovh.battistella.elan.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.ZoneDistribution
import ovh.battistella.elan.domain.ZoneSlice
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class HrZonesCardTest {

    @get:Rule val compose = createComposeRule()

    // FC max 190 : zones 0–113, 114–132, 133–151, 152–170, 171+.
    private val distribution = ZoneDistribution(
        totalSec = 1000.0,
        slices = listOf(
            ZoneSlice(1, "Récupération", 0, 113, 100.0, 0.10),
            ZoneSlice(2, "Endurance", 114, 132, 250.0, 0.25),
            ZoneSlice(3, "Aérobie", 133, 151, 500.0, 0.50),
            ZoneSlice(4, "Seuil", 152, 170, 150.0, 0.15),
            ZoneSlice(5, "Maximal", 171, null, 0.0, 0.0),
        ),
    )

    @Test
    fun captionNamesTheDominantZone() {
        assertEquals(
            "Surtout en zone 3 (aérobie) — 50 % du temps avec la ceinture.",
            dominantCaption(distribution),
        )
    }

    @Test
    fun rangeLabelsFollowTheShortFrenchForm() {
        assertEquals("< 114", rangeLabel(distribution.slices[0]))
        assertEquals("133–151", rangeLabel(distribution.slices[2]))
        assertEquals("171+", rangeLabel(distribution.slices[4]))
    }

    @Test
    fun rendersRowsBarDescriptionAndCaption() {
        compose.setContent { ElanTheme { HrZonesCard(distribution) } }

        compose.onNodeWithText("Zones cardiaques").assertIsDisplayed()
        compose.onNodeWithText("Aérobie").assertIsDisplayed()
        compose.onNodeWithText("8:20").assertIsDisplayed()
        compose.onNodeWithText("Surtout en zone 3 (aérobie) — 50 % du temps avec la ceinture.").assertIsDisplayed()
        // La barre ne décrit que les zones parcourues (la zone 5 vide est omise).
        compose.onNodeWithContentDescription("Récupération 10 %, Endurance 25 %, Aérobie 50 %, Seuil 15 %")
            .assertIsDisplayed()
    }
}
