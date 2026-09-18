// Consolidation des points GPS d'une sortie (approche Strava/OpenTracks).
//
// Pipeline appliqué à chaque position reçue :
//   1. porte de précision — on écarte les fixes inutilisables (> 50 m) ;
//   2. rejet des téléportations — vitesse implicite impossible à vélo ;
//   3. filtre de Kalman 1D sur lat/lon : gain K = variance / (variance + précision²),
//      la précision rapportée par la puce GNSS sert de bruit de mesure ;
//   4. distance par point d'ancrage (OpenTracks) : la distance n'est créditée
//      qu'après s'être éloigné de quelques mètres de l'ancre, ce qui élimine à
//      la fois la dérive à l'arrêt et le sous-comptage des micro-segments ;
//   5. dénivelé : altitude lissée (médiane glissante) + hystérésis symétrique
//      (GoldenCheetah) — l'altitude GPS est bruitée de ±10 m.
//
// Sans dépendance Android : alimenté fix par fix par le tracker, testable isolément.
package ovh.battistella.elan.domain

import kotlin.math.max

/** Position brute telle que fournie par le service de localisation. */
data class GpsFix(
    val ts: Long,
    override val lat: Double,
    override val lon: Double,
    val altitude: Double?,
    /** Précision horizontale en mètres (null si inconnue). */
    val accuracy: Double?,
    /** Précision verticale en mètres (null si inconnue). */
    val altitudeAccuracy: Double?,
    /** Vitesse Doppler en m/s rapportée par la puce (null si inconnue). */
    val speed: Double?,
) : GeoPoint

/** Point consolidé prêt à être tracé/enregistré. */
data class ConsolidatedPoint(
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    val altitude: Double?,
    override val speedKmh: Double?,
) : TimedPoint

data class ConsolidationResult(
    /** Point lissé à conserver, ou null si le fix a été rejeté. */
    val point: ConsolidatedPoint?,
    /** Distance (m) à ajouter au cumul pour ce fix (0 si rejet ou arrêt). */
    val deltaDistanceM: Double,
    /** Dénivelé positif (m) confirmé par l'hystérésis pour ce fix. */
    val deltaElevationGainM: Double,
)

class GpsConsolidator {
    companion object {
        /** Fixes plus imprécis que ce rayon : inutilisables (défaut OpenTracks : 50 m). */
        const val MAX_ACCURACY_M = 50.0
        /** Vitesse implicite au-delà de laquelle un saut est une aberration (30 m/s = 108 km/h). */
        const val MAX_PLAUSIBLE_SPEED_MS = 30.0
        /**
         * En-dessous de cette vitesse Doppler, on se considère à l'arrêt (~2,5 km/h).
         * Même seuil que `MovingTime` ; l'agrégat de récupération, lui, est à 0,8 m/s
         * parce qu'il travaille sur des points déjà filtrés et se veut plus prudent.
         */
        const val STANDSTILL_SPEED_MS = 0.7
        /** Avance minimale de l'ancre de distance (OpenTracks : 5-10 m). */
        const val DISTANCE_ANCHOR_M = 5.0
        /**
         * Bruit de process minimal du Kalman en m/s. Le filtre s'adapte à la vitesse
         * rapportée (Q ≈ vitesse courante) ; ce plancher garde un lissage fort à
         * l'arrêt, là où la dérive est la plus visible.
         */
        const val KALMAN_MIN_PROCESS_NOISE_MS = 3.0
        /** Fenêtre de la médiane glissante appliquée à l'altitude (échantillons ~1 Hz). */
        const val ALTITUDE_WINDOW = 7
        /**
         * Hystérésis du dénivelé. Strava exige ~10 m de montée soutenue pour de
         * l'altitude GPS pure ; la médiane et la porte de précision verticale en
         * amont permettent de descendre à 5 m sans compter le bruit.
         */
        const val ELEVATION_HYSTERESIS_M = 5.0
        /** Précision verticale au-delà de laquelle l'altitude du fix est ignorée. */
        const val MAX_ALTITUDE_ACCURACY_M = 12.0
        /** Précision horizontale supposée quand la puce n'en fournit pas. */
        const val DEFAULT_ACCURACY_M = 15.0
        /**
         * Au-delà de ce trou temporel (s), la fenêtre d'altitude est vidée : après un
         * tunnel / une perte de signal, les altitudes d'avant ne doivent plus lisser
         * la première mesure de sortie.
         */
        const val ALT_RESET_GAP_S = 30.0

        private val REJECTED = ConsolidationResult(point = null, deltaDistanceM = 0.0, deltaElevationGainM = 0.0)

        /** Ramène une différence de longitude dans [-180, 180] (passage à l'antiméridien). */
        private fun wrapLonDelta(deltaDeg: Double): Double {
            var d = deltaDeg
            while (d > 180) d -= 360
            while (d < -180) d += 360
            return d
        }

        /** Normalise une longitude dans [-180, 180). */
        private fun normalizeLon(lon: Double): Double = ((((lon + 180) % 360) + 360) % 360) - 180

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size shr 1
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }

