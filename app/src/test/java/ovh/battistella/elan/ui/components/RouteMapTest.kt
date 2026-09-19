package ovh.battistella.elan.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.GeoPoint
import ovh.battistella.elan.domain.LatLon
import ovh.battistella.elan.maps.OPENFREEMAP_STYLE_URL
import ovh.battistella.elan.ui.theme.ElanTheme

/** Rendu factice : n'instancie jamais MapLibre, trace les appels. */
private class FakeRenderer(style: String) : MapRenderer {
    val style = MutableStateFlow(style)
    override val styleUrl: StateFlow<String> get() = style
    val calls = mutableListOf<String>()

    @Composable
    override fun Map(
        points: List<GeoPoint>,
        styleUrl: String,
        color: Color,
        modifier: Modifier,
        height: Dp,
        live: Boolean,
        interactive: Boolean,
        fill: Boolean,
    ) {
        calls += "map:${points.size}:$styleUrl:live=$live:interactive=$interactive:fill=$fill"
        Box(modifier) { Text("FAKE_MAP") }
    }
}

@RunWith(RobolectricTestRunner::class)
class RouteMapTest {

    @get:Rule val compose = createComposeRule()

    private val route = listOf(LatLon(48.8566, 2.3522), LatLon(48.8600, 2.3600), LatLon(48.8650, 2.3550))

    @Test
    fun `style configuré et au moins 2 points - rendu MapLibre`() {
        val renderer = FakeRenderer(OPENFREEMAP_STYLE_URL)
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides renderer) {
                ElanTheme { RouteMap(points = route, live = true) }
            }
        }
        compose.onNodeWithText("FAKE_MAP").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracé GPS de la sortie en cours").assertDoesNotExist()
        assertEquals(listOf("map:3:$OPENFREEMAP_STYLE_URL:live=true:interactive=false:fill=false"), renderer.calls)
    }

    @Test
    fun `sans style - tracé Canvas hors-ligne`() {
        val renderer = FakeRenderer("")
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides renderer) {
                ElanTheme { RouteMap(points = route) }
            }
        }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
        compose.onNodeWithText("FAKE_MAP").assertDoesNotExist()
        assertEquals(emptyList<String>(), renderer.calls)
    }

    @Test
    fun `style configuré mais moins de 2 points - Canvas (qui ne dessine rien)`() {
        val renderer = FakeRenderer(OPENFREEMAP_STYLE_URL)
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides renderer) {
                ElanTheme { RouteMap(points = route.take(1)) }
            }
        }
        compose.onNodeWithText("FAKE_MAP").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertDoesNotExist()
        assertEquals(emptyList<String>(), renderer.calls)
    }

    @Test
    fun `le style change en cours de route - bascule Canvas vers MapLibre`() {
        val renderer = FakeRenderer("")
        compose.setContent {
            CompositionLocalProvider(LocalMapRenderer provides renderer) {
                ElanTheme { RouteMap(points = route) }
            }
        }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
        renderer.style.value = OPENFREEMAP_STYLE_URL
        compose.waitForIdle()
        compose.onNodeWithText("FAKE_MAP").assertIsDisplayed()
    }

    @Test
    fun `sans graphe Hilt ni substitut - rendu hors-ligne`() {
        // L'application de test n'est pas Hilt : repli `OfflineMapRenderer`, jamais MapLibre.
        compose.setContent { ElanTheme { RouteMap(points = route) } }
        compose.onNodeWithContentDescription("Tracé GPS de la sortie").assertIsDisplayed()
    }
}
