// Modèle local de la séance muscu en cours (port de `Exercise`/`SetRow` de
// `muscu.tsx`) et sa (dé)sérialisation JSON pour le brouillon `muscu_draft`.
// Le format JSON reprend les clés de l'app d'origine pour qu'un brouillon
// importé de l'ancienne base soit repris tel quel.
package ovh.battistella.elan.ui.screens.strength

import org.json.JSONArray
import org.json.JSONObject
import ovh.battistella.elan.domain.CatalogExercise
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.ExerciseTarget
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.RecoProfile
import ovh.battistella.elan.domain.StatsExercise
import ovh.battistella.elan.domain.StatsSet
import ovh.battistella.elan.domain.TemplateExercise
import ovh.battistella.elan.domain.exerciseHowTo
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.domain.recoHint
import ovh.battistella.elan.domain.recommend
import ovh.battistella.elan.domain.targetHint
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

data class SetRow(val reps: Int, val weightKg: Double, val done: Boolean = false)

data class StrengthExercise(
    val id: String,
    val name: String,
    val sets: List<SetRow>,
    /** Cible issue d'un programme, ex. « 3 × 8-12 / bras » (absente en saisie libre). */
    val target: String? = null,
    /** Unité des reps : « sec » pour le gainage chronométré, « reps » sinon. */
    val repUnit: String = "reps",
    /** Dernière charge enregistrée pour cet exercice (amorce de progression). */
    val lastWeight: Double? = null,
    val howTo: String? = null,
    val muscles: List<String> = emptyList(),
    /** Glyphe MDI illustrant le mouvement. */
    val icon: String? = null,
    /** Clé d'illustration photo (paire départ → fin). */
    val imageKey: String? = null,
    /** Ressenti d'effort noté pendant la séance. */
    val difficulty: Difficulty? = null,
    /** Ajustement appliqué par la progression auto au pré-remplissage (kg ou s ; 0 = aucun). */
    val bump: Double = 0.0,
    val bumpKind: ProgressKind? = null,
) {
    fun toStats(): StatsExercise = StatsExercise(sets.map { StatsSet(it.reps, it.weightKg, it.done) })
}

/** Exercices de saisie rapide (chips sous le champ libre). */
val COMMON_EXERCISES: List<String> = listOf(
    "Développé couché",
    "Squat",
    "Soulevé de terre",
    "Développé militaire",
    "Rowing",
    "Tractions",
    "Curl biceps",
    "Dips",
)

/** Série par défaut d'un exercice saisi à la main. */
val DEFAULT_SET = SetRow(reps = 10, weightKg = 20.0)

private val uid = AtomicLong(0)

/** Identifiant local frais (jamais persisté tel quel : régénéré à la reprise d'un brouillon). */
fun nextExerciseId(): String = "e${uid.getAndIncrement()}"

/** Indice « progression auto » sous le nom, ex. « Auto : +2,5 kg cette semaine ». */
fun bumpHint(bump: Double, kind: ProgressKind?): String {
    val unit = if (kind == ProgressKind.TIME) "s" else "kg"
    val amount = if (kind == ProgressKind.TIME) abs(bump).roundToInt().toString() else fmtKg(abs(bump))
    return if (bump > 0) "Auto : +$amount $unit cette semaine" else "Allégé : −$amount $unit cette semaine"
}

/** Exercice de séance depuis un exercice de programme et sa cible pré-calculée. */
fun exerciseFromTemplate(ex: TemplateExercise, target: ExerciseTarget): StrengthExercise = StrengthExercise(
    id = nextExerciseId(),
    name = ex.name,
    target = targetHint(ex),
    repUnit = if (ex.timed) "sec" else "reps",
    howTo = ex.howTo,
    muscles = ex.muscles,
    icon = ex.icon,
    imageKey = ex.imageKey,
    lastWeight = target.lastWeightKg,
    bump = target.bump,
    bumpKind = target.bumpKind,
    sets = List(ex.sets) { SetRow(target.reps, target.weightKg) },
)

/**
 * Exercice de séance depuis une fiche du catalogue : reps, séries et charge
 * pré-remplis depuis la recommandation, la dernière charge enregistrée primant
 * si elle existe (continuité de la progression).
 */
