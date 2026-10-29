package ovh.battistella.elan.domain

import kotlin.math.min

private val ZONE_FLOORS = doubleArrayOf(0.0, 0.6, 0.7, 0.8, 0.9)

private const val MAX_GAP_MS = 30_000L

data class ZoneBound(
    val zone: Int,
    val minBpm: Int,
    val maxBpm: Int?,
)

data class ZoneSlice(
    val zone: Int,
    val label: String,
    val minBpm: Int,
    val maxBpm: Int?,
    val seconds: Double,
    val ratio: Double,
)

data class ZoneDistribution(
    val slices: List<ZoneSlice>,
    val totalSec: Double,
)

fun zoneBounds(maxHr: Double): List<ZoneBound> = ZONE_FLOORS.mapIndexed { i, floor ->
    val next = ZONE_FLOORS.getOrNull(i + 1)
    ZoneBound(
        zone = i + 1,
        minBpm = Math.round(floor * maxHr).toInt(),
        maxBpm = if (next == null) null else Math.round(next * maxHr).toInt() - 1,
    )
}

fun zoneDistribution(samples: List<HrReading>, maxHr: Double): ZoneDistribution? {
    if (maxHr <= 0) return null

    val seconds = DoubleArray(5)
    var totalSec = 0.0

    var previousTs = 0L
    var previousHr: Double? = null
    for (sample in samples) {
        val hr = sample.hr
        if (hr == null || hr <= 0) {
            previousHr = null
            continue
        }
        if (previousHr != null) {
            val gapMs = sample.ts - previousTs
            if (gapMs > 0) {
                val countedSec = min(gapMs, MAX_GAP_MS) / 1000.0
                seconds[heartRateZone(previousHr, maxHr) - 1] += countedSec
                totalSec += countedSec
            }
        }
        previousTs = sample.ts
        previousHr = hr
    }

    if (totalSec <= 0) return null

    val bounds = zoneBounds(maxHr)
    return ZoneDistribution(
        totalSec = totalSec,
        slices = bounds.mapIndexed { i, b ->
            ZoneSlice(
                zone = b.zone,
                label = ZONE_LABELS.getValue(b.zone),
                minBpm = b.minBpm,
                maxBpm = b.maxBpm,
                seconds = seconds[i],
                ratio = seconds[i] / totalSec,
            )
        },
    )
}

fun dominantZone(distribution: ZoneDistribution): ZoneSlice =
    distribution.slices.reduce { best, s -> if (s.seconds > best.seconds) s else best }
