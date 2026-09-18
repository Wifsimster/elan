package ovh.battistella.elan.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class GpsStatusPillTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun labelsFollowStatusAndAccuracy() {
        assertEquals("GPS précis", gpsStatusLabel(GpsStatus.TRACKING, 10.0))
        assertEquals("GPS ±24 m", gpsStatusLabel(GpsStatus.TRACKING, 23.6))
        assertEquals("Recherche GPS…", gpsStatusLabel(GpsStatus.TRACKING, null))
        assertEquals("Recherche GPS…", gpsStatusLabel(GpsStatus.REQUESTING, 5.0))
        assertEquals("GPS refusé", gpsStatusLabel(GpsStatus.DENIED, 5.0))
    }

    @Test
    fun rendersTheLabel() {
        compose.setContent { ElanTheme { GpsStatusPill(GpsStatus.TRACKING, accuracyM = 4.0) } }
        compose.onNodeWithText("GPS précis").assertIsDisplayed()
    }
}
