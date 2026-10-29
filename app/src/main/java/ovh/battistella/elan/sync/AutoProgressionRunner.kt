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

    suspend fun targetsForExercises(exercises: List<TemplateExercise>): Map<String, ExerciseTarget> {
        val enabled = settings.snapshot().autoProgression.enabled
        val out = LinkedHashMap<String, ExerciseTarget>(exercises.size)
        for (ex in exercises) {
            out[ex.name] = targetForExercise(ex, sessions.exerciseHistory(ex.name), enabled)
        }
        return out
    }

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

    suspend fun runWeeklyProgressionIfDue(now: Long): List<ProgressionChange> {
        val snapshot = settings.snapshot()
        if (!snapshot.autoProgression.enabled) return emptyList()

        val week = isoWeekKey(now)
        val state = snapshot.autoProgressionState
        if (state.week == week) return state.changes

        if (state.week.isEmpty()) {
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
            }
        }
        return changes
    }
}
