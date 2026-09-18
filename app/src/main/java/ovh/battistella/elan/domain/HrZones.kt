// Répartition du temps passé dans chaque zone cardiaque sur une séance.
// Les seuils sont ceux de `heartRateZone` (Calories.kt) : l'effort de séance et
// les zones racontent la même histoire.
package ovh.battistella.elan.domain

import kotlin.math.min

/** Bornes basses des zones, en fraction de FC max (cf. `heartRateZone`). */
private val ZONE_FLOORS = doubleArrayOf(0.0, 0.6, 0.7, 0.8, 0.9)

/**
 * Écart maximal entre deux mesures encore compté comme du temps d'effort. Au-delà,
 * on considère qu'il y a eu un trou (ceinture hors de portée, pause) et on ne
 * l'attribue à aucune zone — sinon une coupure de dix minutes gonflerait la zone
 * où la ceinture s'est tue.
 */
private const val MAX_GAP_MS = 30_000L

data class ZoneBound(
    val zone: Int,
    /** Borne basse de la zone en bpm (0 pour la zone 1). */
    val minBpm: Int,
    /** Borne haute en bpm, `null` pour la zone 5 (pas de plafond). */
    val maxBpm: Int?,
)

/** Une zone et le temps qu'on y a passé. */
data class ZoneSlice(
    val zone: Int,
    val label: String,
    val minBpm: Int,
    val maxBpm: Int?,
    val seconds: Double,
    /** Part du temps cardio total, entre 0 et 1. */
    val ratio: Double,
)

data class ZoneDistribution(
    val slices: List<ZoneSlice>,
    /** Temps total effectivement attribué à une zone (hors trous de mesure). */
    val totalSec: Double,
)

/** Bornes en bpm des cinq zones pour une FC max donnée. */
fun zoneBounds(maxHr: Double): List<ZoneBound> = ZONE_FLOORS.mapIndexed { i, floor ->
    val next = ZONE_FLOORS.getOrNull(i + 1)
    ZoneBound(
        zone = i + 1,
        minBpm = Math.round(floor * maxHr).toInt(),
        // La borne haute affichée est exclusive côté calcul : on montre le dernier
        // bpm encore dans la zone pour ne pas afficher deux zones qui se chevauchent.
        maxBpm = if (next == null) null else Math.round(next * maxHr).toInt() - 1,
    )
}

/**
 * Temps passé dans chaque zone à partir d'échantillons cardiaques horodatés.
 *
 * Chaque intervalle entre deux mesures consécutives est attribué à la zone de la
 * mesure qui l'ouvre, et plafonné à `MAX_GAP_MS`. Renvoie `null` quand rien n'est
 * exploitable (pas de ceinture, FC max inconnue, une seule mesure).
 */
fun zoneDistribution(samples: List<HrReading>, maxHr: Double): ZoneDistribution? {
    if (maxHr <= 0) return null

    val seconds = DoubleArray(5)
    var totalSec = 0.0

    var previousTs = 0L
    var previousHr: Double? = null
    for (sample in samples) {
        val hr = sample.hr
        if (hr == null || hr <= 0) {
            // Point sans FC (capteur absent sur ce tronçon) : il coupe l'intervalle au
            // lieu de le prolonger avec la dernière valeur connue.
            previousHr = null
            continue
        }
        if (previousHr != null) {
            val gapMs = sample.ts - previousTs
            // Un horodatage égal ou antérieur (doublon, série non triée) n'ouvre pas
            // d'intervalle : on ne compte jamais de temps négatif.
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

/**
 * Zone où l'on a passé le plus de temps. En cas d'égalité parfaite, la zone la
 * plus basse gagne (comparaison stricte : l'effort le plus prudent).
 */
fun dominantZone(distribution: ZoneDistribution): ZoneSlice =
    distribution.slices.reduce { best, s -> if (s.seconds > best.seconds) s else best }
