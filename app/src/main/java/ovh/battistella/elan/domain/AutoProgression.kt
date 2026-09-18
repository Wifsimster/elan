// Progression automatique du programme de musculation, semaine après semaine,
// d'après le ressenti noté (facile / moyen / dur). C'est LA logique qui « relève
// la difficulté » du plan tout seul : on monte la charge des exercices notés
// faciles, on allège ceux notés durs, d'un cran par semaine au maximum.
//
// Principes de conception (issus de la revue croisée data / UX / sécurité) :
//  - 100 % local, aucune dépendance réseau.
//  - La cible est TOUJOURS calculée à partir de la dernière valeur ENREGISTRÉE
//    (charge/durée réellement loguée), jamais d'un conseil précédent : pas de
//    double-comptage, la boucle s'auto-corrige (si l'utilisateur ignore la montée
//    et re-logue la même charge, on reproposera la même montée — pas de dérive).
//  - Cadencé à un pas par semaine ISO : impossible de s'emballer.
//  - Sécurité : seuls les deux full-body chargés progressent (`autoProgress`
//    posé sur `Program.kt`) ; les exercices de rééducation / mobilité (dos,
//    cervicales) ne sont JAMAIS touchés. Un ressenti « dur » déclenche un
//    allègement (deload), un plancher empêche de descendre sous la charge de
//    départ, un plafond empêche de monter à l'infini.
//
// Ce fichier ne contient que la partie PURE du module TypeScript : la config,
// l'état persisté et l'orchestration hebdomadaire (lecture historique,
// persistance, notification) relèvent de la couche données.
package ovh.battistella.elan.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Nature de la progression : charge (kg) ou gainage chronométré (secondes). */
enum class ProgressKind(val key: String) {
    LOAD("load"),
    TIME("time");

    companion object {
        fun fromKey(key: String?): ProgressKind? = entries.firstOrNull { it.key == key }
    }
}

/** Sens d'un ajustement. */
enum class Direction(val key: String) {
    UP("up"),
    DOWN("down"),
    NONE("none");

    companion object {
        fun fromKey(key: String?): Direction? = entries.firstOrNull { it.key == key }
    }
}

/** Pas de montée hebdomadaire : 2,5 kg (grille du stepper de charge de l'écran muscu). */
const val LOAD_STEP_KG = 2.5

/** Pas d'allongement du gainage : +5 s par semaine. */
const val TIME_STEP_SEC = 5.0

/** Plafond de charge : `startWeightKg × 3` (au-delà, montée manuelle uniquement). */
const val LOAD_CEILING_FACTOR = 3.0

/** Plafond de durée du gainage : `repsMax × 1,5` (planche 40→60 s, latéral 30→45 s). */
const val TIME_CEILING_FACTOR = 1.5

/** Progression auto activée PAR DÉFAUT : le plan monte tout seul tant que l'utilisateur ne coupe pas. */
const val DEFAULT_AUTO_PROGRESSION_ENABLED = true

/**
 * Historique d'un exercice, une ligne par séance terminée (agrégat de lecture de
 * la base : charge max, reps de la meilleure série, volume, nombre de séries).
 */
data class ExercisePoint(
    val sessionId: Long,
    val startedAt: Long,
    val maxWeightKg: Double,
    val topReps: Int,
    val volume: Double,
    val sets: Int,
    /** Ressenti noté pour cet exercice sur la séance (`null` si non noté). */
    val difficulty: Difficulty?,
)

// ---------------------------------------------------------------------------
// Cœur pur : calcul de la prochaine cible
// ---------------------------------------------------------------------------

data class TargetResult(
    /** Nouvelle valeur cible : charge en kg (LOAD) ou secondes (TIME). */
    val value: Double,
    /** Valeur de base (dernière enregistrée) avant ajustement. */
    val base: Double,
    /** Vrai si `value` diffère de `base` (montée ou allègement effectif). */
    val changed: Boolean,
    val direction: Direction,
)

/**
 * Prochaine cible d'un exercice à partir de ses ressentis récents et de sa
 * dernière valeur enregistrée. Fonction pure : le classement du ressenti est
 * délégué à `suggestProgression` (déjà testé), on ne fait ici qu'appliquer un
 * pas borné.
 *
 *  - AUGMENTE  -> +1 pas (plafonné à `ceil`)
 *  - REDUIS    -> −1 pas (planchonné à `floor`) — allègement sur « dur »
 *  - MAINTIENS -> inchangé
 *
 * @param recent ressentis récents, du plus ancien au plus récent (`null` = non noté)
 * @param base dernière valeur enregistrée : charge max (LOAD) ou durée (TIME)
 * @param floor plancher : jamais en-dessous (charge de départ / repsMin secondes)
 * @param ceil plafond : jamais au-dessus (au-delà, on maintient)
 */
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
            if (base >= ceil) return none // plafond atteint : on maintient
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

// ---------------------------------------------------------------------------
// Cible de pré-remplissage d'un exercice de séance (pur : historique en entrée)
// ---------------------------------------------------------------------------

data class ExerciseTarget(
    /** Charge pré-remplie (kg). 0 pour les exercices chronométrés / non chargés. */
    val weightKg: Double,
    /** Reps pré-remplies (ou secondes pour le gainage chronométré). */
    val reps: Int,
    /** Dernière charge enregistrée (kg) — pour l'indice « dernière fois ». */
    val lastWeightKg: Double? = null,
    /** Delta appliqué par la progression auto (kg ou s), 0 si aucun. */
    val bump: Double,
    /** Nature du delta quand `bump != 0`. */
    val bumpKind: ProgressKind? = null,
)

