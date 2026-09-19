// Fond de carte MapLibre avec le tracé en surimpression (port de
// components/maplibre-route.tsx). Le style provient du serveur configuré par
// l'utilisateur (OpenFreeMap ou son propre serveur de tuiles). N'est rendu que
// si un fond de carte est activé ; sinon `RouteMap` retombe sur `RouteCanvas`.
// L'attribution OSM est affichée en surimpression, comme l'exige l'usage des
// données OpenStreetMap.
package ovh.battistella.elan.maps

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ovh.battistella.elan.domain.GeoPoint
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Radius

private const val SOURCE_ROUTE = "route"
private const val SOURCE_START = "route-start"
private const val SOURCE_END = "route-end"
private const val LAYER_LINE = "route-line"
private const val LAYER_START = "route-start-dot"
private const val LAYER_END = "route-end-dot"

/** Initialise MapLibre une fois par processus (idempotent, mais évite l'appel à chaque carte). */
@Volatile
private var initialized = false

internal fun ensureMapLibre(context: Context) {
    if (initialized) return
    synchronized(MapLibre::class.java) {
        if (!initialized) {
            MapLibre.getInstance(context.applicationContext)
            initialized = true
        }
    }
}

/** Ce que la carte doit refléter à un instant donné. */
internal data class RouteMapContent(
    val points: List<GeoPoint>,
    val lineColor: Int,
    val startColor: Int,
    val endStrokeColor: Int,
    val live: Boolean,
    val boundsPaddingPx: Int,
)

/**
 * `MapView` qui laisse passer les touches à Compose quand la carte n'est pas
 * explorable (`interactive = false`) : la page défile, la carte reste passive
 * (sécurité à vélo en mode live).
 */
internal class RouteMapView(context: Context, options: MapLibreMapOptions) : MapView(context, options) {
    var touchable: Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean =
        if (touchable) super.dispatchTouchEvent(ev) else false
}

/**
 * Pilote une `MapView` : style, sources GeoJSON, couches et caméra. Le
 * contenu arrive avant ou après le chargement du style ; on rejoue le dernier
 * connu à chaque étape pour ne rien perdre.
 */
internal class RouteMapController(private val view: MapView, private val styleUrl: String) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var pending: RouteMapContent? = null
    private var lastPoints: List<GeoPoint>? = null

    init {
        view.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
            }
            m.setStyle(Style.Builder().fromUri(styleUrl)) { s ->
                style = s
                pending?.let { apply(it) }
            }
        }
    }

    fun setInteractive(interactive: Boolean) {
        map?.uiSettings?.apply {
            setAllGesturesEnabled(interactive)
            isCompassEnabled = interactive
        }
    }

    fun update(content: RouteMapContent) {
        pending = content
        if (style != null) apply(content)
    }

    private fun apply(content: RouteMapContent) {
        val m = map ?: return
        val s = style ?: return
        val coords = content.points.map { Point.fromLngLat(it.lon, it.lat) }
        if (coords.size < 2) return

        val line = Feature.fromGeometry(LineString.fromLngLats(coords))
        val start = Feature.fromGeometry(coords.first())
        val end = Feature.fromGeometry(coords.last())

        val routeSource = s.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
        if (routeSource == null) {
            s.addSource(GeoJsonSource(SOURCE_ROUTE, line))
            s.addSource(GeoJsonSource(SOURCE_START, start))
            s.addSource(GeoJsonSource(SOURCE_END, end))
            s.addLayer(
                LineLayer(LAYER_LINE, SOURCE_ROUTE).withProperties(
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineColor(content.lineColor),
                    PropertyFactory.lineWidth(4f),
                ),
            )
            s.addLayer(
                CircleLayer(LAYER_START, SOURCE_START).withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor(content.startColor),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                ),
            )
            s.addLayer(
                CircleLayer(LAYER_END, SOURCE_END).withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(content.lineColor),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor(content.endStrokeColor),
                ),
            )
        } else {
            routeSource.setGeoJson(line)
            s.getSourceAs<GeoJsonSource>(SOURCE_START)?.setGeoJson(start)
            s.getSourceAs<GeoJsonSource>(SOURCE_END)?.setGeoJson(end)
            s.getLayerAs<LineLayer>(LAYER_LINE)?.setProperties(PropertyFactory.lineColor(content.lineColor))
            s.getLayerAs<CircleLayer>(LAYER_END)?.setProperties(
                PropertyFactory.circleColor(content.lineColor),
                PropertyFactory.circleStrokeColor(content.endStrokeColor),
            )
        }

        // Caméra : en live on suit la position courante (zoom 15, 600 ms) ;
        // sinon on cadre l'emprise du tracé une fois, sans animation.
        if (content.points != lastPoints) {
            lastPoints = content.points
            val last = content.points.last()
            if (content.live) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), 15.0), 600)
            } else {
                val bounds = LatLngBounds.Builder().apply { content.points.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                if (bounds.latitudeSpan == 0.0 && bounds.longitudeSpan == 0.0) {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), 14.0))
                } else {
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, content.boundsPaddingPx))
                }
            }
        }
    }
}

