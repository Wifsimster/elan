package ovh.battistella.elan.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ProgressKind(val key: String) {
    LOAD("load"),
    TIME("time");

    companion object {
        fun fromKey(key: String?): ProgressKind? = entries.firstOrNull { it.key == key }
    }
}

enum class Direction(val key: String) {
    UP("up"),
    DOWN("down"),
    NONE("none");

    companion object {
        fun fromKey(key: String?): Direction? = entries.firstOrNull { it.key == key }
    }
}

const val LOAD_STEP_KG = 2.5

const val TIME_STEP_SEC = 5.0

const val LOAD_CEILING_FACTOR = 3.0

const val TIME_CEILING_FACTOR = 1.5

const val DEFAULT_AUTO_PROGRESSION_ENABLED = true

data class ExercisePoint(
    val sessionId: Long,
    val startedAt: Long,
    val maxWeightKg: Double,
    val topReps: Int,
    val volume: Double,
    val sets: Int,
    val difficulty: Difficulty?,
)

data class TargetResult(
    val value: Double,
    val base: Double,
    val changed: Boolean,
    val direction: Direction,
)

fun nextTarget(
    kind: ProgressKind,
    recent: List<Difficulty?>,
    base: Double,
    floor: Double,
    ceil: Double,
    step: Double? = null,
): TargetResult {
    val effectiveStep = step ?: if (kind == ProgressKind.LOAD) LOAD_STEP_KG else TIME_STEP_SEC
    val none = TargetResult(value = base, base = base, changed = false, direction = Direction.NONE)

    return when (suggestProgression(recent)) {
        ProgressionAdvice.AUGMENTE -> {
            if (base >= ceil) return none
            val value = min(ceil, base + effectiveStep)
            if (value > base) TargetResult(value, base, changed = true, direction = Direction.UP) else none
        }
        ProgressionAdvice.REDUIS -> {
            val value = max(floor, base - effectiveStep)
            if (value < base) TargetResult(value, base, changed = true, direction = Direction.DOWN) else none
        }
        ProgressionAdvice.MAINTIENS -> none
    }
}

data class ExerciseTarget(
    val weightKg: Double,
    val reps: Int,
    val lastWeightKg: Double? = null,
    val bump: Double,
    val bumpKind: ProgressKind? = null,
)

fun targetForExercise(ex: TemplateExercise, history: List<ExercisePoint>, enabled: Boolean): ExerciseTarget {
    val last = history.lastOrNull()
    val recent = history.map { it.difficulty }
    val timed = ex.timed
    val lastWeightKg = if (timed) null else last?.maxWeightKg

    val baseWeight = if (timed) ex.startWeightKg else last?.maxWeightKg ?: ex.startWeightKg
    if (!enabled || ex.autoProgress == null || last == null) {
        return ExerciseTarget(weightKg = baseWeight, reps = defaultReps(ex), lastWeightKg = lastWeightKg, bump = 0.0)
    }

    if (last.difficulty == null) {
        return if (timed) {
            ExerciseTarget(weightKg = 0.0, reps = last.topReps, lastWeightKg = null, bump = 0.0)
        } else {
            ExerciseTarget(
                weightKg = last.maxWeightKg,
                reps = defaultReps(ex),
                lastWeightKg = last.maxWeightKg,
                bump = 0.0,
            )
        }
    }

    if (ex.autoProgress == ProgressKind.LOAD) {
        val res = nextTarget(
            kind = ProgressKind.LOAD,
            recent = recent,
            base = last.maxWeightKg,
            floor = ex.startWeightKg,
            ceil = ex.startWeightKg * LOAD_CEILING_FACTOR,
        )
        val reps = if (res.direction == Direction.UP) ex.repsMin else defaultReps(ex)
        return ExerciseTarget(
            weightKg = res.value,
            reps = reps,
            lastWeightKg = last.maxWeightKg,
            bump = if (res.changed) res.value - res.base else 0.0,
            bumpKind = if (res.changed) ProgressKind.LOAD else null,
        )
    }

    val res = nextTarget(
        kind = ProgressKind.TIME,
        recent = recent,
        base = last.topReps.toDouble(),
        floor = ex.repsMin.toDouble(),
        ceil = (ex.repsMax * TIME_CEILING_FACTOR).roundToInt().toDouble(),
    )
    return ExerciseTarget(
        weightKg = 0.0,
        reps = res.value.roundToInt(),
        lastWeightKg = null,
        bump = if (res.changed) res.value - res.base else 0.0,
        bumpKind = if (res.changed) ProgressKind.TIME else null,
    )
}

data class ProgressionChange(
    val exercise: String,
    val kind: ProgressKind,
    val from: Double,
    val to: Double,
    val direction: Direction,
) {
    init {
        require(direction != Direction.NONE) { "direction doit être UP ou DOWN" }
    }
}

fun isChange(value: Any?): Boolean = progressionChangeFrom(value) != null

fun progressionChangeFrom(value: Any?): ProgressionChange? {
    if (value is ProgressionChange) return value
    if (value !is Map<*, *>) return null
    val exercise = value["exercise"] as? String ?: return null
    val kind = ProgressKind.fromKey(value["kind"] as? String) ?: return null
    val from = (value["from"] as? Number)?.toDouble() ?: return null
    val to = (value["to"] as? Number)?.toDouble() ?: return null
    val direction = Direction.fromKey(value["direction"] as? String) ?: return null
    if (direction == Direction.NONE) return null
    return ProgressionChange(exercise, kind, from, to, direction)
}

fun fmtKg(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',')

fun describeChange(c: ProgressionChange): String =
    if (c.kind == ProgressKind.LOAD) "${c.exercise} ${fmtKg(c.from)} → ${fmtKg(c.to)} kg"
    else "${c.exercise} ${c.from.toJsString()} → ${c.to.toJsString()} s"

fun changeSummaryLine(changes: List<ProgressionChange>): String {
    val ups = changes.count { it.direction == Direction.UP }
    val downs = changes.count { it.direction == Direction.DOWN }
    val parts = ArrayList<String>(2)
    if (ups > 0) parts.add("$ups exercice${if (ups > 1) "s" else ""} renforcé${if (ups > 1) "s" else ""}")
    if (downs > 0) parts.add("$downs allégé${if (downs > 1) "s" else ""}")
    return parts.joinToString(" · ")
}

data class NotificationContent(val title: String, val body: String)

fun notificationContent(changes: List<ProgressionChange>): NotificationContent {
    val hasUp = changes.any { it.direction == Direction.UP }
    val hasDown = changes.any { it.direction == Direction.DOWN }
    val title = when {
        hasUp && !hasDown -> "Ton programme monte d’un cran"
        !hasUp && hasDown -> "On lève le pied cette semaine"
        else -> "Ton programme évolue cette semaine"
    }
    val list = changes.take(3).joinToString(", ") { describeChange(it) }
    val more = if (changes.size > 3) ", +${changes.size - 3}" else ""
    return NotificationContent(title, "$list$more.")
}

fun isoWeekKey(d: LocalDate): String {
    val day = d.dayOfWeek.value
    val t = d.plusDays((4 - day).toLong())
    val yearStart = LocalDate.of(t.year, 1, 1)
    val week = ceil((ChronoUnit.DAYS.between(yearStart, t) + 1) / 7.0).toInt()
    return "${t.year}-W${week.toString().padStart(2, '0')}"
}

fun isoWeekKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    isoWeekKey(java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate())
