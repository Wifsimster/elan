// Export des données vers un format lisible par une IA (« coach ») — port de
// `src/lib/coach-export.ts` + `use-data-export.tsx`. Deux artefacts, 100 %
// hors-ligne :
//   - un bilan Markdown : programme, planning, stats, progression et
//     historique, pensé pour être déposé dans un projet Claude Code qui suit
//     la programmation ;
//   - un export JSON brut : profil + programme + instantané complet de la base.
package ovh.battistella.elan.data.export

import android.content.Context
import android.util.JsonWriter
import ovh.battistella.elan.data.backup.BackupSnapshotCodec
import ovh.battistella.elan.data.backup.BackupSnapshotCodec.jsNumber
import ovh.battistella.elan.data.repository.BodyWeightRepository
import ovh.battistella.elan.data.repository.ListSessionsOptions
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SnapshotRepository
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.BodyMeasurement
import ovh.battistella.elan.domain.ExercisePoint
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalProgress
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TEMPLATES
import ovh.battistella.elan.domain.WorkoutTemplate
import ovh.battistella.elan.domain.computeProgress
import ovh.battistella.elan.domain.describeGoal
import ovh.battistella.elan.domain.difficultyLabel
import ovh.battistella.elan.domain.formatCalories
import ovh.battistella.elan.domain.formatDateShort
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.domain.formatDistance
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.domain.formatDurationShort
import ovh.battistella.elan.domain.formatGoalValue
import ovh.battistella.elan.domain.formatHr
import ovh.battistella.elan.domain.formatPace
import ovh.battistella.elan.domain.formatSpeed
import ovh.battistella.elan.domain.goalLabel
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.periodRange
import ovh.battistella.elan.domain.targetHint
import ovh.battistella.elan.domain.toJsString
import ovh.battistella.elan.domain.usesPace
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

private const val DAY = 86_400_000L
private val JOURS_SEMAINE = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Nom de fichier et type MIME du bilan Markdown. */
const val COACH_MARKDOWN_FILE = "suivi-sport-coach.md"
const val COACH_MARKDOWN_MIME = "text/markdown"

/** Nom de fichier et type MIME de l'export JSON brut. */
const val COACH_JSON_FILE = "suivi-sport-export.json"
const val COACH_JSON_MIME = "application/json"

/** Nombre en français, sans décimale superflue (3 -> « 3 », 12.5 -> « 12,5 »). */
internal fun num(n: Double?, digits: Int = 1): String {
    if (n == null) return "—"
    val scale = 10.0.pow(digits)
    val r = Math.round(n * scale) / scale
    val text = if (r == floor(r) && r.isFinite()) r.toJsString()
    else BigDecimal(r).setScale(digits, RoundingMode.HALF_UP).toPlainString()
    return text.replace('.', ',')
}

/** Échappe le caractère pipe pour ne pas casser les tableaux Markdown. */
internal fun cell(s: String): String = s.replace("|", "\\|").replace(Regex("\n+"), " ").trim()

