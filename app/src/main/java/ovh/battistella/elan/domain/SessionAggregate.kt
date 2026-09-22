// Reconstruction des agrégats d'une sortie À PARTIR de ses points GPS déjà
// enregistrés. Sert au sauvetage d'une séance orpheline laissée « en cours »
// (endedAt NULL) par un crash en pleine sortie : les points ont été flushés en
// base au fil de l'eau, mais les accumulateurs en mémoire sont perdus. On les
// recalcule ici, en best-effort — estimation à partir des points survivants,
// pas le calcul live exact.
package ovh.battistella.elan.domain

import kotlin.math.max

/** Point minimal exploitable (colonnes de `track_points`). */
interface AggPoint : TimedPoint, HrReading {
    val altitude: Double?
    val cadence: Double?
}

data class SessionAggregate(
    val durationSec: Int,
    val movingTimeSec: Int?,
    val distanceM: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val elevationGainM: Double?,
    val avgHr: Double?,
    val maxHr: Double?,
    val avgCadence: Double?,
    val maxCadence: Double?,
    /** Fin déduite : horodatage du dernier point. */
    val endedAt: Long,
)

/**
 * Seuil (m/s) en-dessous duquel un segment est considéré à l'arrêt. Volontairement
 * plus strict que les 0,7 m/s du filtre live et du temps en mouvement : ici on
 * reconstruit une distance sans le garde-fou de l'ancre de distance.
 */
private const val STANDSTILL_MS = 0.8
/** Distance minimale (m) d'un segment crédité faute de vitesse Doppler. */
private const val MIN_SEGMENT_M = 4.0
/** Vitesse implicite plafond (km/h) : au-delà, segment aberrant (saut GPS). */
private const val MAX_PLAUSIBLE_KMH = 120.0
/** Bruit d'altitude (m) ignoré avant de créditer une montée. */
private const val ELEVATION_NOISE_M = 1.0

/** Moyenne (>0) et max d'une série, en ignorant null / ≤ 0 (dropouts capteur). */
private fun avgMaxPositive(values: List<Double?>): MeanMax {
    var sum = 0.0
    var count = 0
    var max: Double? = null
    for (v in values) {
        if (v == null || v <= 0) continue
        sum += v
        count++
        if (max == null || v > max) max = v
    }
    return if (count == 0) MeanMax(avg = null, max = max) else MeanMax(avg = Math.round(sum / count).toDouble(), max = max)
}

/**
 * Recalcule les agrégats d'une sortie depuis ses points (triés par ts croissant).
 * Renvoie `null` s'il y a moins de 2 points (rien d'exploitable à sauver).
 */
fun aggregateFromPoints(points: List<AggPoint>): SessionAggregate? {
    if (points.size < 2) return null

    val startedAt = points[0].ts
    val endedAt = points[points.size - 1].ts
    val durationSec = max(0L, Math.round((endedAt - startedAt) / 1000.0)).toInt()

    // Distance : somme des segments « en mouvement » (Doppler quand dispo, sinon
    // vitesse implicite avec distance minimale) — cohérent avec le filtre live.
    var distanceM = 0.0
    var elevationGainM = 0.0
    // Le premier point sert de référence d'altitude (la boucle part du second).
    var prevAlt: Double? = points.first().altitude
    var anyAltitude = prevAlt != null
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val dt = (cur.ts - prev.ts) / 1000.0
        val d = haversineMeters(prev, cur)
        if (dt > 0) {
            val implicitKmh = (d / dt) * 3.6
            val dopplerMs = cur.speedKmh?.let { it / 3.6 }
            val moving = if (dopplerMs != null) dopplerMs >= STANDSTILL_MS else d >= MIN_SEGMENT_M && d / dt >= STANDSTILL_MS
            if (moving && implicitKmh <= MAX_PLAUSIBLE_KMH) distanceM += d
        }
        // Dénivelé positif à seuil de bruit (pas d'hystérésis complète : estimation).
        val alt = cur.altitude
        if (alt != null) {
            anyAltitude = true
            if (prevAlt != null && alt - prevAlt > ELEVATION_NOISE_M) {
                elevationGainM += alt - prevAlt
            }
            prevAlt = alt
        }
    }

    val moving = movingTimeSec(points)
    val effectiveSec = if (moving > 0) moving else durationSec
    val avgSpeedKmh = if (distanceM > 0 && effectiveSec > 0) distanceM / 1000 / (effectiveSec / 3600.0) else null

    // Vitesse max : plafonnée pour écarter un fix Doppler glitché.
    var maxSpeedKmh: Double? = null
    for (p in points) {
        val s = p.speedKmh
        if (s != null && s <= MAX_PLAUSIBLE_KMH) {
            if (maxSpeedKmh == null || s > maxSpeedKmh) maxSpeedKmh = s
        }
    }

    val (avgHr, maxHr) = avgMaxPositive(points.map { it.hr })
    val (avgCadence, maxCadence) = avgMaxPositive(points.map { it.cadence })

    return SessionAggregate(
        durationSec = durationSec,
        movingTimeSec = if (moving > 0) moving else null,
        distanceM = if (distanceM > 0) distanceM else null,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        elevationGainM = if (anyAltitude) Math.round(elevationGainM).toDouble() else null,
        avgHr = avgHr,
        maxHr = maxHr,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        endedAt = endedAt,
    )
}