fun exerciseFromCatalog(ex: CatalogExercise, profile: RecoProfile, lastWeight: Double?): StrengthExercise {
    val rec = recommend(profile, ex)
    val reps = ((rec.repsMin + rec.repsMax) / 2.0).roundToInt()
    val unloaded = ex.timed || ex.loadFactor == 0.0
    val weight = if (unloaded) 0.0 else lastWeight ?: rec.weightKg
    return StrengthExercise(
        id = nextExerciseId(),
        name = ex.name,
        target = recoHint(ex, rec),
        repUnit = if (rec.timed) "sec" else "reps",
        howTo = exerciseHowTo(ex.id),
        muscles = ex.muscles,
        icon = ex.icon,
        imageKey = ex.imageKey,
        lastWeight = if (unloaded) null else lastWeight,
        sets = List(rec.sets) { SetRow(reps, weight) },
    )
}

/** Exercice saisi à la main : une série 10 × 20 kg. */
fun exerciseFromName(name: String): StrengthExercise =
    StrengthExercise(id = nextExerciseId(), name = name, sets = listOf(DEFAULT_SET))

// ---- JSON du brouillon --------------------------------------------------

fun StrengthExercise.toJson(): JSONObject {
    val o = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("repUnit", repUnit)
        .put("sets", JSONArray().also { arr ->
            sets.forEach { s -> arr.put(JSONObject().put("reps", s.reps).put("weightKg", s.weightKg).put("done", s.done)) }
        })
    target?.let { o.put("target", it) }
    lastWeight?.let { o.put("lastWeight", it) }
    howTo?.let { o.put("howTo", it) }
    if (muscles.isNotEmpty()) o.put("muscles", JSONArray(muscles))
    icon?.let { o.put("icon", it) }
    imageKey?.let { o.put("imageKey", it) }
    difficulty?.let { o.put("difficulty", it.key) }
    if (bump != 0.0) {
        o.put("bump", bump)
        bumpKind?.let { o.put("bumpKind", it.key) }
    }
    return o
}

fun exercisesToJson(exercises: List<StrengthExercise>): JSONArray =
    JSONArray().also { arr -> exercises.forEach { arr.put(it.toJson()) } }

/** Relit un exercice du brouillon ; `null` si mal formé. L'id est régénéré (compteur repassé à 0 au redémarrage). */
fun exerciseFromJson(o: JSONObject): StrengthExercise? {
    val name = o.optString("name", "")
    if (name.isEmpty()) return null
    val setsJson = o.optJSONArray("sets") ?: return null
    val sets = ArrayList<SetRow>(setsJson.length())
    for (i in 0 until setsJson.length()) {
        val s = setsJson.optJSONObject(i) ?: continue
        sets.add(SetRow(reps = s.optInt("reps", 0), weightKg = s.optDouble("weightKg", 0.0), done = s.optBoolean("done", false)))
    }
    val musclesJson = o.optJSONArray("muscles")
    val muscles = if (musclesJson == null) emptyList() else List(musclesJson.length()) { musclesJson.optString(it) }.filter { it.isNotEmpty() }
    val bump = o.optDouble("bump", 0.0).takeIf { it.isFinite() } ?: 0.0
    return StrengthExercise(
        id = nextExerciseId(),
        name = name,
        sets = sets,
        target = o.optString("target").takeIf { o.has("target") && it.isNotEmpty() },
        repUnit = o.optString("repUnit", "reps").ifEmpty { "reps" },
        lastWeight = if (o.has("lastWeight") && !o.isNull("lastWeight")) o.optDouble("lastWeight") else null,
        howTo = o.optString("howTo").takeIf { o.has("howTo") && it.isNotEmpty() },
        muscles = muscles,
        icon = o.optString("icon").takeIf { o.has("icon") && it.isNotEmpty() },
        imageKey = o.optString("imageKey").takeIf { o.has("imageKey") && it.isNotEmpty() },
        difficulty = Difficulty.fromKey(o.optString("difficulty").takeIf { o.has("difficulty") }),
        bump = bump,
        bumpKind = ProgressKind.fromKey(o.optString("bumpKind").takeIf { o.has("bumpKind") }),
    )
}

fun exercisesFromJson(arr: JSONArray): List<StrengthExercise> {
    val out = ArrayList<StrengthExercise>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        exerciseFromJson(o)?.let(out::add)
    }
    return out
}
