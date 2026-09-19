package ovh.battistella.elan.ui.screens.common

import kotlinx.coroutines.flow.StateFlow

/**
 * Vue minimale de la ceinture cardio pour les écrans (pastille `HrBadge`,
 * tuile Cardio). Implémentée par un adaptateur du `HeartRateManager`
 * (module `sensors/ble/`).
 */
interface HeartRatePort {
    /** Dernier bpm reçu, `null` sans ceinture ou sans trame. */
    val bpm: StateFlow<Int?>
    val connected: StateFlow<Boolean>
}

/**
 * Vue minimale des capteurs cadence / vitesse roue (vélo). Implémentée par un
 * adaptateur du `CadenceSpeedManager`.
 */
interface CadencePort {
    val cadenceRpm: StateFlow<Int?>
    val speedKmh: StateFlow<Double?>

    /** Au moins un capteur mémorisé ou connecté : les tuiles vélo s'affichent. */
    val hasSensor: StateFlow<Boolean>
}
