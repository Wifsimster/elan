package ovh.battistella.elan.ui.screens.strength

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.data.repository.MuscuSetInput
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.CatalogExercise
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.HrSample
import ovh.battistella.elan.domain.MuscuDraft
import ovh.battistella.elan.domain.MuscuStats
import ovh.battistella.elan.domain.RecoProfile
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.WorkoutTemplate
import ovh.battistella.elan.domain.catalogById
import ovh.battistella.elan.domain.estimateCalories
import ovh.battistella.elan.domain.goalSpec
import ovh.battistella.elan.domain.muscuStats
import ovh.battistella.elan.domain.muscuSummary
import ovh.battistella.elan.domain.pushDownsampled
import ovh.battistella.elan.domain.summarizeHr
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.domain.toRecoProfile
import ovh.battistella.elan.sync.AutoProgressionRunner
import ovh.battistella.elan.tracking.LiveKind
import ovh.battistella.elan.tracking.LiveNotification
import ovh.battistella.elan.tracking.SavedSessionData
import ovh.battistella.elan.tracking.SessionFinalizer
import ovh.battistella.elan.tracking.Stopwatch
import ovh.battistella.elan.ui.screens.common.HeartRatePort
import java.time.Clock
import javax.inject.Inject

sealed interface StrengthDialog {
    data object None : StrengthDialog

    data object Finish : StrengthDialog

    data object EmptyFinish : StrengthDialog

    data object Exit : StrengthDialog

    data object SaveFailed : StrengthDialog

    data class RemoveExercise(val id: String, val name: String) : StrengthDialog

    data class RemoveSet(val id: String, val index: Int) : StrengthDialog
}

sealed interface StrengthEvent {
    data class Saved(val sessionId: Long) : StrengthEvent

    data object Exit : StrengthEvent

    data object RequestNotificationPermission : StrengthEvent
}

data class StrengthUi(
    val hydrated: Boolean = false,
    val exercises: List<StrengthExercise> = emptyList(),
    val paused: Boolean = false,
    val saving: Boolean = false,
    val elapsedSec: Int = 0,
    val bpm: Int? = null,
    val restEndsAt: Long? = null,
    val infoExercise: StrengthExercise? = null,
    val catalogOpen: Boolean = false,
    val recoProfile: RecoProfile = RecoProfile(70.0, 175.0, null, TrainingGoal.HYPERTROPHIE),
    val dialog: StrengthDialog = StrengthDialog.None,
) {
    val stats: MuscuStats get() = muscuStats(exercises.map { it.toStats() })
    val addedNames: Set<String> get() = exercises.mapTo(HashSet()) { it.name }
}

