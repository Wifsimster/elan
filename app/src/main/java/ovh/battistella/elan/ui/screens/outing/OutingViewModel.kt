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
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.toActivityType
import ovh.battistella.elan.domain.usesPace
import ovh.battistella.elan.ui.screens.common.CadencePort
import javax.inject.Inject

/** Boîte de dialogue ouverte sur l'écran Sortie. */
enum class OutingDialog { None, Finish, Discard }

/** Alerte de permission ou d'échec, affichée en dialogue simple « OK ». */
enum class OutingAlert { Coarse, Denied, StartFailed, SaveFailed }

/** Barre de contrôle : trois dispositions selon la phase. */
sealed interface OutingControls {
    /** « Démarrer la sortie » (chargement pendant `Requesting`). */
    data class Start(val requesting: Boolean) : OutingControls

    /** « Abandonner » + « Réessayer » après un échec d'enregistrement. */
    data object SaveFailed : OutingControls

    /** « Pause » / « Reprendre » + « Terminer » (chargement pendant `Saving`). */
    data class Running(val paused: Boolean, val saving: Boolean) : OutingControls
}

data class OutingScreenUi(
    val outing: OutingUi = OutingUi(),
    val type: ActivityType = ActivityType.VELO,
    val meta: ActivityMeta = ActivityType.VELO.meta,
    /** Effort en allure (min/km) plutôt qu'en vitesse (km/h). */
    val pace: Boolean = false,
    /** Au moins un capteur cadence connu : tuile Cadence (à vélo). */
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

/** Événements ponctuels vers l'écran (navigation). */
sealed interface OutingEvent {
    /** Séance enregistrée : remplacer l'écran par son détail. */
    data class Saved(val sessionId: Long) : OutingEvent

    /** Quitter l'écran (abandon confirmé, ou sortie jamais démarrée). */
    data object Exit : OutingEvent
}

/**
 * Écran de séance tracée au GPS. Le type vient de la route (`outing/{type}`)
 * et pilote la teinte, le libellé et l'unité d'effort. Toute la mécanique de
 * suivi vit derrière [OutingPort] ; ici : phases → contrôles, confirmations,
 * alertes de permission et navigation.
 */
@HiltViewModel
class OutingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val port: OutingPort,
    cadence: CadencePort,
) : ViewModel() {

    val type: ActivityType = toActivityType(savedStateHandle.get<String>("type"))

    private val dialog = MutableStateFlow(OutingDialog.None)
    private val alert = MutableStateFlow<OutingAlert?>(null)

    private val _events = MutableSharedFlow<OutingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<OutingEvent> = _events.asSharedFlow()

    val ui: StateFlow<OutingScreenUi> = combine(port.state, cadence.hasSensor, dialog, alert) { s, sensor, d, a ->
        OutingScreenUi(
            outing = s,
            type = type,
            meta = type.meta,
            pace = usesPace(type),
            hasCadenceSensor = sensor || s.cadenceRpm != null,
            dialog = d,
            alert = a,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutingScreenUi(type = type, meta = type.meta, pace = usesPace(type)))

    init {
        // Le port est un singleton de processus : son état à la création de
        // l'écran (id de la dernière séance enregistrée, dernière erreur) est
        // de l'histoire ancienne et s'ignore tant qu'il n'a pas bougé — sinon
        // un nouvel écran sauterait aussitôt vers le détail précédent ou
        // ré-alerterait.
        val initial = port.state.value
        // Enregistrement réussi → détail de la séance (une seule fois par id).
        viewModelScope.launch {
            port.state.map { it.savedSessionId }.dropWhile { it == initial.savedSessionId }.filterNotNull().distinctUntilChanged().collect { id ->
                _events.emit(OutingEvent.Saved(id))
            }
        }
        // Échecs remontés par le contrôleur : alerte adaptée à la phase.
        val initialError = initial.errorMessage to initial.phase
        viewModelScope.launch {
            port.state.map { it.errorMessage to it.phase }.dropWhile { it == initialError }.distinctUntilChanged().collect { (message, phase) ->
                if (message == null) return@collect
                alert.value = if (phase == OutingPhase.SaveFailed) OutingAlert.SaveFailed else OutingAlert.StartFailed
            }
        }
    }

    /**
     * Résultat de la demande de permission (l'écran la porte). Position
     * précise → démarrage ; approximative seule → « Position précise requise » ;
     * rien → « Localisation refusée ».
     */
    fun onLocationPermission(fine: Boolean, coarse: Boolean) {
        when {
            fine -> port.begin(type)
            coarse -> alert.value = OutingAlert.Coarse
            else -> alert.value = OutingAlert.Denied
        }
    }

    fun pause() = port.pause()

    fun resume() = port.resume()

    /** « Terminer » : confirmation, sauf enregistrement déjà en cours. */
    fun requestFinish() {
        if (port.state.value.phase == OutingPhase.Saving) return
        dialog.value = OutingDialog.Finish
    }

    fun confirmFinish() {
        dialog.value = OutingDialog.None
        port.finish()
    }

    fun retrySave() = port.retrySave()

    /**
     * Croix et retour matériel : quitter sans confirmation si rien n'a démarré,
     * ignorer en plein enregistrement, sinon demander confirmation d'abandon.
     */
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

    /** Abandon après un échec d'enregistrement : l'utilisateur a choisi, pas de reconfirmation. */
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
