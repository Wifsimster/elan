package ovh.battistella.elan.data.repository

import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.AggPoint
import ovh.battistella.elan.domain.Difficulty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SessionUpdate {
    private val assigned = LinkedHashMap<String, Any?>()

    var endedAt: Long? by column("endedAt")
    var durationSec: Int? by column("durationSec")
    var movingTimeSec: Int? by column("movingTimeSec")
    var notes: String? by column("notes")
    var avgHr: Double? by column("avgHr")
    var maxHr: Double? by column("maxHr")
    var distanceM: Double? by column("distanceM")
    var avgSpeedKmh: Double? by column("avgSpeedKmh")
    var maxSpeedKmh: Double? by column("maxSpeedKmh")
    var elevationGainM: Double? by column("elevationGainM")
    var avgCadence: Double? by column("avgCadence")
    var maxCadence: Double? by column("maxCadence")
    var calories: Double? by column("calories")

    val isEmpty: Boolean get() = assigned.isEmpty()

    val columns: List<String> get() = assigned.keys.toList()

    val values: List<Any?> get() = assigned.values.toList()

    private fun <T> column(name: String) = object : ReadWriteProperty<SessionUpdate, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: SessionUpdate, property: KProperty<*>): T? = assigned[name] as T?
        override fun setValue(thisRef: SessionUpdate, property: KProperty<*>, value: T?) {
            assigned[name] = value
        }
    }

    companion object {
        operator fun invoke(block: SessionUpdate.() -> Unit): SessionUpdate = SessionUpdate().apply(block)
    }
}

data class ListSessionsOptions(
    val limit: Int = 100,
    val offset: Int = 0,
    val type: ActivityType? = null,
    val search: String? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
)

data class TrackPointInput(
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    override val altitude: Double? = null,
    override val speedKmh: Double? = null,
    override val hr: Double? = null,
    override val cadence: Double? = null,
) : AggPoint

data class MuscuSetInput(
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val difficulty: Difficulty? = null,
)

data class ImportedSession(
    val type: ActivityType,
    val startedAt: Long,
    val endedAt: Long,
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
    val source: String,
    val externalId: String,
)

sealed interface ImportResult {
    data class Imported(val id: Long) : ImportResult
    data object Duplicate : ImportResult
}

data class DailyDuration(val day: String, val durationSec: Int)

data class ExerciseSummary(
    val exercise: String,
    val sessions: Int,
    val lastWeightKg: Double,
    val lastAt: Long,
    val lastDifficulty: Difficulty?,
)

enum class RecordKind { DISTANCE, ELEVATION, DURATION, SPEED }

enum class RecordScope { YEAR, ALL }

data class SessionRecord(val kind: RecordKind, val scope: RecordScope)