/**
 * Carte MapLibre du parcours. [styleUrl] doit être une URL HTTPS valide et
 * [points] compter au moins 2 points — c'est `RouteMap` qui décide.
 */
@Composable
fun MapLibreRouteView(
    points: List<GeoPoint>,
    styleUrl: String,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    live: Boolean = false,
    interactive: Boolean = false,
    fill: Boolean = false,
) {
    val colors = ElanTheme.colors
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val content = RouteMapContent(
        points = points,
        lineColor = color.toArgb(),
        startColor = colors.success.toArgb(),
        endStrokeColor = colors.background.toArgb(),
        live = live,
        boundsPaddingPx = with(density) { 56.dp.roundToPx() },
    )

    val frame = if (fill) {
        Modifier.fillMaxSize()
    } else {
        val shape = RoundedCornerShape(Radius.md)
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
    }

    Box(
        modifier = modifier
            .semantics { contentDescription = if (live) "Tracé GPS de la sortie en cours" else "Tracé GPS de la sortie" }
            .then(frame),
    ) {
        // Un nouveau style = une nouvelle vue (le style se fixe à la création).
        key(styleUrl) {
        val holder = remember { MapViewHolder() }
        DisposableEffect(lifecycle, holder) {
            val observer = LifecycleEventObserver { _, event ->
                val view = holder.view ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_START -> view.onStart()
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    Lifecycle.Event.ON_STOP -> view.onStop()
                    Lifecycle.Event.ON_DESTROY -> holder.destroy()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                holder.destroy()
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ensureMapLibre(ctx)
                val options = MapLibreMapOptions.createFromAttributes(ctx)
                    .logoEnabled(false)
                    .attributionEnabled(false)
                    .compassEnabled(interactive)
                RouteMapView(ctx, options).also { view ->
                    view.onCreate(null)
                    holder.view = view
                    // L'observateur (ajouté après la création du nœud) reçoit
                    // ON_CREATE/ON_START/ON_RESUME de rattrapage : rien à rejouer ici.
                    holder.controller = RouteMapController(view, styleUrl)
                }
            },
            update = { view ->
                view.touchable = interactive
                holder.controller?.setInteractive(interactive)
                holder.controller?.update(content)
            },
        )
        }
        // Attribution OSM — obligatoire dès qu'un fond de carte est affiché.
        Text(
            text = mapAttribution(styleUrl),
            style = TextStyle(fontSize = 9.sp, lineHeight = 12.sp),
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp),
        )
    }
}

/** Référence mutable vers la vue et son pilote, partagée entre la fabrique et le cycle de vie. */
private class MapViewHolder {
    var view: RouteMapView? = null
    var controller: RouteMapController? = null
    private var destroyed = false

    fun destroy() {
        if (destroyed) return
        destroyed = true
        view?.onDestroy()
        view = null
        controller = null
    }
}