@Singleton
class CoachExporter @Inject constructor(
    private val sessions: SessionRepository,
    private val bodyWeight: BodyWeightRepository,
    private val settings: SettingsRepository,
    private val snapshots: SnapshotRepository,
    private val clock: Clock,
) {
    // ---- points d'entrée (écran Réglages) ---------------------------------

    /** Écrit le bilan Markdown dans le cache partagé et renvoie le fichier. */
    suspend fun exportMarkdown(context: Context): File {
        val file = File(FileShare.shareDir(context), COACH_MARKDOWN_FILE)
        file.writeText(buildMarkdown(), Charsets.UTF_8)
        return file
    }

    /** Écrit l'export JSON brut dans le cache partagé et renvoie le fichier. */
    suspend fun exportJson(context: Context): File {
        val file = File(FileShare.shareDir(context), COACH_JSON_FILE)
        buildJson(file)
        return file
    }

    // ---- Markdown --------------------------------------------------------

    /**
     * Construit le bilan Markdown complet : un seul document auto-suffisant à
     * déposer dans un projet pour qu'une IA suive la programmation et coache.
     */
    suspend fun buildMarkdown(): String {
        val now = clock.millis()
        val s = settings.snapshot()
        val profile = s.profile
        val all = sessions.listSessions(ListSessionsOptions(limit = 10_000))
        val measurements = bodyWeight.listBodyMeasurements()
        val goals = s.goals.map { computeProgress(it, measureGoal(it, now)) }

        val muscuSessions = all.filter { it.type == ActivityType.MUSCU }
        val gpsSessions = all.filter { isGpsActivity(it.type) }

        // Séries par séance muscu (déjà triées de la plus récente à la plus ancienne).
        val muscuDetail = muscuSessions.map { session -> session to sessions.getMuscuSets(session.id) }

        // Progression par exercice : exercices distincts vus dans l'historique
        // muscu, puis leur courbe complète.
        val exerciseNames = ArrayList<String>()
        for ((_, sets) in muscuDetail) {
            for (set in sets) if (set.exercise !in exerciseNames) exerciseNames.add(set.exercise)
        }
        val history = exerciseNames.map { it to sessions.exerciseHistory(it) }

        val header = listOf(
            "# Élan — Export pour ton coach IA",
            "",
            "Généré le ${formatDateTime(now)}. Données issues de l'app Élan (toutes locales).",
            "Charges en kg, durées en h/min, distances en km. Dates en heure locale.",
        ).joinToString("\n")

        return listOfNotNull(
            header,
            profileSection(profile),
            weightSection(measurements),
            programSection(s.weekPlan),
            statsSection(now),
            goalsSection(goals),
            progressionSection(history),
            muscuDetailSection(muscuDetail),
            gpsSection(gpsSessions),
        ).joinToString("\n\n")
    }

    /** Valeur réalisée d'un objectif sur sa période courante (`measureGoal` d'origine). */
    private suspend fun measureGoal(goal: Goal, now: Long): Double {
        val range = periodRange(goal.period, now)
        val type = when (goal.activity) {
            GoalActivity.ALL -> null
            GoalActivity.VELO -> ActivityType.VELO
            GoalActivity.COURSE -> ActivityType.COURSE
            GoalActivity.MARCHE -> ActivityType.MARCHE
            GoalActivity.MUSCU -> ActivityType.MUSCU
        }
        return when (goal.metric) {
            GoalMetric.SESSIONS -> sessions.statsBetween(range.fromMs, range.toMs, type).sessionCount.toDouble()
            // km arrondis au dixième, cohérent avec l'affichage des distances.
            GoalMetric.DISTANCE -> {
                val m = sessions.statsBetween(range.fromMs, range.toMs, type).totalDistanceM
                (m / 1000 * 10).roundToLong() / 10.0
            }
            GoalMetric.TONNAGE -> sessions.tonnageBetween(range.fromMs, range.toMs)
        }
    }

    private fun profileSection(profile: Profile): String {
        val sexLabel = when (profile.sex) {
            Sex.H -> "homme"
            Sex.F -> "femme"
            null -> "non précisé"
        }
        return listOf(
            "## Profil",
            "",
            "- Poids : ${num(profile.weightKg)} kg",
            "- Taille : ${profile.heightCm.toJsString()} cm",
            "- FC max : ${profile.maxHr.toJsString()} bpm",
            "- Objectif : ${goalLabel(profile.goal)}",
            "- Sexe : $sexLabel",
        ).joinToString("\n")
    }

    /** Courbe de poids corporel — section omise tant que le journal est vide. */
    private fun weightSection(measurements: List<BodyMeasurement>): String? {
        if (measurements.isEmpty()) return null
        val lines = mutableListOf(
            "## Poids corporel",
            "",
            "Journal des pesées, du plus ancien au plus récent. La dernière pesée est le poids de référence du profil.",
            "",
            "| Date | Poids (kg) |",
            "| --- | --- |",
        )
        for (m in measurements.asReversed()) {
            lines.add("| ${formatDateShort(m.measuredAt)} | ${num(m.weightKg)} |")
        }
        return lines.joinToString("\n")
    }

    private fun programSection(weekPlan: List<PlannedSession>): String {
        val lines = mutableListOf("## Mon programme")
        lines.add("")
        lines.add(
            "Programme « Maison (haltères) » orienté prise de muscle : deux séances full-body par semaine, la charge est le moteur de la progression (surcharge progressive).",
        )
        lines.add("")
        lines.add("### Planning hebdomadaire")
        lines.add("")
        for ((i, p) in weekPlan.withIndex()) {
            val label = when (p) {
                PlannedSession.Repos -> "Repos"
                is PlannedSession.Outing -> "${ActivityType.fromKey(p.kind)?.meta?.label ?: p.kind} — ${p.label}"
                is PlannedSession.Muscu -> "${ActivityType.MUSCU.meta.label} — ${p.label}"
            }
            lines.add("- ${JOURS_SEMAINE.getOrElse(i) { "Jour ${i + 1}" }} : $label")
        }

        for (t in TEMPLATES) {
            lines.add("")
            lines.add("### ${t.name} (${t.day})")
            lines.add("")
            for ((i, ex) in t.exercises.withIndex()) {
                val charge = if (ex.startWeightKg > 0) "${num(ex.startWeightKg)} kg" else "poids du corps"
                lines.add("${i + 1}. **${ex.name}** — ${targetHint(ex)}, charge de départ $charge")
                lines.add("   _Muscles :_ ${ex.muscles.joinToString(", ")}")
                lines.add("   _Exécution :_ ${ex.howTo}")
            }
        }
        return lines.joinToString("\n")
    }

    private suspend fun statsSection(now: Long): String {
        val periods = listOf(
            "7 derniers jours" to now - 7 * DAY,
            "30 derniers jours" to now - 30 * DAY,
            "90 derniers jours" to now - 90 * DAY,
            "Depuis le début" to 0L,
        )
        val rows = periods.map { (label, since) ->
            val st = sessions.statsSince(since)
            "| $label | ${st.sessionCount} | ${formatDurationShort(st.totalDurationSec)} | " +
                "${formatDistance(st.totalDistanceM)} | ${formatCalories(st.totalCalories)} |"
        }
        return (
            listOf(
                "## Statistiques",
                "",
                "| Période | Séances | Durée totale | Distance (vélo) | Calories |",
                "| --- | --- | --- | --- | --- |",
            ) + rows
            ).joinToString("\n")
    }

    /** Objectifs et leur avancement sur la période courante — omis si aucun défini. */
    private fun goalsSection(progress: List<GoalProgress>): String? {
        if (progress.isEmpty()) return null
        val lines = mutableListOf(
            "## Objectifs",
            "",
            "Avancement sur la période courante (semaine ou mois selon l’objectif).",
            "",
            "| Objectif | Réalisé | Cible | Atteint |",
            "| --- | --- | --- | --- |",
        )
        for (p in progress) {
            val done = if (p.done) "oui" else "${Math.round(p.ratio * 100)} %"
            lines.add(
                "| ${cell(describeGoal(p.goal))} | ${formatGoalValue(p.goal, p.value)} | " +
                    "${formatGoalValue(p.goal, p.target)} | $done |",
            )
        }
        return lines.joinToString("\n")
    }

    private fun progressionSection(history: List<Pair<String, List<ExercisePoint>>>): String {
        if (history.isEmpty()) {
            return "## Progression par exercice (musculation)\n\n_Aucune séance de musculation enregistrée._"
        }
        val lines = mutableListOf(
            "## Progression par exercice (musculation)",
            "",
            "Une ligne par séance, du plus ancien au plus récent. « Charge max » = série la plus lourde de la séance ; « Reps » = répétitions de cette série ; « Volume » = somme de (reps × charge) sur toutes les séries ; « Ressenti » = effort noté par l'utilisateur (facile / moyen / dur) pour décider d'augmenter ou non les reps et la charge.",
        )
        for ((exercise, points) in history) {
            lines.add("")
            lines.add("### $exercise")
            lines.add("")
            lines.add("| Date | Charge max (kg) | Reps | Volume | Séries | Ressenti |")
            lines.add("| --- | --- | --- | --- | --- | --- |")
            for (p in points) {
                val ressenti = p.difficulty?.let { difficultyLabel(it) } ?: "—"
                lines.add(
                    "| ${formatDateShort(p.startedAt)} | ${num(p.maxWeightKg)} | ${p.topReps} | ${num(p.volume)} | ${p.sets} | $ressenti |",
                )
            }
        }
        return lines.joinToString("\n")
    }

    private fun muscuDetailSection(sessionsWithSets: List<Pair<Session, List<MuscuSet>>>): String {
        if (sessionsWithSets.isEmpty()) {
            return "## Détail des séances de musculation\n\n_Aucune séance de musculation enregistrée._"
        }
        val lines = mutableListOf(
            "## Détail des séances de musculation",
            "",
            "Toutes les séries enregistrées, de la plus récente à la plus ancienne.",
        )
        for ((session, sets) in sessionsWithSets) {
            lines.add("")
            lines.add("### ${formatDateTime(session.startedAt)} — durée ${formatDuration(session.durationSec)}")
            if (session.avgHr != null) lines.add("FC moyenne ${formatHr(session.avgHr)} · max ${formatHr(session.maxHr)}.")
            if (!session.notes.isNullOrEmpty()) lines.add("Notes : ${session.notes}")
            lines.add("")
            lines.add("| Exercice | Série | Reps | Charge (kg) |")
            lines.add("| --- | --- | --- | --- |")
            for ((exercise, group) in groupByExercise(sets)) {
                for ((i, set) in group.withIndex()) {
                    lines.add("| ${cell(exercise)} | ${i + 1} | ${set.reps} | ${num(set.weightKg)} |")
                }
            }
        }
        return lines.joinToString("\n")
    }

    /** Regroupe les séries d'une séance par exercice, dans l'ordre d'apparition. */
    private fun groupByExercise(sets: List<MuscuSet>): List<Pair<String, List<MuscuSet>>> {
        val byEx = LinkedHashMap<String, MutableList<MuscuSet>>()
        for (s in sets) byEx.getOrPut(s.exercise) { ArrayList() }.add(s)
        return byEx.map { (k, v) -> k to v }
    }

    /**
     * Historique des sorties tracées au GPS — vélo, course et marche réunis.
     * Une colonne « Activité » les distingue, et l'allure remplace la vitesse
     * pour les activités à pied, où c'est l'unité qui parle.
     */
    private fun gpsSection(gps: List<Session>): String {
        if (gps.isEmpty()) return "## Historique des sorties\n\n_Aucune sortie enregistrée._"
        val lines = mutableListOf(
            "## Historique des sorties",
            "",
            "| Date | Activité | Durée | Distance | Moy. | Max | FC moy | D+ | Calories | Source |",
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
        )
        for (s in gps) {
            val dplus = s.elevationGainM?.let { "${Math.round(it)} m" } ?: "—"
            val speed: (Double?) -> String = if (usesPace(s.type)) ::formatPace else ::formatSpeed
            lines.add(
                "| ${formatDateTime(s.startedAt)} | ${s.type.meta.label} | ${formatDuration(s.movingTimeSec ?: s.durationSec)} | " +
                    "${formatDistance(s.distanceM)} | ${speed(s.avgSpeedKmh)} | ${speed(s.maxSpeedKmh)} | ${formatHr(s.avgHr)} | " +
                    "$dplus | ${formatCalories(s.calories)} | ${s.source ?: "app"} |",
            )
        }
        return lines.joinToString("\n")
    }

    // ---- JSON ------------------------------------------------------------

    /**
     * Export JSON brut, écrit en flux : profil + programme + instantané complet
     * de la base (séances, points GPS, séries, réglages non secrets).
     */
    suspend fun buildJson(file: File) {
        val s = settings.snapshot()
        val data = snapshots.exportAll()
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { out ->
            val w = JsonWriter(out.writer(Charsets.UTF_8))
            w.setIndent("  ")
            w.beginObject()
            w.name("format").value(1L)
            w.name("app").value("suivi-sport")
            w.name("exportedAt").value(clock.millis())
            w.name("profile")
            writeProfile(w, s.profile)
            w.name("program").beginObject()
            w.name("templates").beginArray()
            for (t in TEMPLATES) writeTemplate(w, t)
            w.endArray()
            w.name("weekPlan").beginArray()
            for (p in s.weekPlan) writePlanned(w, p)
            w.endArray()
            w.endObject()
            w.name("data")
            BackupSnapshotCodec.writeSnapshot(w, data)
            w.endObject()
            w.flush()
        }
    }

    private fun writeProfile(w: JsonWriter, p: Profile) {
        w.beginObject()
        w.name("weightKg").jsNumber(p.weightKg)
        w.name("heightCm").jsNumber(p.heightCm)
        w.name("maxHr").jsNumber(p.maxHr)
        w.name("goal").value(p.goal.key)
        w.name("sex").value(p.sex?.key)
        w.endObject()
    }

    private fun writeTemplate(w: JsonWriter, t: WorkoutTemplate) {
        w.beginObject()
        w.name("id").value(t.id.key)
        w.name("name").value(t.name)
        w.name("day").value(t.day)
        w.name("exercises").beginArray()
        for (ex in t.exercises) {
            w.beginObject()
            w.name("name").value(ex.name)
            w.name("sets").value(ex.sets.toLong())
            w.name("repsMin").value(ex.repsMin.toLong())
            w.name("repsMax").value(ex.repsMax.toLong())
            w.name("startWeightKg").jsNumber(ex.startWeightKg)
            w.name("howTo").value(ex.howTo)
            w.name("muscles").beginArray()
            for (m in ex.muscles) w.value(m)
            w.endArray()
            w.name("icon").value(ex.icon)
            if (ex.imageKey != null) w.name("imageKey").value(ex.imageKey)
            if (ex.perSideLabel != null) w.name("perSideLabel").value(ex.perSideLabel)
            if (ex.timed) w.name("timed").value(true)
            if (ex.autoProgress != null) w.name("autoProgress").value(ex.autoProgress.key)
            w.endObject()
        }
        w.endArray()
        w.endObject()
    }

    private fun writePlanned(w: JsonWriter, p: PlannedSession) {
        w.beginObject()
        w.name("kind").value(p.kind)
        when (p) {
            is PlannedSession.Outing -> w.name("label").value(p.label)
            is PlannedSession.Muscu -> {
                w.name("label").value(p.label)
                w.name("templateId").value(p.templateId.key)
            }
            PlannedSession.Repos -> Unit
        }
        w.endObject()
    }
}
