package ovh.battistella.elan.ui.screens.outing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.elan.domain.ActivityMeta
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.toActivityType
import ovh.battistella.elan.domain.usesPace
import ovh.battistella.elan.ui.screens.common.CadencePort
import javax.inject.Inject

enum class OutingDialog { None, Finish, Discard }

enum class OutingAlert { Coarse, Denied, StartFailed, SaveFailed }

sealed interface OutingControls {
    data class Start(val requesting: Boolean) : OutingControls

    data object SaveFailed : OutingControls

    data class Running(val paused: Boolean, val saving: Boolean) : OutingControls
}

data class OutingScreenUi(
    val outing: OutingUi = OutingUi(),
    val type: ActivityType = ActivityType.VELO,
    val meta: ActivityMeta = ActivityType.VELO.meta,
    val pace: Boolean = false,
    val hasCadenceSensor: Boolean = false,
    val dialog: OutingDialog = OutingDialog.None,
    val alert: OutingAlert? = null,
) {
    val phase: OutingPhase get() = outing.phase
    val controls: OutingControls
        get() = when (outing.phase) {
            OutingPhase.Idle -> OutingControls.Start(requesting = false)
            OutingPhase.Requesting -> OutingControls.Start(requesting = true)
            OutingPhase.SaveFailed -> OutingControls.SaveFailed
            OutingPhase.Active -> OutingControls.Running(paused = false, saving = false)
            OutingPhase.Paused -> OutingControls.Running(paused = true, saving = false)
            OutingPhase.Saving -> OutingControls.Running(paused = false, saving = true)
        }
}

sealed interface OutingEvent {
    data class Saved(val sessionId: Long) : OutingEvent

    data object Exit : OutingEvent
}

@HiltViewModel
class OutingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val port: OutingPort,
    cadence: CadencePort,
) : ViewModel() {

    val type: ActivityType = toActivityType(savedStateHandle.get<String>("type"))
        .takeIf { isGpsActivity(it) } ?: ActivityType.VELO

    private val dialog = MutableStateFlow(OutingDialog.None)
    private val alert = MutableStateFlow<OutingAlert?>(null)

    private val _events = MutableSharedFlow<OutingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<OutingEvent> = _events.asSharedFlow()

    val ui: StateFlow<OutingScreenUi> = combine(port.state, cadence.hasSensor, dialog, alert) { s, sensor, d, a ->
        val t = if (s.phase != OutingPhase.Idle) s.type else type
        OutingScreenUi(
            outing = s,
            type = t,
            meta = t.meta,
            pace = usesPace(t),
            hasCadenceSensor = sensor || s.cadenceRpm != null,
            dialog = d,
            alert = a,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutingScreenUi(type = type, meta = type.meta, pace = usesPace(type)))

    init {
        val initial = port.state.value
        viewModelScope.launch {
            port.state.map { it.savedSessionId }.dropWhile { it == initial.savedSessionId }.filterNotNull().distinctUntilChanged().collect { id ->
                _events.emit(OutingEvent.Saved(id))
            }
        }
        val initialError = initial.errorMessage to initial.phase
        viewModelScope.launch {
            port.state.map { it.errorMessage to it.phase }.dropWhile { it == initialError }.distinctUntilChanged().collect { (message, phase) ->
                if (message == null) return@collect
                alert.value = if (phase == OutingPhase.SaveFailed) OutingAlert.SaveFailed else OutingAlert.StartFailed
            }
        }
    }

    fun onLocationPermission(fine: Boolean, coarse: Boolean) {
        when {
            fine -> port.begin(type)
            coarse -> alert.value = OutingAlert.Coarse
            else -> alert.value = OutingAlert.Denied
        }
    }

    fun pause() = port.pause()

    fun resume() = port.resume()

    fun requestFinish() {
        if (port.state.value.phase == OutingPhase.Saving) return
        dialog.value = OutingDialog.Finish
    }

    fun confirmFinish() {
        dialog.value = OutingDialog.None
        port.finish()
    }

    fun retrySave() = port.retrySave()

    fun requestDiscard() {
        when (port.state.value.phase) {
            OutingPhase.Idle -> _events.tryEmit(OutingEvent.Exit)
            OutingPhase.Saving -> Unit
            else -> dialog.value = OutingDialog.Discard
        }
    }

    fun confirmDiscard() {
        dialog.value = OutingDialog.None
        port.discard()
        _events.tryEmit(OutingEvent.Exit)
    }

    fun discardAfterFailure() {
        port.discard()
        _events.tryEmit(OutingEvent.Exit)
    }

    fun dismissDialog() {
        dialog.value = OutingDialog.None
    }

    fun dismissAlert() {
        alert.value = null
    }
}