/**
 * Cible de pré-remplissage d'un exercice, en tenant compte (ou non) de la
 * progression auto. Fonction PURE : reçoit l'historique de l'exercice (du plus
 * ancien au plus récent, séances terminées) plutôt que d'y accéder.
 *
 * C'est l'unique endroit où le pré-remplissage est décidé : la charge portée par
 * la séance précédente est remplacée par cette cible, qui EST cette charge plus,
 * au plus, un pas gagné — d'où l'absence de double comptage.
 */
fun targetForExercise(ex: TemplateExercise, history: List<ExercisePoint>, enabled: Boolean): ExerciseTarget {
    val last = history.lastOrNull()
    val recent = history.map { it.difficulty }
    val timed = ex.timed
    val lastWeightKg = if (timed) null else last?.maxWeightKg

    // Repli (progression auto désactivée, exercice non progressable, ou aucune
    // séance passée) : continuité simple — dernière charge enregistrée, sinon
    // charge de départ ; reps au milieu de la fourchette.
    val baseWeight = if (timed) ex.startWeightKg else last?.maxWeightKg ?: ex.startWeightKg
    if (!enabled || ex.autoProgress == null || last == null) {
        return ExerciseTarget(weightKg = baseWeight, reps = defaultReps(ex), lastWeightKg = lastWeightKg, bump = 0.0)
    }

    // Sécurité : si la dernière séance n'a pas été notée, on ne progresse pas
    // (le silence n'autorise pas une montée de charge). On garde la continuité.
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
        // Double progression : après une montée de charge, on repart en bas de la
        // fourchette de reps ; sinon on garde le milieu habituel.
        val reps = if (res.direction == Direction.UP) ex.repsMin else defaultReps(ex)
        return ExerciseTarget(
            weightKg = res.value,
            reps = reps,
            lastWeightKg = last.maxWeightKg,
            bump = if (res.changed) res.value - res.base else 0.0,
            bumpKind = if (res.changed) ProgressKind.LOAD else null,
        )
    }

    // autoProgress == TIME : on progresse la durée (secondes), pas la charge.
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

// ---------------------------------------------------------------------------
// Description des changements (bannière, notification, revue) — pur
// ---------------------------------------------------------------------------

/** Un ajustement de charge/durée appliqué à un exercice pour la nouvelle semaine. */
data class ProgressionChange(
    val exercise: String,
    val kind: ProgressKind,
    /** Valeur avant (dernière enregistrée). */
    val from: Double,
    /** Valeur après (nouvelle cible). */
    val to: Double,
    /** UP ou DOWN uniquement (jamais NONE). */
    val direction: Direction,
) {
    init {
        require(direction != Direction.NONE) { "direction doit être UP ou DOWN" }
    }
}

/**
 * Valide une entrée désérialisée de l'état persisté (`Map` `exercise`/`kind`/
 * `from`/`to`/`direction`) — équivalent du garde `isChange` TypeScript.
 */
fun isChange(value: Any?): Boolean = progressionChangeFrom(value) != null

/** Convertit une entrée désérialisée en `ProgressionChange`, ou `null` si mal formée. */
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

/** Charge lisible : entier tel quel, sinon une décimale avec virgule (« 22,5 »). */
fun fmtKg(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',')

/** « Goblet squat 20 → 22,5 kg » / « Gainage planche 30 → 35 s ». */
fun describeChange(c: ProgressionChange): String =
    if (c.kind == ProgressKind.LOAD) "${c.exercise} ${fmtKg(c.from)} → ${fmtKg(c.to)} kg"
    else "${c.exercise} ${c.from.toJsString()} → ${c.to.toJsString()} s"

/** Résumé court : « 3 exercices renforcés · 1 allégé ». '' si aucun changement. */
fun changeSummaryLine(changes: List<ProgressionChange>): String {
    val ups = changes.count { it.direction == Direction.UP }
    val downs = changes.count { it.direction == Direction.DOWN }
    val parts = ArrayList<String>(2)
    if (ups > 0) parts.add("$ups exercice${if (ups > 1) "s" else ""} renforcé${if (ups > 1) "s" else ""}")
    if (downs > 0) parts.add("$downs allégé${if (downs > 1) "s" else ""}")
    return parts.joinToString(" · ")
}

/** Titre + corps de la notification « programme régénéré ». */
data class NotificationContent(val title: String, val body: String)

/** Titre + corps de la notification « programme régénéré ». Pur. */
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

// ---------------------------------------------------------------------------
// Semaine ISO (idempotence de l'évaluation hebdomadaire) — pur
// ---------------------------------------------------------------------------

/**
 * Clé de semaine ISO-8601, ex. « 2026-W27 ». Deux dates de la même semaine ->
 * même clé. Même algorithme que la source (jeudi de la semaine courante).
 */
fun isoWeekKey(d: LocalDate): String {
    val day = d.dayOfWeek.value // lundi = 1 … dimanche = 7
    val t = d.plusDays((4 - day).toLong()) // jeudi de la semaine courante
    val yearStart = LocalDate.of(t.year, 1, 1)
    val week = ceil((ChronoUnit.DAYS.between(yearStart, t) + 1) / 7.0).toInt()
    return "${t.year}-W${week.toString().padStart(2, '0')}"
}

/** Clé de semaine ISO de l'instant `epochMs`, interprété dans le fuseau local (comme `new Date(ms)`). */
fun isoWeekKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    isoWeekKey(java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate())
