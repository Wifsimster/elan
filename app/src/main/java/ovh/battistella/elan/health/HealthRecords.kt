// Construction PURE des enregistrements Health Connect d'une séance (port de
// `buildHealthRecords` dans lib/health-connect.ts). Aucune dépendance androidx :
// les constantes du SDK sont recopiées ici pour que la logique se teste sur la
// JVM sans charger le client Health Connect. La traduction en `Record` androidx
// vit dans `AndroidHealthConnectGateway`.
package ovh.battistella.elan.health

import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.HrSample
import kotlin.math.roundToInt

/**
 * Les quatre types d'enregistrement écrits par Élan, avec le suffixe de leur
 * identifiant client et la permission d'écriture androidx correspondante.
 * Sert à l'écriture comme à la suppression : une seule liste, donc pas de
 * miroir orphelin qu'on aurait oublié d'effacer.
 */
enum class HealthRecordType(val suffix: String, val writePermission: String) {
    EXERCISE_SESSION("session", "android.permission.health.WRITE_EXERCISE"),
    DISTANCE("distance", "android.permission.health.WRITE_DISTANCE"),
    ACTIVE_CALORIES("calories", "android.permission.health.WRITE_ACTIVE_CALORIES_BURNED"),
    HEART_RATE("hr", "android.permission.health.WRITE_HEART_RATE");

    companion object {
        /** Écriture seule : Élan n'a pas besoin de lire les données des autres apps. */
        val WRITE_PERMISSIONS: Set<String> = entries.map { it.writePermission }.toSet()
    }
}

/** Type d'exercice Health Connect (`ExerciseSessionRecord.EXERCISE_TYPE_*`) et titre, par activité. */
data class HealthExercise(val exerciseType: Int, val title: String)

private const val EXERCISE_TYPE_BIKING = 8
private const val EXERCISE_TYPE_RUNNING = 56
private const val EXERCISE_TYPE_STRENGTH_TRAINING = 70
private const val EXERCISE_TYPE_WALKING = 79

val HEALTH_EXERCISE: Map<ActivityType, HealthExercise> = mapOf(
    ActivityType.VELO to HealthExercise(EXERCISE_TYPE_BIKING, "Sortie vélo"),
    ActivityType.COURSE to HealthExercise(EXERCISE_TYPE_RUNNING, "Course à pied"),
    ActivityType.MARCHE to HealthExercise(EXERCISE_TYPE_WALKING, "Marche"),
    ActivityType.MUSCU to HealthExercise(EXERCISE_TYPE_STRENGTH_TRAINING, "Séance musculation"),
)

/** Données minimales d'une séance terminée, prêtes à écrire dans Health Connect. */
data class HealthSessionData(
    val type: ActivityType,
    /** ms epoch */
    val startedAt: Long,
    /** ms epoch */
    val endedAt: Long,
    val distanceM: Double? = null,
    val calories: Double? = null,
    val hrSamples: List<HrSample> = emptyList(),
)

/**
 * Identifiant stable d'un enregistrement : (séance × type d'enregistrement). Le
 * type d'activité en fait partie — changer le type d'une séance change donc ses
 * identifiants, d'où la suppression de l'ancien miroir avant réécriture
 * (cf. `HealthConnectManager.removeSession`).
 */
fun healthClientRecordId(type: ActivityType, startedAt: Long, suffix: String): String =
    "elan-${type.key}-$startedAt-$suffix"

/** Un échantillon cardiaque prêt pour Health Connect (BPM entier). */
data class HealthHrPoint(val ts: Long, val bpm: Int)

/**
 * Enregistrement à écrire, indépendant du SDK. Chaque variante porte son
 * `clientRecordId` (déduplication côté Health Connect) et l'intervalle de la
 * séance.
 */
sealed class HealthRecordSpec {
    abstract val type: HealthRecordType
    abstract val clientRecordId: String
    abstract val startedAt: Long
    abstract val endedAt: Long

    data class ExerciseSession(
        override val clientRecordId: String,
        override val startedAt: Long,
        override val endedAt: Long,
        val exerciseType: Int,
        val title: String,
    ) : HealthRecordSpec() {
        override val type get() = HealthRecordType.EXERCISE_SESSION
    }

    data class Distance(
        override val clientRecordId: String,
        override val startedAt: Long,
        override val endedAt: Long,
        val meters: Double,
    ) : HealthRecordSpec() {
        override val type get() = HealthRecordType.DISTANCE
    }

    data class ActiveCalories(
        override val clientRecordId: String,
        override val startedAt: Long,
        override val endedAt: Long,
        val kilocalories: Double,
    ) : HealthRecordSpec() {
        override val type get() = HealthRecordType.ACTIVE_CALORIES
    }

    data class HeartRate(
        override val clientRecordId: String,
        override val startedAt: Long,
        override val endedAt: Long,
        val samples: List<HealthHrPoint>,
    ) : HealthRecordSpec() {
        override val type get() = HealthRecordType.HEART_RATE
    }
}

/**
 * Construit les enregistrements Health Connect d'une séance. Fonction pure.
 * Retourne [] si l'intervalle est invalide — Health Connect rejette les
 * enregistrements dont startTime >= endTime. L'ExerciseSession est toujours
 * émise ; Distance si `distanceM > 0` ; calories si `> 0` ; HeartRate si au
 * moins un échantillon dans `[startedAt, endedAt]` avec `0 < hr < 300`
 * (BPM arrondis : Health Connect attend des entiers).
 */
fun buildHealthRecords(data: HealthSessionData): List<HealthRecordSpec> {
    if (data.endedAt <= data.startedAt) return emptyList()
    val exercise = HEALTH_EXERCISE.getValue(data.type)
    fun id(type: HealthRecordType) = healthClientRecordId(data.type, data.startedAt, type.suffix)

    val records = mutableListOf<HealthRecordSpec>(
        HealthRecordSpec.ExerciseSession(
            clientRecordId = id(HealthRecordType.EXERCISE_SESSION),
            startedAt = data.startedAt,
            endedAt = data.endedAt,
            exerciseType = exercise.exerciseType,
            title = exercise.title,
        ),
    )

    val distance = data.distanceM
    if (distance != null && distance > 0) {
        records += HealthRecordSpec.Distance(id(HealthRecordType.DISTANCE), data.startedAt, data.endedAt, distance)
    }

    val calories = data.calories
    if (calories != null && calories > 0) {
        records += HealthRecordSpec.ActiveCalories(id(HealthRecordType.ACTIVE_CALORIES), data.startedAt, data.endedAt, calories)
    }

    // Health Connect exige des échantillons dans [startTime, endTime] — on écarte
    // ce qui déborde (horloge BLE en avance, échantillons reçus après l'arrêt).
    val samples = data.hrSamples
        .filter { it.ts >= data.startedAt && it.ts <= data.endedAt && it.hr > 0 && it.hr < 300 }
        .map { HealthHrPoint(it.ts, it.hr.roundToInt()) }
    if (samples.isNotEmpty()) {
        records += HealthRecordSpec.HeartRate(id(HealthRecordType.HEART_RATE), data.startedAt, data.endedAt, samples)
    }

    return records
}
