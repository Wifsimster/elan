package ovh.battistella.elan.domain

enum class ActivityType(val key: String) {
    VELO("velo"),
    MUSCU("muscu"),
    COURSE("course"),
    MARCHE("marche");

    companion object {
        fun fromKey(key: String?): ActivityType? = entries.firstOrNull { it.key == key }
    }
}

enum class Difficulty(val key: String) {
    FACILE("facile"),
    MOYEN("moyen"),
    DUR("dur");

    companion object {
        fun fromKey(key: String?): Difficulty? = entries.firstOrNull { it.key == key }
    }
}

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

enum class Sex(val key: String) {
    H("h"),
    F("f");

    companion object {
        fun fromKey(key: String?): Sex? = entries.firstOrNull { it.key == key }
    }
}

interface Timestamped {
    val ts: Long
}

interface GeoPoint {
    val lat: Double
    val lon: Double
}

interface HrReading : Timestamped {
    val hr: Double?
}

data class Session(
    val id: Long,
    val type: ActivityType,
    val startedAt: Long,
    val endedAt: Long?,
    val durationSec: Int,
    val movingTimeSec: Int?,
    val notes: String?,
    val avgHr: Double?,
    val maxHr: Double?,
    val distanceM: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val elevationGainM: Double?,
    val avgCadence: Double?,
    val maxCadence: Double?,
    val calories: Double?,
    val source: String?,
    val externalId: String?,
    val setCount: Int? = null,
    val exerciseCount: Int? = null,
)

data class TrackPoint(
    val id: Long,
    val sessionId: Long,
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    override val altitude: Double?,
    override val speedKmh: Double?,
    override val hr: Double?,
    override val cadence: Double?,
) : AggPoint

data class HrSample(override val ts: Long, override val hr: Double) : HrReading

data class BodyMeasurement(
    val id: Long,
    val measuredAt: Long,
    val weightKg: Double,
)

data class MuscuSet(
    val id: Long,
    val sessionId: Long,
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val difficulty: Difficulty?,
)

data class Profile(
    val weightKg: Double = 70.0,
    val heightCm: Double = 175.0,
    val maxHr: Double = 190.0,
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHIE,
    val sex: Sex? = null,
)

data class PeriodStats(
    val sessionCount: Int,
    val totalDurationSec: Int,
    val totalDistanceM: Double,
    val totalCalories: Double,
)
