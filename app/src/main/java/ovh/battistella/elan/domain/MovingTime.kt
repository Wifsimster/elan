// Temps « en mouvement » d'une sortie — façon Strava : on ne compte que les
// intervalles où l'on avance réellement, en écartant les arrêts (feu rouge,
// pause café… ou un chrono qu'on a oublié de stopper).
//
// Détection de l'arrêt, par segment entre deux points successifs :
//   - si le point porte une vitesse (Doppler GNSS, fiable à l'arrêt), on s'y fie ;
//   - sinon, on retombe sur la vitesse implicite du segment (distance / temps),
//     en exigeant une distance minimale pour ne pas confondre la dérive de la
//     position à l'arrêt (dans le rayon de précision) avec un vrai déplacement.
// Un intervalle anormalement long (perte de signal, app suspendue) est plafonné
// pour ne pas gonfler le temps sur un trou de tracé.
package ovh.battistella.elan.domain

import kotlin.math.min

/** Point minimal exploitable pour le calcul (TrackPoint, ConsolidatedPoint…). */
interface TimedPoint : GeoPoint, Timestamped {
    val speedKmh: Double?
}

/**
 * En-dessous de cette vitesse on se considère à l'arrêt (~2,5 km/h). Aligné sur
 * `GpsConsolidator.STANDSTILL_SPEED_MS` : les deux décrivent le même « à l'arrêt ».
 */
private const val MOVING_THRESHOLD_MS = 0.7
/** Distance minimale d'un segment pour le juger « en mouvement » faute de Doppler. */
private const val MIN_SEGMENT_M = 4.0
/** Au-delà de cet écart entre deux points, l'intervalle est ambigu : plafonné. */
private const val MAX_SEGMENT_SEC = 30.0

/**
 * Temps en mouvement (secondes, arrondi) déduit d'une suite de points
 * chronologiques. Renvoie 0 s'il y a moins de deux points : l'appelant retombe
 * alors sur la durée totale.
 */
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
