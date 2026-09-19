// Orchestration IMPURE de la progression automatique (port de la partie
// « impure » de `lib/auto-progression.ts`) : lecture de l'historique et de la
// config, persistance de l'état hebdomadaire, notification. La logique pure
// (cibles, pas, plafonds, textes) vit dans `domain/AutoProgression.kt`.
package ovh.battistella.elan.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.ExerciseTarget
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.TemplateExercise
import ovh.battistella.elan.domain.isoWeekKey
import ovh.battistella.elan.domain.notificationContent
import ovh.battistella.elan.domain.targetForExercise
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.domain.PlannedSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoProgressionRunner internal constructor(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val notifier: ProgressionNotify,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        sessions: SessionRepository,
    ) : this(settings, sessions, ProgressionNotify { title, body -> ProgressionNotifier.notify(context, title, body) })

    /**
     * Cibles de pré-remplissage des exercices d'un programme (clé = nom) :
     * dernière charge/durée enregistrée, éventuellement relevée d'un cran par
     * la progression auto. L'unique endroit où la montée est appliquée.
     */
    suspend fun targetsForExercises(exercises: List<TemplateExercise>): Map<String, ExerciseTarget> {
        val enabled = settings.snapshot().autoProgression.enabled
        val out = LinkedHashMap<String, ExerciseTarget>(exercises.size)
        for (ex in exercises) {
            out[ex.name] = targetForExercise(ex, sessions.exerciseHistory(ex.name), enabled)
        }
        return out
    }

    /**
     * Exercices auto-progressables du planning effectif, dédupliqués par nom
     * (un même mouvement présent plusieurs jours ne compte qu'une fois).
     */
    private suspend fun planAutoExercises(): List<TemplateExercise> {
        val plan = settings.snapshot().weekPlan
        val seen = HashSet<String>()
        val out = ArrayList<TemplateExercise>()
        for (entry in plan) {
            if (entry !is PlannedSession.Muscu) continue
            for (ex in templateById(entry.templateId).exercises) {
                if (ex.autoProgress != null && seen.add(ex.name)) out.add(ex)
            }
        }
        return out
    }

    /**
     * Changements de la nouvelle semaine : pour chaque exercice auto du
     * planning, compare la dernière valeur enregistrée à la cible. Ne renvoie
     * que les exercices qui bougent réellement.
     */
    suspend fun computeWeeklyChanges(): List<ProgressionChange> {
        val changes = ArrayList<ProgressionChange>()
        for (ex in planAutoExercises()) {
            val history = sessions.exerciseHistory(ex.name)
            val target = targetForExercise(ex, history, enabled = true)
            val kind = target.bumpKind
            if (target.bump == 0.0 || kind == null) continue
            val last = history.last()
            val isLoad = kind == ProgressKind.LOAD
            changes.add(
                ProgressionChange(
                    exercise = ex.name,
                    kind = kind,
                    from = if (isLoad) last.maxWeightKg else last.topReps.toDouble(),
                    to = if (isLoad) target.weightKg else target.reps.toDouble(),
                    direction = if (target.bump > 0) Direction.UP else Direction.DOWN,
                ),
            )
        }
        return changes
    }

    /**
     * Évalue la progression si une nouvelle semaine ISO a commencé (idempotent).
     * À appeler au lancement et au retour sur l'accueil. Renvoie les changements
     * de la semaine (vide si rien, désactivé, ou déjà évalué).
     *
     * Sécurités : l'état est persisté AVANT la notification (un échec de notif
     * ne rejoue pas l'annonce) ; premier passage silencieux (amorçage) pour
     * qu'activer la fonctionnalité ne déclenche pas une fausse annonce.
     */
    suspend fun runWeeklyProgressionIfDue(now: Long): List<ProgressionChange> {
        val snapshot = settings.snapshot()
        if (!snapshot.autoProgression.enabled) return emptyList()

        val week = isoWeekKey(now)
        val state = snapshot.autoProgressionState
        if (state.week == week) return state.changes // déjà évalué cette semaine

        if (state.week.isEmpty()) {
            // Amorçage silencieux : on mémorise la semaine sans annoncer.
            settings.setAutoProgressionState(AutoProgressionState(week = week, changes = emptyList(), dismissed = true))
            return emptyList()
        }

        val changes = computeWeeklyChanges()
        settings.setAutoProgressionState(AutoProgressionState(week = week, changes = changes, dismissed = changes.isEmpty()))
        if (changes.isNotEmpty()) {
            val content = notificationContent(changes)
            try {
                notifier.notify(content.title, content.body)
            } catch (_: Exception) {
                // Notification best-effort : l'état est déjà persisté.
            }
        }
        return changes
    }
}
