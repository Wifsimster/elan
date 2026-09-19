// Projection géographique d'un tracé GPS vers des coordonnées écran.
// Pure, sans état : réutilisable par le rendu statique, live et interactif.
package ovh.battistella.elan.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Coordonnées projetées dans le viewBox. */
data class ProjectedPoint(val x: Double, val y: Double)

class Projection internal constructor(
    val width: Double,
    val height: Double,
    /**
     * Mètres réels couverts par une unité du viewBox (isotrope : la projection
     * conserve le ratio géographique). Sert à dessiner une échelle de distance.
     */
    val metersPerUnit: Double,
    private val projector: (GeoPoint) -> ProjectedPoint,
) {
    /** Projette un point géographique vers les coordonnées du viewBox. */
    fun project(p: GeoPoint): ProjectedPoint = projector(p)
}

/**
 * Construit une projection cadrant l'ensemble des points dans un viewBox
 * `width × height`, en conservant le ratio géographique réel (à cette latitude,
 * 1° de longitude couvre moins de distance que 1° de latitude).
 */
fun createProjection(points: List<GeoPoint>, width: Double, height: Double, pad: Double = 40.0): Projection {
    // Liste vide : mêmes bornes dégénérées que `Math.min()` / `Math.max()` en JS.
    val minLat = points.minOfOrNull { it.lat } ?: Double.POSITIVE_INFINITY
    val maxLat = points.maxOfOrNull { it.lat } ?: Double.NEGATIVE_INFINITY
    val minLon = points.minOfOrNull { it.lon } ?: Double.POSITIVE_INFINITY
    val maxLon = points.maxOfOrNull { it.lon } ?: Double.NEGATIVE_INFINITY

    val latRange = max(1e-6, maxLat - minLat)
    val lonRange = max(1e-6, maxLon - minLon)
    val lonScale = cos(((minLat + maxLat) / 2) * PI / 180)
    val geoW = lonRange * lonScale
    val geoH = latRange
    val scale = min((width - 2 * pad) / geoW, (height - 2 * pad) / geoH)

    val offsetX = (width - geoW * scale) / 2
    val offsetY = (height - geoH * scale) / 2

    // `scale` est en unités viewBox par degré de latitude ; 1° de latitude ≈
    // 111 320 m. Garde-fou si `scale` dégénère (tracé quasi ponctuel).
    val metersPerUnit = if (scale.isFinite() && scale > 0) 111_320 / scale else 0.0

    return Projection(width, height, metersPerUnit) { p ->
        ProjectedPoint(
            x = offsetX + (p.lon - minLon) * lonScale * scale,
            y = offsetY + (maxLat - p.lat) * scale, // y inversé (nord en haut)
        )
    }
}
