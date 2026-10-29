package ovh.battistella.elan.ui.screens.common

import kotlinx.coroutines.flow.StateFlow

interface HeartRatePort {
    val bpm: StateFlow<Int?>
    val connected: StateFlow<Boolean>
}

interface CadencePort {
    val cadenceRpm: StateFlow<Int?>
    val speedKmh: StateFlow<Double?>

    val hasSensor: StateFlow<Boolean>
}
