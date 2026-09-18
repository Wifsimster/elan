package ovh.battistella.elan.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.elan.R
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.repository.ListSessionsOptions
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.data.settings.ElanSettings
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.DEFAULT_WEEK_PLAN
import ovh.battistella.elan.domain.DailyDuration
import ovh.battistella.elan.domain.DayBar
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalProgress
import ovh.battistella.elan.domain.PeriodStats
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.computeProgress
import ovh.battistella.elan.domain.dailyDurationBars
import ovh.battistella.elan.domain.isoWeekKey
import ovh.battistella.elan.domain.periodRange
import ovh.battistella.elan.domain.planForDay
import ovh.battistella.elan.domain.startOfWeekMs
import ovh.battistella.elan.sync.AutoProgressionRunner
import ovh.battistella.elan.ui.components.Tone
import ovh.battistella.elan.ui.components.Trend
import ovh.battistella.elan.ui.screens.common.HeartRatePort
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

/** Ce que l'accueil affiche, chargé au focus comme l'écran d'origine. */
data class HomeUi(
    val loaded: Boolean = false,
    /** Instant du chargement (ms epoch) : référence des libellés relatifs. */
    val now: Long = 0L,
    val stats: PeriodStats? = null,
    val lastStats: PeriodStats? = null,
    val bars: List<DayBar> = emptyList(),
    val recent: List<Session> = emptyList(),
    /** Une séance muscu est en pause : « Reprendre » plutôt que « Muscu ». */
    val resumable: Boolean = false,
    /** Séance prévue aujourd'hui par le planning effectif. */
    val today: PlannedSession = PlannedSession.Repos,
    /** `Date.getDay()` JS du jour (0 = dimanche) — pour le nom du jour. */
    val jsDay: Int = 0,
    val goals: List<GoalProgress> = emptyList(),
    /** Bannière de progression auto à afficher (semaine courante, non masquée, avec changements). */
    val planUpdate: AutoProgressionState? = null,
    /** Profil pré-rempli de l'onboarding ; `null` = onboarding déjà fait. */
    val onboarding: Profile? = null,
)

/** Pastille cardio de l'en-tête. */
data class HeartUi(val bpm: Int? = null, val connected: Boolean = false)

/**
 * Tendance d'une métrique par rapport à la semaine précédente : « stable »
 * neutre, sinon `+`/`−` suivi de la différence formatée.
 */
internal fun buildTrend(current: Double, previous: Double, stable: String, fmt: (Double) -> String): Trend {
    val delta = current - previous
    if (delta == 0.0) return Trend(stable, Tone.Neutral)
    val sign = if (delta > 0) "+" else "−"
    return Trend("$sign${fmt(abs(delta))}", if (delta > 0) Tone.Positive else Tone.Negative)
}

/** Noms des jours indexés comme `Date.getDay()` (0 = dimanche). */
internal val WEEKDAYS_FR = listOf("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    heartRate: HeartRatePort,
    private val clock: Clock,
    private val snackbar: SnackbarController,
    @ApplicationContext private val context: Context,
    private val progression: AutoProgressionRunner,
) : ViewModel() {

    // Un tick par (re)prise de l'écran : rejoue les lectures en base, comme le
    // `useFocusEffect` d'origine. Les réglages, eux, arrivent en flux.
    private val refreshTick = MutableStateFlow(0)

    val ui: StateFlow<HomeUi> = combine(settings.settings, refreshTick) { s, _ -> s }
        .mapLatest { s -> load(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    val heart: StateFlow<HeartUi> = combine(heartRate.bpm, heartRate.connected) { bpm, connected ->
        HeartUi(bpm, connected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeartUi())

    /**
     * À chaque (re)prise de l'écran : évaluation hebdomadaire de la progression
     * auto (idempotente, best-effort — son écriture éventuelle rejoue le flux de
     * réglages) puis relecture des séances.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                progression.runWeeklyProgressionIfDue(clock.millis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeViewModel", "progression hebdomadaire ignorée", e)
            }
        }
        refreshTick.update { it + 1 }
    }

    private suspend fun load(s: ElanSettings): HomeUi {
        val now = clock.millis()
        val weekStart = startOfWeekMs(now)
        // Début de la semaine PRÉCÉDENTE par re-floor local (robuste aux
        // changements d'heure), plutôt qu'un `weekStart − 168 h` fixe.
        val prevWeekStart = startOfWeekMs(weekStart - 43_200_000L)
        val stats = sessions.statsSince(weekStart)
        val lastStats = sessions.statsBetween(prevWeekStart, weekStart)
        val daily = sessions.dailyDurations(7, now).map { DailyDuration(it.day, it.durationSec) }
        val recent = sessions.listSessions(ListSessionsOptions(limit = 3))
        val jsDay = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).dayOfWeek.value % 7
        return HomeUi(
            loaded = true,
            now = now,
            stats = stats,
            lastStats = lastStats,
            bars = dailyDurationBars(daily, 7, now),
            recent = recent,
            resumable = s.muscuDraft != null,
            today = planForDay(jsDay, s.weekPlan.takeIf { it.size == 7 } ?: DEFAULT_WEEK_PLAN),
            jsDay = jsDay,
            goals = s.goals.map { computeProgress(it, measureGoal(it, now)) },
            planUpdate = s.autoProgressionState.takeIf { st ->
                st.week == isoWeekKey(now) && !st.dismissed && st.changes.isNotEmpty()
            },
            onboarding = if (s.onboardingDone) null else s.profile,
        )
    }

    /** Valeur réalisée d'un objectif sur sa période courante (`measureGoal` d'origine). */
    private suspend fun measureGoal(goal: Goal, now: Long): Double {
        val range = periodRange(goal.period, now)
        val type = goalActivityType(goal.activity)
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

    private fun goalActivityType(activity: GoalActivity): ActivityType? = when (activity) {
        GoalActivity.ALL -> null
        GoalActivity.VELO -> ActivityType.VELO
        GoalActivity.COURSE -> ActivityType.COURSE
        GoalActivity.MARCHE -> ActivityType.MARCHE
        GoalActivity.MUSCU -> ActivityType.MUSCU
    }

    /** « C'est parti » : fusionne les valeurs saisies avec le profil courant et marque l'onboarding fait. */
    fun finishOnboarding(weightKg: Int, heightCm: Int, maxHr: Int, goal: TrainingGoal) {
        viewModelScope.launch {
            val base = settings.getProfile()
            settings.saveProfile(
                base.copy(
                    weightKg = weightKg.toDouble(),
                    heightCm = heightCm.toDouble(),
                    maxHr = maxHr.toDouble(),
                    goal = goal,
                ),
            )
            settings.setOnboardingDone(true)
        }
    }

    /** Restauration depuis l'onboarding : câblée au jalon M3 (sauvegarde S3). */
    fun requestRestore() {
        snackbar.show(context.getString(R.string.common_soon))
    }

    /** `close` sur la bannière de progression : masquée jusqu'à la prochaine évaluation. */
    fun dismissPlanUpdate() {
        viewModelScope.launch {
            val current = settings.snapshot().autoProgressionState
            settings.setAutoProgressionState(current.copy(dismissed = true))
        }
    }
}
