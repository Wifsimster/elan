package ovh.battistella.elan.domain

import kotlin.math.min

interface TimedPoint : GeoPoint, Timestamped {
    val speedKmh: Double?
}

private const val MOVING_THRESHOLD_MS = 0.7
private const val MIN_SEGMENT_M = 4.0
private const val MAX_SEGMENT_SEC = 30.0

fun movingTimeSec(points: List<TimedPoint>): Int {
    if (points.size < 2) return 0
    var sec = 0.0
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val dt = (cur.ts - prev.ts) / 1000.0
        if (dt <= 0) continue

        val dopplerMs = cur.speedKmh?.let { it / 3.6 }
        val moving = if (dopplerMs != null) {
            dopplerMs >= MOVING_THRESHOLD_MS
        } else {
            val d = haversineMeters(prev, cur)
            d >= MIN_SEGMENT_M && d / dt >= MOVING_THRESHOLD_MS
        }
        if (moving) sec += min(dt, MAX_SEGMENT_SEC)
    }
    return Math.round(sec).toInt()
}
