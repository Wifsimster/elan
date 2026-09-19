package ovh.battistella.elan.ui.screens.outing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.elan.di.ApplicationScope
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.tracking.OutingState
import ovh.battistella.elan.tracking.TrackingController
import javax.inject.Inject
import javax.inject.Singleton
import ovh.battistella.elan.tracking.OutingPhase as TrackingPhase

/**
 * Adaptateur du [TrackingController] vers le contrat de l'écran Sortie : l'état
 * du contrôleur est recopié champ pour champ dans [OutingUi], et les commandes
 * `suspend` du contrôleur sont lancées sur la portée applicative (une
 * transition ne meurt pas avec le ViewModel qui l'a demandée).
 */
@Singleton
class TrackingOutingPort @Inject constructor(
    private val controller: TrackingController,
    @ApplicationScope private val scope: CoroutineScope,
) : OutingPort {

    override val state: StateFlow<OutingUi> = controller.state
        .map { it.toUi() }
        .stateIn(scope, SharingStarted.Eagerly, controller.state.value.toUi())

    override fun begin(type: ActivityType) {
        scope.launch { controller.begin(type) }
    }

    override fun pause() {
        scope.launch { controller.pause() }
    }

    override fun resume() {
        scope.launch { controller.resume() }
    }

    override fun finish() {
        scope.launch { controller.finish() }
    }

    override fun retrySave() {
        scope.launch { controller.retrySave() }
    }

    override fun discard() {
        scope.launch { controller.discard() }
    }
}

/** Projection de l'état du contrôleur vers celui de l'écran. */
fun OutingState.toUi(): OutingUi = OutingUi(
    phase = phase.toUi(),
    type = type,
    sessionId = sessionId,
    elapsedSec = elapsedSec,
    distanceM = distanceM,
    // Le contrôleur vaut 0.0 avant le premier point ; l'écran attend `null`
    // tant qu'aucun fix n'a été accepté (tuile « — » plutôt que « 0 »).
    speedKmh = if (pointCount == 0) null else speedKmh,
    maxSpeedKmh = if (pointCount == 0) null else maxSpeedKmh,
    elevationGainM = elevationGainM,
    pointCount = pointCount,
    accuracyM = accuracyM,
    gpsStatus = gpsStatus,
    livePath = livePath,
    bpm = bpm,
    cadenceRpm = cadenceRpm,
    wheelSpeedKmh = wheelSpeedKmh,
    caloriesLive = caloriesLive,
    errorMessage = error?.message,
    savedSessionId = savedSessionId,
)

private fun TrackingPhase.toUi(): OutingPhase = when (this) {
    TrackingPhase.IDLE -> OutingPhase.Idle
    TrackingPhase.REQUESTING -> OutingPhase.Requesting
    TrackingPhase.ACTIVE -> OutingPhase.Active
    TrackingPhase.PAUSED -> OutingPhase.Paused
    TrackingPhase.SAVING -> OutingPhase.Saving
    TrackingPhase.SAVE_FAILED -> OutingPhase.SaveFailed
}
