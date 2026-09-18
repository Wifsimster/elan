package ovh.battistella.elan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ovh.battistella.elan.domain.GeoPoint
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.domain.ProjectedPoint
import ovh.battistella.elan.domain.createProjection
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Radius
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 8f

/** Largeur du viewBox de référence : toutes les épaisseurs sont exprimées dans cette unité. */
private const val VIEW_W = 1000.0

/** Arrondit une distance à une valeur « ronde » (1/2/5 ×10ⁿ) pour l'échelle. */
internal fun niceMeters(m: Double): Double {
    if (!(m > 0)) return 0.0
    val pow = 10.0.pow(floor(log10(m)))
    val f = m / pow
    return (if (f >= 5) 5.0 else if (f >= 2) 2.0 else 1.0) * pow
}

/** Libellé court d'une distance ronde : « 500 m », « 1 km », « 2,5 km ». */
internal fun scaleLabel(m: Double): String {
    if (m >= 1000) {
        val km = m / 1000
        val text = if (km == floor(km)) km.toLong().toString()
        else String.format(Locale.ROOT, "%.1f", km).replace('.', ',')
        return "$text km"
    }
    return "${m.roundToLong()} m"
}

/** Projection et tracé calculés une fois par (points, taille) — pas à chaque image du halo pulsant. */
private class RouteGeometry(points: List<GeoPoint>, val width: Float, val height: Float) {
    /** Hauteur du viewBox pour garder le ratio de la zone de dessin. */
    val viewH: Double = VIEW_W * height / width
    /** Pixels par unité de viewBox. */
    val k: Float = width / VIEW_W.toFloat()
    val projection = createProjection(points, VIEW_W, viewH)
    val coords: List<ProjectedPoint> = points.map { projection.project(it) }
    val path: Path = Path().apply {
        coords.forEachIndexed { i, c ->
            val x = (c.x * k).toFloat()
            val y = (c.y * k).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    fun px(p: ProjectedPoint): Offset = Offset((p.x * k).toFloat(), (p.y * k).toFloat())
}

/**
 * Tracé du parcours, normalisé dans la vue, sur `Canvas`. Aucun fond
 * cartographique : aucune donnée de localisation n'est envoyée à un serveur
 * (promesse 100 % locale). Les épaisseurs suivent le viewBox de 1000 unités de
 * l'app d'origine (halo 22, trait 10, marqueurs r 13), mises à l'échelle de la
 * largeur réelle.
 *
 * Trois modes :
 * - statique (défaut) — vignette figée avec grille, échelle de distance et nord ;
 * - [live] — marqueur de position courante pulsant, décor épuré (sécurité à vélo) ;
 * - [interactive] — pinch-zoom [1, 8], pan et double-tap pour réinitialiser.
 *
 * Ne dessine rien sous 2 points.
 */
@Composable
fun RouteCanvas(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = 200.dp,
    live: Boolean = false,
    interactive: Boolean = false,
    /** Plein écran : remplit son parent, sans bordure ni coins arrondis. */
    fill: Boolean = false,
) {
    if (points.size < 2) return

    val colors = ElanTheme.colors
    val stroke = color ?: colors.velo
    val measurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(points, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            RouteGeometry(points, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else {
            null
        }
    }

    // Halo pulsant du marqueur de position courante (mode live) : 1 → 1,9 en 1 100 ms, aller-retour.
    val pulse = if (live) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.9f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        1f
    }

    // Transform de vue (pinch/pan), découplé de la projection géographique.
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch {
            scale.snapTo((scale.value * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE))
            offset.snapTo(offset.value + panChange)
        }
    }

    val frame = if (fill) {
        Modifier.fillMaxSize().background(colors.background)
    } else {
        val shape = RoundedCornerShape(Radius.md)
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
    }
    val gestures = if (interactive) {
        Modifier
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scope.launch {
                        launch { scale.animateTo(1f) }
                        launch { offset.animateTo(Offset.Zero) }
                    }
                })
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .semantics { contentDescription = if (live) "Tracé GPS de la sortie en cours" else "Tracé GPS de la sortie" }
            .then(frame)
            .then(gestures)
            .clipToBounds(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .graphicsLayer {
                    translationX = offset.value.x
                    translationY = offset.value.y
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            val g = geometry ?: return@Canvas
            drawRoute(
                g = g,
                stroke = stroke,
                live = live,
                pulse = pulse,
                gridColor = colors.border,
                startColor = colors.success,
                endColor = colors.heart,
                background = colors.background,
                decorColor = colors.textSecondary,
                measurer = measurer,
            )
        }
    }
}

