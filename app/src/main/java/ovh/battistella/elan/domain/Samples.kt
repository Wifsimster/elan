package ovh.battistella.elan.domain

import kotlin.math.abs

data class MeanMax(val avg: Double?, val max: Double?)
data class HrSummary(val avgHr: Double?, val maxHr: Double?)
data class CadenceSummary(val avgCadence: Double?, val maxCadence: Double?)

fun meanMax(values: List<Double>): MeanMax {
    if (values.isEmpty()) return MeanMax(avg = null, max = null)
    var sum = 0.0
    var max = values[0]
    for (v in values) {
        sum += v
        if (v > max) max = v
    }
    return MeanMax(avg = Math.round(sum / values.size).toDouble(), max = max)
}

fun summarizeHr(samples: List<HrSample>): HrSummary {
    val (avg, max) = meanMax(samples.map { it.hr })
    return HrSummary(avgHr = avg, maxHr = max)
}

fun summarizeCadence(rpms: List<Double>): CadenceSummary {
    if (rpms.isEmpty()) return CadenceSummary(avgCadence = null, maxCadence = null)
    var max = rpms[0]
    var sum = 0.0
    var moving = 0
    for (v in rpms) {
        if (v > max) max = v
        if (v > 0) {
            sum += v
            moving++
        }
    }
    if (moving == 0) return CadenceSummary(avgCadence = null, maxCadence = max.takeIf { it != 0.0 })
    return CadenceSummary(avgCadence = Math.round(sum / moving).toDouble(), maxCadence = max)
}

fun <T : Timestamped, V> pushDownsampled(
    buf: MutableList<T>,
    sample: T,
    value: (T) -> V,
    minGapMs: Long = 1000,
) {
    val last = buf.lastOrNull()
    if (last != null && value(last) == value(sample) && sample.ts - last.ts < minGapMs) return
    buf.add(sample)
}

fun <T : Timestamped, R> nearestSample(
    samples: List<T>,
    ts: Long,
    pick: (T) -> R,
    toleranceMs: Long = 10_000,
): R? {
    if (samples.isEmpty()) return null
    var lo = 0
    var hi = samples.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (samples[mid].ts < ts) lo = mid + 1 else hi = mid
    }
    val candidates = ArrayList<T>(2)
    if (lo < samples.size) candidates.add(samples[lo])
    if (lo > 0) candidates.add(samples[lo - 1])
    var best = candidates[0]
    var bestDiff = abs(best.ts - ts)
    for (i in 1 until candidates.size) {
        val diff = abs(candidates[i].ts - ts)
        if (diff < bestDiff) {
            bestDiff = diff
            best = candidates[i]
        }
    }
    return if (bestDiff <= toleranceMs) pick(best) else null
}