@HiltViewModel
class StrengthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val progression: AutoProgressionRunner,
    private val finalizer: SessionFinalizer,
    heartRate: HeartRatePort,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val templateArg: String? = savedStateHandle.get<String>("template")
    private val addArg: String? = savedStateHandle.get<String>("add")

    private val _ui = MutableStateFlow(StrengthUi())
    val watch = Stopwatch(clock, viewModelScope)

    val ui: StateFlow<StrengthUi> = combine(_ui, watch.elapsedSec, heartRate.bpm) { u, elapsed, bpm ->
        u.copy(elapsedSec = elapsed, bpm = bpm)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StrengthUi())

    private val _events = MutableSharedFlow<StrengthEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StrengthEvent> = _events.asSharedFlow()

    private var startedAt: Long = 0L
    private var weightKg = 70.0
    private var maxHr = 190.0

    private var restDurationSec = 90

    private var savedId: Long? = null

    private val hrSamples = ArrayList<HrSample>()
    private val draftMutex = Mutex()

    init {
        viewModelScope.launch { hydrate() }
        viewModelScope.launch {
            heartRate.bpm.collect { bpm ->
                if (bpm == null || _ui.value.paused || !_ui.value.hydrated) return@collect
                pushDownsampled(hrSamples, HrSample(clock.millis(), bpm.toDouble()), { it.hr })
            }
        }
    }

    private suspend fun hydrate() {
        val profile = settings.getProfile()
        weightKg = profile.weightKg
        maxHr = profile.maxHr
        _ui.update { it.copy(recoProfile = profile.toRecoProfile()) }

        val saved = settings.getSetting(SettingsRepository.Keys.REST_SECONDS)?.toDoubleOrNull()
        restDurationSec = if (saved != null && saved.isFinite() && saved > 0) saved.toInt().coerceIn(15, 600) else goalSpec(profile.goal).restSec

        val draft = settings.snapshot().muscuDraft
        if (draft != null) {
            startedAt = draft.startedAt
            hrSamples.clear()
            hrSamples.addAll(draft.hrSamples)
            watch.seed(draft.elapsedSec.toInt())
            _ui.update { it.copy(exercises = exercisesFromJson(draft.exercises), paused = true) }
        } else {
            startedAt = clock.millis()
            watch.start()
            _events.tryEmit(StrengthEvent.RequestNotificationPermission)
            LiveNotification.showLive(context, LiveKind.MUSCU)
            templateById(templateArg)?.let { loadTemplate(it) }
        }

        catalogById(addArg)?.let { cat ->
            val last = sessions.lastWeightByExercise(listOf(cat.name))[cat.name]
            _ui.update { it.copy(exercises = it.exercises + exerciseFromCatalog(cat, it.recoProfile, last)) }
        }
        _ui.update { it.copy(hydrated = true) }
    }

    fun loadTemplate(template: WorkoutTemplate) {
        viewModelScope.launch {
            val targets = progression.targetsForExercises(template.exercises)
            mutate { u -> u.copy(exercises = template.exercises.map { ex -> exerciseFromTemplate(ex, targets.getValue(ex.name)) }) }
        }
    }

    fun onNotificationPermission() = LiveNotification.showLive(context, LiveKind.MUSCU)

    fun pause() {
        watch.pause()
        mutate { it.copy(paused = true) }
    }

    fun resume() {
        watch.start()
        mutate { it.copy(paused = false) }
        LiveNotification.showLive(context, LiveKind.MUSCU)
    }

    fun addExercise(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { it.copy(exercises = it.exercises + exerciseFromName(trimmed)) }
    }

    fun addCatalogExercise(ex: CatalogExercise) {
        viewModelScope.launch {
            val last = sessions.lastWeightByExercise(listOf(ex.name))[ex.name]
            mutate { it.copy(exercises = it.exercises + exerciseFromCatalog(ex, it.recoProfile, last)) }
        }
    }

    fun requestRemoveExercise(id: String) {
        val ex = _ui.value.exercises.firstOrNull { it.id == id } ?: return
        _ui.update { it.copy(dialog = StrengthDialog.RemoveExercise(id, ex.name)) }
    }

    fun confirmRemoveExercise(id: String) {
        mutate { u -> u.copy(dialog = StrengthDialog.None, exercises = u.exercises.filter { it.id != id }) }
    }

    fun addSet(id: String) = mutate { u ->
        u.copy(exercises = u.exercises.map { e ->
            if (e.id != id) e else e.copy(sets = e.sets + (e.sets.lastOrNull()?.copy(done = false) ?: DEFAULT_SET))
        })
    }

    fun requestRemoveSet(id: String, index: Int) {
        val done = _ui.value.exercises.firstOrNull { it.id == id }?.sets?.getOrNull(index)?.done ?: return
        if (done) _ui.update { it.copy(dialog = StrengthDialog.RemoveSet(id, index)) } else dropSet(id, index)
    }

    fun confirmRemoveSet(id: String, index: Int) {
        _ui.update { it.copy(dialog = StrengthDialog.None) }
        dropSet(id, index)
    }

    private fun dropSet(id: String, index: Int) = mutate { u ->
        u.copy(exercises = u.exercises.map { e ->
            if (e.id != id) e else e.copy(sets = e.sets.filterIndexed { i, _ -> i != index })
        })
    }

    fun setReps(id: String, index: Int, reps: Int) = updateSet(id, index) { it.copy(reps = reps) }

    fun setWeight(id: String, index: Int, weightKg: Double) = updateSet(id, index) { it.copy(weightKg = weightKg) }

    private fun updateSet(id: String, index: Int, patch: (SetRow) -> SetRow) = mutate { u ->
        u.copy(exercises = u.exercises.map { e ->
            if (e.id != id) e else e.copy(sets = e.sets.mapIndexed { i, s -> if (i == index) patch(s) else s })
        })
    }

    fun toggleSet(id: String, index: Int) {
        val wasDone = _ui.value.exercises.firstOrNull { it.id == id }?.sets?.getOrNull(index)?.done ?: return
        updateSet(id, index) { it.copy(done = !it.done) }
        if (!wasDone) _ui.update { it.copy(restEndsAt = clock.millis() + restDurationSec * 1_000L) }
    }

    fun setDifficulty(id: String, value: Difficulty) = mutate { u ->
        u.copy(exercises = u.exercises.map { e ->
            if (e.id != id) e else e.copy(difficulty = if (e.difficulty == value) null else value)
        })
    }

    fun showInfo(exercise: StrengthExercise?) = _ui.update { it.copy(infoExercise = exercise) }

    fun setCatalogOpen(open: Boolean) = _ui.update { it.copy(catalogOpen = open) }

    fun onRestChange(endsAt: Long?) = _ui.update { it.copy(restEndsAt = endsAt) }

    fun adjustRestPreference(deltaSec: Int) {
        restDurationSec = (restDurationSec + deltaSec).coerceIn(15, 600)
        val secs = restDurationSec
        viewModelScope.launch { settings.setRestSeconds(secs) }
    }

    val restDuration: Int get() = restDurationSec

    fun nowMs(): Long = clock.millis()

    private fun mutate(block: (StrengthUi) -> StrengthUi) {
        _ui.update(block)
        persistDraft()
    }

    fun onStop() = persistDraft()

    private fun persistDraft() {
        if (!_ui.value.hydrated || _ui.value.saving) return
        viewModelScope.launch {
            try {
                draftMutex.withLock {
                    val u = _ui.value
                    if (u.stats.totalSets == 0) settings.setMuscuDraft(null) else settings.setMuscuDraft(currentDraft(u))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "brouillon muscu non écrit", e)
            }
        }
    }

    private fun currentDraft(u: StrengthUi): MuscuDraft = MuscuDraft(
        startedAt = startedAt,
        elapsedSec = watch.getElapsedSec().toDouble(),
        exercises = exercisesToJson(u.exercises),
        hrSamples = hrSamples.toList(),
    )

    fun requestFinish() {
        val u = _ui.value
        if (u.saving) return
        _ui.update { it.copy(dialog = if (u.stats.totalSets == 0) StrengthDialog.EmptyFinish else StrengthDialog.Finish) }
    }

    fun confirmFinish() {
        _ui.update { it.copy(dialog = StrengthDialog.None) }
        save()
    }

    private fun save() {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true) }
        watch.pause()
        viewModelScope.launch {
            val durationSec = watch.getElapsedSec()
            val samples = hrSamples.toList()
            val hr = summarizeHr(samples)
            val calories = estimateCalories(
                type = ActivityType.MUSCU,
                weightKg = weightKg,
                durationSec = durationSec,
                avgHr = hr.avgHr,
                maxHr = maxHr,
            )
            val u = _ui.value
            val endedAt = clock.millis()
            val flat = u.exercises.flatMap { e ->
                e.sets.mapIndexed { i, s ->
                    MuscuSetInput(exercise = e.name, setIndex = i + 1, reps = s.reps, weightKg = s.weightKg, difficulty = e.difficulty)
                }
            }
            try {
                val id = sessions.saveMuscuSession(
                    id = savedId,
                    startedAt = startedAt,
                    patch = SessionUpdate {
                        this.endedAt = endedAt
                        this.durationSec = durationSec
                        this.avgHr = hr.avgHr
                        this.maxHr = hr.maxHr
                        this.calories = calories
                        this.notes = muscuSummary(u.stats)
                    },
                    sets = flat,
                )
                savedId = id
                draftMutex.withLock { settings.setMuscuDraft(null) }
                finalizer.onSaved(
                    SavedSessionData(
                        type = ActivityType.MUSCU,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        calories = calories,
                        hrSamples = samples,
                    ),
                )
                LiveNotification.clear(context)
                _events.emit(StrengthEvent.Saved(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "enregistrement de la séance muscu échoué", e)
                _ui.update { it.copy(saving = false, paused = true, dialog = StrengthDialog.SaveFailed) }
            }
        }
    }

    fun requestExit() {
        val u = _ui.value
        if (u.saving) return
        if (u.stats.totalSets == 0) {
            viewModelScope.launch {
                draftMutex.withLock { settings.setMuscuDraft(null) }
                LiveNotification.clear(context)
                _events.emit(StrengthEvent.Exit)
            }
            return
        }
        _ui.update { it.copy(dialog = StrengthDialog.Exit) }
    }

    fun pauseAndExit() {
        watch.pause()
        _ui.update { it.copy(dialog = StrengthDialog.None, paused = true) }
        viewModelScope.launch {
            draftMutex.withLock { settings.setMuscuDraft(currentDraft(_ui.value)) }
            _events.emit(StrengthEvent.Exit)
        }
    }

    fun abandon() {
        _ui.update { it.copy(dialog = StrengthDialog.None, exercises = emptyList()) }
        viewModelScope.launch {
            draftMutex.withLock { settings.setMuscuDraft(null) }
            LiveNotification.clear(context)
            _events.emit(StrengthEvent.Exit)
        }
    }

    fun dismissDialog() = _ui.update { it.copy(dialog = StrengthDialog.None) }

    private companion object {
        const val TAG = "StrengthViewModel"
    }
}