    // État du filtre de Kalman (position estimée + variance en m²).
    private var estLat = 0.0
    private var estLon = 0.0
    private var variance = -1.0 // < 0 tant qu'aucun fix accepté

    private var lastTs = 0L
    private var lastAccepted: ConsolidatedPoint? = null
    // Ancre de distance : dernier point depuis lequel la distance a été créditée.
    private var distanceAnchor: ConsolidatedPoint? = null

    // Lissage altimétrique : fenêtre brute + ancre de l'hystérésis.
    private val altWindow = ArrayDeque<Double>()
    private var elevationAnchor: Double? = null

    /**
     * Intègre un fix brut et retourne le point consolidé (ou null si rejeté)
     * avec les incréments de distance et de dénivelé à appliquer.
     */
    fun process(fix: GpsFix): ConsolidationResult {
        // 1. Porte de précision.
        val accuracy = max(fix.accuracy ?: DEFAULT_ACCURACY_M, 1.0)
        if (accuracy > MAX_ACCURACY_M) return REJECTED

        // 2. Rejet des téléportations. La vitesse implicite décroît avec le temps
        //    écoulé : après une vraie coupure GPS, le point suivant redevient
        //    plausible de lui-même (pas de blocage).
        val prev = lastAccepted
        if (prev != null) {
            val dt = (fix.ts - prev.ts) / 1000.0
            if (dt <= 0) return REJECTED
            if (haversineMeters(prev, fix) / dt > MAX_PLAUSIBLE_SPEED_MS) return REJECTED
        }

        // 3. Kalman 1D (modèle position constante). La variance croît avec le temps
        //    écoulé, plus vite si la puce indique qu'on roule vite : le filtre suit
        //    le mouvement réel mais écrase la dérive à l'arrêt.
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
            // Innovation de longitude enroulée à l'antiméridien : sans ça, un passage
            // de +179,9° à −179,9° produirait une innovation de ~360°.
            estLon = normalizeLon(estLon + gain * wrapLonDelta(fix.lon - estLon))
            variance *= 1 - gain
        }

        // Trou de signal (tunnel, app suspendue) : purge la fenêtre d'altitude.
        if (lastTs > 0 && (fix.ts - lastTs) / 1000.0 > ALT_RESET_GAP_S) {
            altWindow.clear()
        }
        lastTs = fix.ts

        // Altitude lissée par médiane glissante (robuste aux pics isolés), en
        // ignorant les fixes à la précision verticale médiocre.
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

        // 4. Distance par ancre : créditée seulement après s'être éloigné d'au moins
        //    DISTANCE_ANCHOR_M, et jamais quand le Doppler dit qu'on est à l'arrêt
        //    (la position dérive dans le rayon de précision). Le delta crédité est
        //    la distance complète à l'ancre, pas seulement la part au-delà du seuil.
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

        // 5. Dénivelé : hystérésis symétrique. Une montée n'est créditée — en
        //    totalité — qu'au-delà du seuil ; une descente ne déplace l'ancre
        //    qu'au-delà du même seuil, pour que l'oscillation du bruit vertical ne
        //    fabrique pas de faux dénivelé.
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
