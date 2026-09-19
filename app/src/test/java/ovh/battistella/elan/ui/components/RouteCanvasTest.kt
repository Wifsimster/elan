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
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.domain.LatLon
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class RouteCanvasTest {

    @get:Rule val compose = createComposeRule()

    private val route = listOf(
        LatLon(48.8566, 2.3522),
        LatLon(48.8600, 2.3600),
        LatLon(48.8650, 2.3550),
        LatLon(48.8700, 2.3700),
    )

    @Test
    fun niceMetersRoundsTo125Series() {
        assertEquals(0.0, niceMeters(0.0), 0.0)
        assertEquals(0.0, niceMeters(-5.0), 0.0)
        assertEquals(100.0, niceMeters(137.0), 0.0)
        assertEquals(200.0, niceMeters(250.0), 0.0)
        assertEquals(500.0, niceMeters(640.0), 0.0)
        assertEquals(1000.0, niceMeters(1000.0), 0.0)
        assertEquals(2000.0, niceMeters(4999.0), 0.0)
        assertEquals(5000.0, niceMeters(7200.0), 0.0)
    }

    @Test
    fun scaleLabelsUseFrenchUnits() {
        assertEquals("500 m", scaleLabel(500.0))
        assertEquals("1 km", scaleLabel(1000.0))
        assertEquals("2,5 km", scaleLabel(2500.0))
        assertEquals("20 km", scaleLabel(20_000.0))
    }

    @Test
    fun composesWithNoPoints() {
        compose.setContent { ElanTheme { RouteCanvas(points = emptyList()) } }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertDoesNotExist()
    }

    @Test
    fun composesWithASinglePoint() {
        compose.setContent { ElanTheme { RouteCanvas(points = route.take(1)) } }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertDoesNotExist()
    }

    @Test
    fun drawsAStaticRoute() {
        compose.setContent { ElanTheme { RouteCanvas(points = route) } }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
    }

    @Test
    fun drawsALiveRoute() {
        compose.setContent { ElanTheme { RouteCanvas(points = route.take(2), live = true) } }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie en cours").assertIsDisplayed()
    }

    @Test
    fun drawsAnInteractiveRoute() {
        compose.setContent { ElanTheme { RouteCanvas(points = route, interactive = true) } }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
    }

    @Test
    fun placeholderTextsFollowTheGpsStatus() {
        compose.setContent { ElanTheme { MapPlaceholder(GpsStatus.DENIED) } }
        compose.onNodeWithText("Localisation refusée — active le GPS pour tracer la sortie.").assertIsDisplayed()
    }
}
