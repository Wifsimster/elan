package ovh.battistella.elan.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.random.Random

enum class GoalMetric(val key: String) {
    SESSIONS("sessions"),
    DISTANCE("distance"),
    TONNAGE("tonnage");

    companion object {
        fun fromKey(key: String?): GoalMetric? = entries.firstOrNull { it.key == key }
    }
}

enum class GoalPeriod(val key: String) {
    WEEK("week"),
    MONTH("month");

    companion object {
        fun fromKey(key: String?): GoalPeriod? = entries.firstOrNull { it.key == key }
    }
}

enum class GoalActivity(val key: String) {
    ALL("all"),
    VELO("velo"),
    COURSE("course"),
    MARCHE("marche"),
    MUSCU("muscu");

    companion object {
        fun fromKey(key: String?): GoalActivity? = entries.firstOrNull { it.key == key }
    }
}

data class Goal(
    val id: String,
    val metric: GoalMetric,
    val period: GoalPeriod,
    val target: Double,
    val activity: GoalActivity,
)

const val GOALS_SETTING_KEY = "goals"

private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 7 * DAY_MS

data class PeriodRange(val fromMs: Long, val toMs: Long)

fun periodRange(period: GoalPeriod, now: Long): PeriodRange {
    if (period == GoalPeriod.WEEK) {
        val fromMs = startOfWeekMs(now)
        val toMs = startOfWeekMs(fromMs + WEEK_MS + 43_200_000L)
        return PeriodRange(fromMs, toMs)
    }
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val first = LocalDate.of(d.year, d.month, 1)
    val fromMs = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMs = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return PeriodRange(fromMs, toMs)
}

data class GoalProgress(
    val goal: Goal,
    val value: Double,
    val target: Double,
    val ratio: Double,
    val done: Boolean,
)

fun computeProgress(goal: Goal, value: Double): GoalProgress {
    val target = goal.target
    val ratio = if (target > 0) (value / target).coerceIn(0.0, 1.0) else 0.0
    return GoalProgress(goal, value, target, ratio, done = target > 0 && value >= target)
}

private val PERIOD_LABEL: Map<GoalPeriod, String> = mapOf(
    GoalPeriod.WEEK to "semaine",
    GoalPeriod.MONTH to "mois",
)

private val ACTIVITY_NOUN: Map<GoalActivity, String> = mapOf(
    GoalActivity.ALL to "séances",
    GoalActivity.VELO to "sorties vélo",
    GoalActivity.COURSE to "sorties course à pied",
    GoalActivity.MARCHE to "sorties marche",
    GoalActivity.MUSCU to "séances muscu",
)

fun describeGoal(goal: Goal): String {
    val per = PERIOD_LABEL.getValue(goal.period)
    val target = goal.target.toJsString()
    return when (goal.metric) {
        GoalMetric.DISTANCE -> "$target km / $per"
        GoalMetric.TONNAGE -> "$target kg soulevés / $per"
        GoalMetric.SESSIONS -> "$target ${ACTIVITY_NOUN.getValue(goal.activity)} / $per"
    }
}

fun formatGoalValue(goal: Goal, value: Double): String = when (goal.metric) {
    GoalMetric.DISTANCE -> "${value.toJsString()} km"
    GoalMetric.TONNAGE -> "${value.roundToLong()} kg"
    GoalMetric.SESSIONS -> value.toJsString()
}

private fun normalizeGoal(input: Any?): Goal? {
    if (input !is JSONObject) return null
    val metric = GoalMetric.fromKey(input.opt("metric") as? String) ?: return null
    val period = GoalPeriod.fromKey(input.opt("period") as? String) ?: return null
    val target = jsToNumber(input.opt("target"))
    if (target == null || !target.isFinite() || target <= 0) return null
    val activity = GoalActivity.fromKey(input.opt("activity") as? String) ?: GoalActivity.ALL
    val rawId = input.opt("id")
    val id = if (rawId is String && rawId.isNotEmpty()) rawId else makeId()
    return Goal(id, metric, period, target, activity)
}

private fun jsToNumber(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
    is Boolean -> if (v) 1.0 else 0.0
    null, JSONObject.NULL -> null
    else -> null
}

fun parseGoals(raw: String?): List<Goal> {
    if (raw.isNullOrEmpty()) return emptyList()
    val arr = try {
        JSONArray(raw)
    } catch (e: JSONException) {
        return emptyList()
    }
    val out = ArrayList<Goal>(arr.length())
    for (i in 0 until arr.length()) {
        val goal = normalizeGoal(arr.opt(i))
        if (goal != null) out.add(goal)
    }
    return out
}

fun serializeGoals(goals: List<Goal>): String {
    val arr = JSONArray()
    for (g in goals) {
        arr.put(
            JSONObject()
                .put("id", g.id)
                .put("metric", g.metric.key)
                .put("period", g.period.key)
                .put("target", g.target)
                .put("activity", g.activity.key),
        )
    }
    return arr.toString()
}

fun upsertGoal(goals: List<Goal>, goal: Goal): List<Goal> {
    val i = goals.indexOfFirst { it.id == goal.id }
    if (i == -1) return goals + goal
    val next = goals.toMutableList()
    next[i] = goal
    return next
}

fun removeGoal(goals: List<Goal>, id: String): List<Goal> = goals.filter { it.id != id }

fun makeGoal(metric: GoalMetric, period: GoalPeriod, target: Double, activity: GoalActivity): Goal =
    Goal(makeId(), metric, period, target, activity)

private const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"

fun makeId(): String {
    val rand = CharArray(6) { BASE36[Random.nextInt(BASE36.length)] }
    return "g${System.currentTimeMillis().toString(36)}${String(rand)}"
}
