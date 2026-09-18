// Construction des séries de graphes (profils vitesse / altitude / FC) à partir
// des points GPS d'une sortie. Tout est calculé localement.
package ovh.battistella.elan.domain

import kotlin.math.max

/** Un point de graphe (abscisse → valeur). */
data class ChartPoint(val x: Double, val y: Double)

/** Nombre maximal de points d'une série (au-delà, sous-échantillonnage + dernier point). */
private const val MAX_POINTS = 140

/** Distance cumulée (en km) à chaque point du tracé. */
private fun cumulativeKm(points: List<TrackPoint>): DoubleArray {
    val out = DoubleArray(max(1, points.size))
    for (i in 1 until points.size) {
        out[i] = out[i - 1] + haversineMeters(points[i - 1], points[i]) / 1000
    }
    return out
}

/** Sous-échantillonne une série pour rester fluide sur les longues sorties. */
private fun resample(series: List<ChartPoint>, max: Int = MAX_POINTS): List<ChartPoint> {
    if (series.size <= max) return series
    val step = series.size.toDouble() / max
    val out = ArrayList<ChartPoint>(max + 1)
    for (i in 0 until max) out.add(series[kotlin.math.floor(i * step).toInt()])
    out.add(series[series.size - 1])
    return out
}

/** Construit une série {distance(km) → valeur} en ignorant les points sans donnée. */
private fun profile(points: List<TrackPoint>, pick: (TrackPoint) -> Double?): List<ChartPoint> {
    val km = cumulativeKm(points)
    val series = ArrayList<ChartPoint>()
    for (i in points.indices) {
        val y = pick(points[i])
        if (y != null) series.add(ChartPoint(km[i], y))
    }
    return resample(series)
}

/** Profil de vitesse (km/h) sur la distance. */
fun speedProfile(points: List<TrackPoint>): List<ChartPoint> =
    profile(points) { p -> p.speedKmh?.let { max(0.0, it) } }

/** Profil d'altitude (m) sur la distance. */
fun elevationProfile(points: List<TrackPoint>): List<ChartPoint> = profile(points) { it.altitude }

/** Profil de fréquence cardiaque (bpm) sur la distance. */
fun hrProfile(points: List<TrackPoint>): List<ChartPoint> = profile(points) { it.hr }