private fun DrawScope.drawRoute(
    g: RouteGeometry,
    stroke: Color,
    live: Boolean,
    pulse: Float,
    gridColor: Color,
    startColor: Color,
    endColor: Color,
    background: Color,
    decorColor: Color,
    measurer: TextMeasurer,
) {
    val k = g.k
    val w = g.width
    val h = g.height

    // Grille discrète : repère visuel façon carte, sous le tracé (hors live).
    if (!live) {
        for (i in 1..5) {
            val x = w / 6 * i
            val y = h / 6 * i
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 2 * k, alpha = 0.5f)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 2 * k, alpha = 0.5f)
        }
    }

    // Halo sous le tracé : profondeur + lisibilité au-dessus de la grille.
    drawPath(g.path, stroke, alpha = 0.16f, style = Stroke(22 * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(g.path, stroke, style = Stroke(10 * k, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Départ : disque plein vert. Distinction non chromatique avec l'arrivée (a11y).
    val start = g.px(g.coords.first())
    val end = g.px(g.coords.last())
    drawCircle(startColor, radius = 13 * k, center = start)
    if (live) {
        drawCircle(stroke, radius = 13 * k * pulse, center = end, alpha = 1f / pulse)
        drawCircle(stroke, radius = 13 * k, center = end)
        drawCircle(background, radius = 13 * k, center = end, style = Stroke(4 * k))
    } else {
        // Arrivée : anneau (forme distincte du départ).
        drawCircle(endColor, radius = 13 * k, center = end, style = Stroke(6 * k))
    }

    if (live) return

    val labelStyle = TextStyle(
        fontSize = (30 * k).toSp(),
        fontWeight = FontWeight.Bold,
        color = decorColor,
    )

    // Échelle de distance (bas-gauche) : repère métrique pour le tracé.
    val mpp = g.projection.metersPerUnit
    val niceM = if (mpp > 0) niceMeters(VIEW_W * 0.28 * mpp) else 0.0
    if (niceM > 0) {
        val barPx = (niceM / mpp * k).toFloat()
        val x0 = 36 * k
        val yBar = h - 34 * k
        val label = measurer.measure(scaleLabel(niceM), labelStyle)
        drawText(label, topLeft = Offset(x0, h - 50 * k - label.size.height))
        drawLine(decorColor, Offset(x0, yBar), Offset(x0 + barPx, yBar), strokeWidth = 5 * k, cap = StrokeCap.Round)
        drawLine(decorColor, Offset(x0, h - 42 * k), Offset(x0, h - 26 * k), strokeWidth = 5 * k)
        drawLine(decorColor, Offset(x0 + barPx, h - 42 * k), Offset(x0 + barPx, h - 26 * k), strokeWidth = 5 * k)
    }

    // Nord (haut-droit) : la projection garde toujours le nord en haut.
    val nx = w - 46 * k
    drawLine(decorColor, Offset(nx, 68 * k), Offset(nx, 32 * k), strokeWidth = 5 * k, cap = StrokeCap.Round)
    val arrow = Path().apply {
        moveTo(nx - 9 * k, 46 * k)
        lineTo(nx, 30 * k)
        lineTo(nx + 9 * k, 46 * k)
    }
    drawPath(arrow, decorColor, style = Stroke(5 * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val north = measurer.measure("N", labelStyle.copy(textAlign = TextAlign.Center))
    drawText(north, topLeft = Offset(nx - north.size.width / 2f, 92 * k - north.size.height))
}

/** Cadre d'attente affiché tant que le tracé live n'a pas assez de points. */
@Composable
fun MapPlaceholder(status: GpsStatus, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    val label = when (status) {
        GpsStatus.TRACKING -> "En attente de déplacement — le tracé apparaîtra dès les premiers mètres."
        GpsStatus.REQUESTING -> "Recherche du signal GPS…"
        GpsStatus.DENIED -> "Localisation refusée — active le GPS pour tracer la sortie."
        GpsStatus.IDLE -> "Tracé GPS indisponible."
    }
    val shape = RoundedCornerShape(Radius.md)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 28.dp),
    ) {
        Icon(
            painter = painterResource(MdiIcons.MapMarkerPath),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = label,
            color = colors.textSecondary,
            style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
            textAlign = TextAlign.Center,
        )
    }
}
