package ovh.battistella.elan.ui.screens.session

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.battistella.elan.R
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.repository.RecordScope
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.HrSample
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.canRetype
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.ui.screens.settings.ExportPort
import java.io.File
import ovh.battistella.elan.tracking.SavedSessionData
import ovh.battistella.elan.tracking.SessionFinalizer
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
    /** Export GPX en cours (anti-double-appui). */
    val exporting: Boolean = false,
    /** Aperçu de la carte de partage ouvert. */
    val sharePreview: Boolean = false,
    /** Capture + partage de l'image en cours. */
    val sharing: Boolean = false,
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
    private val finalizer: SessionFinalizer,
    private val export: ExportPort,
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
            val previousType = _ui.value.session?.type
            val updated = runCatching { sessions.changeSessionType(sessionId, dialog.to) }.getOrNull()
            retypePending = false
            if (updated == null) {
                _ui.update { it.copy(busy = false, retypeError = true) }
                _events.emit(SessionEvent.RetypeFailed)
                return@launch
            }
            // Miroirs externes (`retypeSavedSession`) : sauvegarde, puis Health
            // Connect — l'ancien type est retiré avant réécriture.
            if (previousType != null && updated.endedAt != null) {
                finalizer.onRetyped(
                    previousType,
                    SavedSessionData(
                        type = updated.type,
                        startedAt = updated.startedAt,
                        endedAt = updated.endedAt,
                        distanceM = updated.distanceM,
                        calories = updated.calories,
                        hrSamples = _ui.value.points.mapNotNull { p -> p.hr?.let { HrSample(p.ts, it) } },
                    ),
                )
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

    /**
     * Export GPX (format Strava) : fichier dans le cache, zone de
     * confidentialité appliquée par l'exporteur, puis feuille de partage. Sans
     * tracé exploitable (< 2 points), rien n'est écrit.
     */
    fun exportGpx() {
        if (_ui.value.exporting) return
        if (_ui.value.points.size < 2) {
            snackbar.show(context.getString(R.string.session_gpx_no_track))
            return
        }
        _ui.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val privacyZoneM = settings.snapshot().privacyZoneM
                val file = export.exportGpx(context, sessionId, privacyZoneM)
                if (file == null) {
                    snackbar.show(context.getString(R.string.session_gpx_no_track))
                } else {
                    export.share(context, file, "application/gpx+xml", context.getString(R.string.session_export_gpx))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(e.message ?: context.getString(R.string.session_gpx_failed))
            } finally {
                _ui.update { it.copy(exporting = false) }
            }
        }
    }

    /** Ouvre l'aperçu de la carte de partage. */
    fun share() = _ui.update { it.copy(sharePreview = true) }

    fun dismissSharePreview() = _ui.update { it.copy(sharePreview = false) }

    /** Carte capturée en bitmap par l'aperçu : PNG dans le cache puis feuille de partage. */
    fun shareImage(bitmap: ImageBitmap) {
        if (_ui.value.sharing) return
        _ui.update { it.copy(sharing = true) }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    File(dir, "elan-seance-$sessionId.png").also { f ->
                        f.outputStream().use { out -> bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out) }
                    }
                }
                export.share(context, file, "image/png", context.getString(R.string.session_share))
                _ui.update { it.copy(sharePreview = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(e.message ?: context.getString(R.string.session_share_failed))
            } finally {
                _ui.update { it.copy(sharing = false) }
            }
        }
    }
}
