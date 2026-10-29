package ovh.battistella.elan.domain

import kotlin.math.max

data class ChartPoint(val x: Double, val y: Double)

private const val MAX_POINTS = 140

private fun cumulativeKm(points: List<TrackPoint>): DoubleArray {
    val out = DoubleArray(max(1, points.size))
    for (i in 1 until points.size) {
        out[i] = out[i - 1] + haversineMeters(points[i - 1], points[i]) / 1000
    }
    return out
}

private fun resample(series: List<ChartPoint>, max: Int = MAX_POINTS): List<ChartPoint> {
    if (series.size <= max) return series
    val step = series.size.toDouble() / max
    val out = ArrayList<ChartPoint>(max + 1)
    for (i in 0 until max) out.add(series[kotlin.math.floor(i * step).toInt()])
    out.add(series[series.size - 1])
    return out
}

private fun profile(points: List<TrackPoint>, pick: (TrackPoint) -> Double?): List<ChartPoint> {
    val km = cumulativeKm(points)
    val series = ArrayList<ChartPoint>()
    for (i in points.indices) {
        val y = pick(points[i])
        if (y != null) series.add(ChartPoint(km[i], y))
    }
    return resample(series)
}

fun speedProfile(points: List<TrackPoint>): List<ChartPoint> =
    profile(points) { p -> p.speedKmh?.let { max(0.0, it) } }

fun elevationProfile(points: List<TrackPoint>): List<ChartPoint> = profile(points) { it.altitude }

fun hrProfile(points: List<TrackPoint>): List<ChartPoint> = profile(points) { it.hr }
