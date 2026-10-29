package ovh.battistella.elan.health

import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.HrSample
import kotlin.math.roundToInt

enum class HealthRecordType(val suffix: String, val writePermission: String) {
    EXERCISE_SESSION("session", "android.permission.health.WRITE_EXERCISE"),
    DISTANCE("distance", "android.permission.health.WRITE_DISTANCE"),
    ACTIVE_CALORIES("calories", "android.permission.health.WRITE_ACTIVE_CALORIES_BURNED"),
    HEART_RATE("hr", "android.permission.health.WRITE_HEART_RATE");

    companion object {
        val WRITE_PERMISSIONS: Set<String> = entries.map { it.writePermission }.toSet()
    }
}

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

data class HealthSessionData(
    val type: ActivityType,
    val startedAt: Long,
    val endedAt: Long,
    val distanceM: Double? = null,
    val calories: Double? = null,
    val hrSamples: List<HrSample> = emptyList(),
)

fun healthClientRecordId(type: ActivityType, startedAt: Long, suffix: String): String =
    "elan-${type.key}-$startedAt-$suffix"

data class HealthHrPoint(val ts: Long, val bpm: Int)

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

    val samples = data.hrSamples
        .filter { it.ts >= data.startedAt && it.ts <= data.endedAt && it.hr > 0 && it.hr < 300 }
        .map { HealthHrPoint(it.ts, it.hr.roundToInt()) }
    if (samples.isNotEmpty()) {
        records += HealthRecordSpec.HeartRate(id(HealthRecordType.HEART_RATE), data.startedAt, data.endedAt, samples)
    }

    return records
}
