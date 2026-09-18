package ovh.battistella.elan.ui.screens.session

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.elan.R
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.repository.RecordScope
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.canRetype
import ovh.battistella.elan.domain.isGpsActivity
import javax.inject.Inject

/** Dialogue ouvert sur le détail de séance. */
sealed interface SessionDialog {
    data object None : SessionDialog
    data object Delete : SessionDialog

    /** Confirmation de changement de type vers [to]. */
    data class Retype(val to: ActivityType) : SessionDialog
}

data class SessionDetailUi(
    val loading: Boolean = true,
    /** `null` une fois chargé = séance introuvable. */
    val session: Session? = null,
    val points: List<TrackPoint> = emptyList(),
    val sets: List<MuscuSet> = emptyList(),
    val records: List<SessionRecord> = emptyList(),
    /** FC max du profil (zones, effort). */
    val maxHr: Double = 0.0,
    val dialog: SessionDialog = SessionDialog.None,
    /** Changement de type en cours d'écriture. */
    val busy: Boolean = false,
    /** Message d'échec du changement de type (alerte « Changement impossible »). */
    val retypeError: Boolean = false,
)

/** Événements ponctuels (navigation, haptique). */
sealed interface SessionEvent {
    data object Deleted : SessionEvent
    data object RetypeSucceeded : SessionEvent
    data object RetypeFailed : SessionEvent
}

/** Un exercice d'une séance muscu et ses séries, dans l'ordre d'apparition. */
data class ExerciseGroup(val name: String, val rows: List<MuscuSet>) {
    val volume: Double get() = rows.sumOf { it.reps * it.weightKg }

    /** Ressenti uniforme sur les séries de l'exercice : on lit la 1re ligne. */
    val difficulty: Difficulty? get() = rows.firstOrNull()?.difficulty
}

/** Argument `id` de la route (`Long` navigué, ou texte en test) ; -1 si absent. */
internal fun sessionIdArg(handle: SavedStateHandle): Long = when (val raw = handle.get<Any>("id")) {
    is Long -> raw
    is Int -> raw.toLong()
    is String -> raw.toLongOrNull() ?: -1L
    else -> -1L
}

/** Regroupe les séries par exercice en conservant l'ordre d'apparition. */
internal fun groupSets(sets: List<MuscuSet>): List<ExerciseGroup> =
    sets.groupBy { it.exercise }.map { (name, rows) -> ExerciseGroup(name, rows) }

/**
 * Records à mettre en avant, façon « PR » Strava : les records absolus
 * d'abord, puis ceux de l'année, trois au plus pour éviter la surcharge.
 */
internal fun topRecords(records: List<SessionRecord>): List<SessionRecord> =
    records.sortedBy { if (it.scope == RecordScope.ALL) 0 else 1 }.take(3)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val snackbar: SnackbarController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val sessionId: Long = sessionIdArg(savedStateHandle)

    private val _ui = MutableStateFlow(SessionDetailUi())
    val ui: StateFlow<SessionDetailUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    // Garde de réentrance armée dès la CONFIRMATION de retype : un double appui
    // n'ouvre pas deux dialogues.
    private var retypePending = false

    init {
        load()
    }

    /** (Re)lit la séance, ses points / séries, ses records et la FC max du profil. */
    fun load() {
        viewModelScope.launch {
            val s = sessions.getSession(sessionId)
            val points = if (s != null && isGpsActivity(s.type)) sessions.getTrackPoints(sessionId) else emptyList()
            val sets = if (s?.type == ActivityType.MUSCU) sessions.getMuscuSets(sessionId) else emptyList()
            val records = if (s != null) sessions.sessionRecords(s) else emptyList()
            val maxHr = if (s != null) settings.getProfile().maxHr else 0.0
            _ui.update {
                it.copy(loading = false, session = s, points = points, sets = sets, records = records, maxHr = maxHr)
            }
        }
    }

    fun requestDelete() = _ui.update { it.copy(dialog = SessionDialog.Delete) }

    fun confirmDelete() {
        _ui.update { it.copy(dialog = SessionDialog.None) }
        viewModelScope.launch {
            sessions.deleteSession(sessionId)
            _events.emit(SessionEvent.Deleted)
        }
    }

    /** Ouvre la confirmation de changement de type — refusée hors activités GPS ou vers le type courant. */
    fun requestRetype(to: ActivityType) {
        val current = _ui.value.session ?: return
        if (retypePending || !canRetype(current.type, to)) return
        retypePending = true
        _ui.update { it.copy(dialog = SessionDialog.Retype(to)) }
    }

    fun confirmRetype() {
        val dialog = _ui.value.dialog as? SessionDialog.Retype ?: return
        _ui.update { it.copy(dialog = SessionDialog.None, busy = true) }
        viewModelScope.launch {
            val updated = runCatching { sessions.changeSessionType(sessionId, dialog.to) }.getOrNull()
            retypePending = false
            if (updated == null) {
                _ui.update { it.copy(busy = false, retypeError = true) }
                _events.emit(SessionEvent.RetypeFailed)
                return@launch
            }
            _ui.update { it.copy(busy = false) }
            _events.emit(SessionEvent.RetypeSucceeded)
            // Les records se comparent par type : on relit tout.
            load()
        }
    }

    fun dismissDialog() {
        retypePending = false
        _ui.update { it.copy(dialog = SessionDialog.None) }
    }

    fun dismissRetypeError() = _ui.update { it.copy(retypeError = false) }

    /** Export GPX : câblé au jalon M2 (fichiers). */
    fun exportGpx() = snackbar.show(context.getString(R.string.common_soon))

    /** Partage d'image : câblé au jalon M2 (capture + partage). */
    fun share() = snackbar.show(context.getString(R.string.common_soon))
}
