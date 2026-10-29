package ovh.battistella.elan.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class ProjectedPoint(val x: Double, val y: Double)

class Projection internal constructor(
    val width: Double,
    val height: Double,
    val metersPerUnit: Double,
    private val projector: (GeoPoint) -> ProjectedPoint,
) {
    fun project(p: GeoPoint): ProjectedPoint = projector(p)
}

fun createProjection(points: List<GeoPoint>, width: Double, height: Double, pad: Double = 40.0): Projection {
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

    val metersPerUnit = if (scale.isFinite() && scale > 0) 111_320 / scale else 0.0

    return Projection(width, height, metersPerUnit) { p ->
        ProjectedPoint(
            x = offsetX + (p.lon - minLon) * lonScale * scale,
            y = offsetY + (maxLat - p.lat) * scale,
        )
    }
}
