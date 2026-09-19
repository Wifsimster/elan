// Objectifs d'entraînement (100 % local) : « 3 sorties par semaine »,
// « 100 km par mois », « 5000 kg de tonnage par mois »… Les définitions sont
// stockées en JSON dans la table `settings` (clé `goals`) — donc incluses dans
// la sauvegarde S3 et l'export coach sans migration de schéma — et la
// progression est calculée À LA VOLÉE depuis les séances terminées.
//
// Ce fichier porte la logique pure (périodes, progression, libellés, parsing,
// opérations de liste) ; la mesure en base (`measureGoal`) relève de la couche
// données.
package ovh.battistella.elan.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.random.Random

/** Métrique suivie : nombre de séances, distance vélo (km) ou tonnage muscu (kg). */
enum class GoalMetric(val key: String) {
    SESSIONS("sessions"),
    DISTANCE("distance"),
    TONNAGE("tonnage");

    companion object {
        fun fromKey(key: String?): GoalMetric? = entries.firstOrNull { it.key == key }
    }
}

/** Fenêtre de suivi : semaine (lundi→dimanche) ou mois calendaire. */
enum class GoalPeriod(val key: String) {
    WEEK("week"),
    MONTH("month");

    companion object {
        fun fromKey(key: String?): GoalPeriod? = entries.firstOrNull { it.key == key }
    }
}

/** Type d'activité compté (pertinent pour `sessions` et `distance`). */
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
    /** Cible dans l'unité naturelle : séances (n), km (distance), kg (tonnage). */
    val target: Double,
    /**
     * Restreint le comptage à un type d'activité — pour `sessions` comme pour
     * `distance` (« 50 km de course par mois »). Ignoré pour `tonnage`, qui n'existe
     * qu'en musculation. `ALL` = toutes activités confondues.
     */
    val activity: GoalActivity,
)

/** Clé de réglage sous laquelle la couche données persiste la liste d'objectifs. */
const val GOALS_SETTING_KEY = "goals"

private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 7 * DAY_MS

// ---------------------------------------------------------------------------
// Logique pure (périodes, progression, libellés, parsing, opérations de liste)
// ---------------------------------------------------------------------------

/** Bornes `[fromMs, toMs)` d'une période. */
data class PeriodRange(val fromMs: Long, val toMs: Long)

/**
 * Bornes `[fromMs, toMs)` de la période courante contenant `now` (ms epoch,
 * heure locale = fuseau par défaut du système, comme `startOfWeekMs`).
 */
fun periodRange(period: GoalPeriod, now: Long): PeriodRange {
    if (period == GoalPeriod.WEEK) {
        val fromMs = startOfWeekMs(now)
        // Fin = début du lundi SUIVANT (re-floor local), pas `from + 168 h` : aux
        // changements d'heure la semaine dure 167 ou 169 h, et un +168 h fixe
        // décalait la borne d'une heure (séances de fin de dimanche mal comptées).
        val toMs = startOfWeekMs(fromMs + WEEK_MS + 43_200_000L) // +7,5 j → lundi suivant
        return PeriodRange(fromMs, toMs)
    }
    // Mois calendaire local : du 1er du mois au 1er du mois suivant.
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val first = LocalDate.of(d.year, d.month, 1)
    val fromMs = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMs = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return PeriodRange(fromMs, toMs)
}

data class GoalProgress(
    val goal: Goal,
    /** Valeur réalisée sur la période, dans l'unité de la cible. */
    val value: Double,
    val target: Double,
    /** Avancement borné à [0, 1] (pour une barre/anneau). */
    val ratio: Double,
    /** Objectif atteint ou dépassé. */
    val done: Boolean,
)

/** Construit l'avancement d'un objectif à partir de la valeur réalisée. */
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

/** Libellé court d'un objectif : « 3 sorties vélo / semaine », « 100 km / mois ». */
fun describeGoal(goal: Goal): String {
    val per = PERIOD_LABEL.getValue(goal.period)
    val target = goal.target.toJsString()
    return when (goal.metric) {
        GoalMetric.DISTANCE -> "$target km / $per"
        GoalMetric.TONNAGE -> "$target kg soulevés / $per"
        GoalMetric.SESSIONS -> "$target ${ACTIVITY_NOUN.getValue(goal.activity)} / $per"
    }
}

/** Valeur réalisée formatée avec son unité (« 2 », « 42.5 km », « 5000 kg »). */
fun formatGoalValue(goal: Goal, value: Double): String = when (goal.metric) {
    GoalMetric.DISTANCE -> "${value.toJsString()} km"
    GoalMetric.TONNAGE -> "${value.roundToLong()} kg"
    GoalMetric.SESSIONS -> value.toJsString()
}

/** Valide et normalise une entrée JSON inconnue en `Goal` (ou `null` si invalide). */
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

/** Équivalent de `Number(x)` sur les formes plausibles (nombre, chaîne numérique), `null` sinon. */
private fun jsToNumber(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
    is Boolean -> if (v) 1.0 else 0.0
    null, JSONObject.NULL -> null
    else -> null
}

/**
 * Lit une liste d'objectifs depuis la valeur de réglage (robuste : ignore le
 * JSON corrompu ou les entrées invalides plutôt que de planter).
 */
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

/** Ajoute un objectif, ou remplace celui de même `id`. */
fun upsertGoal(goals: List<Goal>, goal: Goal): List<Goal> {
    val i = goals.indexOfFirst { it.id == goal.id }
    if (i == -1) return goals + goal
    val next = goals.toMutableList()
    next[i] = goal
    return next
}

fun removeGoal(goals: List<Goal>, id: String): List<Goal> = goals.filter { it.id != id }

/** Crée un objectif avec un identifiant frais. */
fun makeGoal(metric: GoalMetric, period: GoalPeriod, target: Double, activity: GoalActivity): Goal =
    Goal(makeId(), metric, period, target, activity)

private const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"

/** `g` + horodatage en base 36 + 6 caractères aléatoires en base 36. */
fun makeId(): String {
    val rand = CharArray(6) { BASE36[Random.nextInt(BASE36.length)] }
    return "g${System.currentTimeMillis().toString(36)}${String(rand)}"
}
