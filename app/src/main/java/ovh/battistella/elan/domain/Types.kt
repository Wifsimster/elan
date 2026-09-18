// Types du domaine — suivi d'activité physique personnel et local.
//
// Convention numérique du portage : les identifiants et horodatages (ms epoch)
// sont des Long, les durées en secondes et les compteurs des Int, toutes les
// grandeurs mesurées (kg, bpm, km/h, m, kcal, tr/min) des Double — comme les
// colonnes REAL de la base et les `number` de l'app d'origine.
package ovh.battistella.elan.domain

/**
 * Type d'activité. `velo`, `course` et `marche` produisent un tracé GPS (voir
 * `isGpsActivity`) ; `muscu` non. La colonne `sessions.type` stocke `key`.
 */
enum class ActivityType(val key: String) {
    VELO("velo"),
    MUSCU("muscu"),
    COURSE("course"),
    MARCHE("marche");

    companion object {
        /** Clé stockée → type, `null` si inconnue (valeur restaurée ou paramètre douteux). */
        fun fromKey(key: String?): ActivityType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Ressenti d'effort d'un exercice sur une séance : pilote le conseil de
 * progression. `null` côté appelant = non noté (anciennes séances, imports).
 */
enum class Difficulty(val key: String) {
    FACILE("facile"),
    MOYEN("moyen"),
    DUR("dur");

    companion object {
        fun fromKey(key: String?): Difficulty? = entries.firstOrNull { it.key == key }
    }
}

/** Objectif d'entraînement musculation — fourchettes de reps, repos et intensité conseillés. */
enum class TrainingGoal(val key: String) {
    FORCE("force"),
    HYPERTROPHIE("hypertrophie"),
    ENDURANCE("endurance"),
    TONIFICATION("tonification"),
    PERTE_POIDS("perte-poids");

    companion object {
        fun fromKey(key: String?): TrainingGoal? = entries.firstOrNull { it.key == key }
    }
}

/** Sexe biologique — affine la recommandation de charge. `null` côté appelant = non précisé. */
enum class Sex(val key: String) {
    H("h"),
    F("f");

    companion object {
        fun fromKey(key: String?): Sex? = entries.firstOrNull { it.key == key }
    }
}

/** Quelque chose d'horodaté en ms epoch (échantillons capteurs, points GPS). */
interface Timestamped {
    val ts: Long
}

/** Coordonnée géographique en degrés décimaux. */
interface GeoPoint {
    val lat: Double
    val lon: Double
}

/** Une mesure cardiaque horodatée, éventuellement absente (point GPS sans ceinture). */
interface HrReading : Timestamped {
    val hr: Double?
}

/** Une séance d'entraînement enregistrée. */
data class Session(
    val id: Long,
    val type: ActivityType,
    /** Début en ms epoch. */
    val startedAt: Long,
    /** Fin en ms epoch (null si en cours). */
    val endedAt: Long?,
    /** Durée active en secondes (hors pauses). */
    val durationSec: Int,
    /**
     * Temps « en mouvement » en secondes (activités GPS) : durée hors arrêts.
     * `null` quand il n'a pas pu être calculé — l'affichage retombe sur `durationSec`.
     */
    val movingTimeSec: Int?,
    val notes: String?,
    val avgHr: Double?,
    val maxHr: Double?,
    val distanceM: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val elevationGainM: Double?,
    /** Cadence (tr/min), capteur vélo. */
    val avgCadence: Double?,
    val maxCadence: Double?,
    val calories: Double?,
    /** Provenance : null = enregistré dans l'app, 'strava' = importé d'un fichier. */
    val source: String?,
    /** Clé de déduplication des séances importées (null pour les séances natives). */
    val externalId: String?,
    /** Nombre de séries muscu : agrégat de lecture de `listSessions` seulement, `null` ailleurs. */
    val setCount: Int? = null,
    /** Nombre d'exercices muscu distincts. Mêmes règles que `setCount`. */
    val exerciseCount: Int? = null,
)

/** Un point GPS d'un tracé. */
data class TrackPoint(
    val id: Long,
    val sessionId: Long,
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    override val altitude: Double?,
    override val speedKmh: Double?,
    override val hr: Double?,
    /** Cadence en tours/min au point le plus proche (si capteur connecté). */
    override val cadence: Double?,
) : AggPoint

/** Un échantillon de fréquence cardiaque horodaté, accumulé pendant une séance. */
data class HrSample(override val ts: Long, override val hr: Double) : HrReading

/** Une pesée du journal de poids corporel. */
data class BodyMeasurement(
    val id: Long,
    /** Date de la mesure en ms epoch. */
    val measuredAt: Long,
    val weightKg: Double,
)

/** Une série de musculation (un exercice peut avoir plusieurs séries). */
data class MuscuSet(
    val id: Long,
    val sessionId: Long,
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    /** Ressenti dénormalisé sur chaque série de l'exercice. `null` si non noté. */
    val difficulty: Difficulty?,
)

/** Profil utilisateur : calories, zones cardio et charges/reps conseillées. Défauts = profil neuf. */
data class Profile(
    val weightKg: Double = 70.0,
    /** Taille en cm. */
    val heightCm: Double = 175.0,
    /** FC max théorique, pour les zones cardio. */
    val maxHr: Double = 190.0,
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHIE,
    /** `null` = non précisé. */
    val sex: Sex? = null,
)

/** Statistiques agrégées sur une période. */
data class PeriodStats(
    val sessionCount: Int,
    val totalDurationSec: Int,
    val totalDistanceM: Double,
    val totalCalories: Double,
)
