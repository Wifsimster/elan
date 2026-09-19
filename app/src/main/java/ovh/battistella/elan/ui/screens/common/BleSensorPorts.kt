package ovh.battistella.elan.ui.screens.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.elan.di.ApplicationScope
import ovh.battistella.elan.sensors.ble.CadenceSpeedManager
import ovh.battistella.elan.sensors.ble.HeartRateManager
import ovh.battistella.elan.sensors.ble.SensorStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptateurs des gestionnaires BLE vers les vues minimales des écrans : chaque
 * champ est une projection de l'état du gestionnaire, partagée sur la portée
 * applicative (les gestionnaires sont eux-mêmes des singletons de processus).
 */
@Singleton
class BleHeartRatePort @Inject constructor(
    manager: HeartRateManager,
    @ApplicationScope scope: CoroutineScope,
) : HeartRatePort {
    override val bpm: StateFlow<Int?> = manager.state.map { it.bpm }
        .stateIn(scope, SharingStarted.Eagerly, manager.state.value.bpm)
    override val connected: StateFlow<Boolean> = manager.state.map { it.status == SensorStatus.Connected }
        .stateIn(scope, SharingStarted.Eagerly, manager.state.value.status == SensorStatus.Connected)
}

@Singleton
class BleCadencePort @Inject constructor(
    manager: CadenceSpeedManager,
    @ApplicationScope scope: CoroutineScope,
) : CadencePort {
    override val cadenceRpm: StateFlow<Int?> = manager.state.map { it.cadenceRpm }
        .stateIn(scope, SharingStarted.Eagerly, manager.state.value.cadenceRpm)
    override val speedKmh: StateFlow<Double?> = manager.state.map { it.speedKmh }
        .stateIn(scope, SharingStarted.Eagerly, manager.state.value.speedKmh)
    override val hasSensor: StateFlow<Boolean> = manager.state.map { it.devices.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, manager.state.value.devices.isNotEmpty())
}
