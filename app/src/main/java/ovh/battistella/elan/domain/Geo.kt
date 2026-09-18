// Calculs géographiques.
package ovh.battistella.elan.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Rayon terrestre moyen en mètres. */
private const val EARTH_RADIUS_M = 6_371_000.0

/** Coordonnée nue, pour les appelants qui n'ont pas de point typé sous la main. */
data class LatLon(override val lat: Double, override val lon: Double) : GeoPoint

private fun toRad(deg: Double): Double = deg * PI / 180

/** Distance en mètres entre deux coordonnées (formule de haversine). */
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = toRad(b.lat - a.lat)
    val dLon = toRad(b.lon - a.lon)
    val lat1 = toRad(a.lat)
    val lat2 = toRad(b.lat)
    val sinLat = sin(dLat / 2)
    val sinLon = sin(dLon / 2)
    val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
}

/**
 * Décime un tracé en ne conservant qu'un point tous les `minMeters` (départ et
 * arrivée toujours préservés). Purement pour l'affichage : la base conserve la
 * résolution pleine.
 */
fun <T : GeoPoint> decimateByDistance(points: List<T>, minMeters: Double): List<T> {
    if (points.size <= 2) return points
    val out = ArrayList<T>()
    out.add(points[0])
    var last = points[0]
    for (i in 1 until points.size - 1) {
        if (haversineMeters(last, points[i]) >= minMeters) {
            out.add(points[i])
            last = points[i]
        }
    }
    out.add(points[points.size - 1])
    return out
}
