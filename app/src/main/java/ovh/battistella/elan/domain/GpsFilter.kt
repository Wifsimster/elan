package ovh.battistella.elan.domain

import kotlin.math.max

data class GpsFix(
    val ts: Long,
    override val lat: Double,
    override val lon: Double,
    val altitude: Double?,
    val accuracy: Double?,
    val altitudeAccuracy: Double?,
    val speed: Double?,
) : GeoPoint

data class ConsolidatedPoint(
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    val altitude: Double?,
    override val speedKmh: Double?,
) : TimedPoint

data class ConsolidationResult(
    val point: ConsolidatedPoint?,
    val deltaDistanceM: Double,
    val deltaElevationGainM: Double,
)

class GpsConsolidator {
    companion object {
        const val MAX_ACCURACY_M = 50.0
        const val MAX_PLAUSIBLE_SPEED_MS = 30.0
        const val STANDSTILL_SPEED_MS = 0.7
        const val DISTANCE_ANCHOR_M = 5.0
        const val KALMAN_MIN_PROCESS_NOISE_MS = 3.0
        const val ALTITUDE_WINDOW = 7
        const val ELEVATION_HYSTERESIS_M = 5.0
        const val MAX_ALTITUDE_ACCURACY_M = 12.0
        const val DEFAULT_ACCURACY_M = 15.0
        const val ALT_RESET_GAP_S = 30.0

        private val REJECTED = ConsolidationResult(point = null, deltaDistanceM = 0.0, deltaElevationGainM = 0.0)

        private fun wrapLonDelta(deltaDeg: Double): Double {
            var d = deltaDeg
            while (d > 180) d -= 360
            while (d < -180) d += 360
            return d
        }

        private fun normalizeLon(lon: Double): Double = ((((lon + 180) % 360) + 360) % 360) - 180

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size shr 1
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }

    private var estLat = 0.0
    private var estLon = 0.0
    private var variance = -1.0

    private var lastTs = 0L
    private var lastAccepted: ConsolidatedPoint? = null
    private var distanceAnchor: ConsolidatedPoint? = null

    private val altWindow = ArrayDeque<Double>()
    private var elevationAnchor: Double? = null

    fun process(fix: GpsFix): ConsolidationResult {
        val accuracy = max(fix.accuracy ?: DEFAULT_ACCURACY_M, 1.0)
        if (accuracy > MAX_ACCURACY_M) return REJECTED

        val prev = lastAccepted
        if (prev != null) {
            val dt = (fix.ts - prev.ts) / 1000.0
            if (dt <= 0) return REJECTED
            if (haversineMeters(prev, fix) / dt > MAX_PLAUSIBLE_SPEED_MS) return REJECTED
        }

        if (variance < 0) {
            estLat = fix.lat
            estLon = fix.lon
            variance = accuracy * accuracy
        } else {
            val dt = max((fix.ts - lastTs) / 1000.0, 0.001)
            val q = max(KALMAN_MIN_PROCESS_NOISE_MS, fix.speed ?: 0.0)
            variance += dt * q * q
            val gain = variance / (variance + accuracy * accuracy)
            estLat += gain * (fix.lat - estLat)
            estLon = normalizeLon(estLon + gain * wrapLonDelta(fix.lon - estLon))
            variance *= 1 - gain
        }

        if (lastTs > 0 && (fix.ts - lastTs) / 1000.0 > ALT_RESET_GAP_S) {
            altWindow.clear()
        }
        lastTs = fix.ts

        var smoothedAlt: Double? = null
        val altitude = fix.altitude
        val altitudeAccuracy = fix.altitudeAccuracy
        if (altitude != null && (altitudeAccuracy == null || altitudeAccuracy <= MAX_ALTITUDE_ACCURACY_M)) {
            altWindow.addLast(altitude)
            if (altWindow.size > ALTITUDE_WINDOW) altWindow.removeFirst()
            smoothedAlt = median(altWindow)
        }

        val speed = fix.speed
        val point = ConsolidatedPoint(
            ts = fix.ts,
            lat = estLat,
            lon = estLon,
            altitude = smoothedAlt,
            speedKmh = if (speed != null && speed >= 0) speed * 3.6 else null,
        )

        var deltaDistanceM = 0.0
        val anchor = distanceAnchor
        if (anchor == null) {
            distanceAnchor = point
        } else {
            val moving = speed == null || speed >= STANDSTILL_SPEED_MS
            val d = haversineMeters(anchor, point)
            if (moving && d >= DISTANCE_ANCHOR_M) {
                deltaDistanceM = d
                distanceAnchor = point
            }
        }

        var deltaElevationGainM = 0.0
        if (smoothedAlt != null) {
            val elevAnchor = elevationAnchor
            if (elevAnchor == null) {
                elevationAnchor = smoothedAlt
            } else if (smoothedAlt > elevAnchor + ELEVATION_HYSTERESIS_M) {
                deltaElevationGainM = smoothedAlt - elevAnchor
                elevationAnchor = smoothedAlt
            } else if (smoothedAlt < elevAnchor - ELEVATION_HYSTERESIS_M) {
                elevationAnchor = smoothedAlt
            }
        }

        lastAccepted = point
        return ConsolidationResult(point, deltaDistanceM, deltaElevationGainM)
    }
}
