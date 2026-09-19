// Orchestrateur de la carte du parcours (port de components/route-map.tsx) :
// si un style MapLibre est configuré, affiche un vrai fond de carte avec le
// tracé par-dessus ; sinon retombe sur le tracé Canvas hors-ligne.
package ovh.battistella.elan.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ovh.battistella.elan.domain.GeoPoint
import ovh.battistella.elan.maps.MapLibreRouteView
import ovh.battistella.elan.maps.MapStyleRepository
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Rendu du fond de carte, injecté par composition locale pour que les tests
 * fournissent un substitut qui n'instancie jamais MapLibre (pas de bibliothèque
 * native sur la JVM) et que les écrans ignorent d'où vient le style.
 */
interface MapRenderer {
    /** URL de style connue en mémoire ; `""` = aucun fond (rendu Canvas). */
    val styleUrl: StateFlow<String>

    @Composable
    fun Map(
        points: List<GeoPoint>,
        styleUrl: String,
        color: Color,
        modifier: Modifier,
        height: Dp,
        live: Boolean,
        interactive: Boolean,
        fill: Boolean,
    )
}

/** Rendu de production : style du dépôt, carte MapLibre. */
class MapLibreRenderer(repository: MapStyleRepository) : MapRenderer {
    override val styleUrl: StateFlow<String> = repository.styleUrl

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
        MapLibreRouteView(
            points = points,
            styleUrl = styleUrl,
            color = color,
            modifier = modifier,
            height = height,
            live = live,
            interactive = interactive,
            fill = fill,
        )
    }
}

/** Sans graphe Hilt (aperçus, tests sans substitut) : jamais de fond de carte. */
object OfflineMapRenderer : MapRenderer {
    override val styleUrl: StateFlow<String> = MutableStateFlow("")

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
    ) = Unit
}

/** `null` = résoudre depuis le graphe Hilt de l'application (production). */
val LocalMapRenderer = staticCompositionLocalOf<MapRenderer?> { null }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapRendererEntryPoint {
    fun mapStyleRepository(): MapStyleRepository
}

@Composable
private fun resolveMapRenderer(): MapRenderer {
    LocalMapRenderer.current?.let { return it }
    val app: Context = LocalContext.current.applicationContext
    return remember(app) {
        try {
            MapLibreRenderer(EntryPointAccessors.fromApplication(app, MapRendererEntryPoint::class.java).mapStyleRepository())
        } catch (e: IllegalStateException) {
            OfflineMapRenderer
        }
    }
}

/**
 * Carte du parcours : fond MapLibre si un style est configuré et qu'il y a au
 * moins 2 points, sinon [RouteCanvas] (qui ne dessine rien sous 2 points). Le
 * style est lu de façon synchrone depuis le cache mémoire : pas de flash
 * Canvas→MapLibre quand le fond est déjà connu.
 */
@Composable
fun RouteMap(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 200.dp,
    live: Boolean = false,
    interactive: Boolean = false,
    fill: Boolean = false,
) {
    val renderer = resolveMapRenderer()
    val styleUrl by renderer.styleUrl.collectAsStateWithLifecycle()
    val stroke = color ?: ElanTheme.colors.velo
    if (styleUrl.isNotEmpty() && points.size >= 2) {
        renderer.Map(
            points = points,
            styleUrl = styleUrl,
            color = stroke,
            modifier = modifier,
            height = height,
            live = live,
            interactive = interactive,
            fill = fill,
        )
    } else {
        RouteCanvas(
            points = points,
            modifier = modifier,
            color = stroke,
            height = height,
            live = live,
            interactive = interactive,
            fill = fill,
        )
    }
}
