package ovh.battistella.elan.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import ovh.battistella.elan.domain.WheelSize
import ovh.battistella.elan.domain.matchWheelSize
import ovh.battistella.elan.sensors.ble.CadenceSpeedManager
import ovh.battistella.elan.sensors.ble.CscState
import ovh.battistella.elan.sensors.ble.HeartRateManager
import ovh.battistella.elan.sensors.ble.HrState
import javax.inject.Inject

/** Bornes du réglage de circonférence de roue (mm), pas de 1. */
const val WHEEL_MM_MIN = 1000
const val WHEEL_MM_MAX = 2400

/**
 * Cartes « Ceinture cardiaque » et « Capteurs vélo » : projection directe des
 * deux gestionnaires BLE (singletons de processus, une seule connexion
 * partagée avec les écrans de séance). La demande de permissions se fait
 * depuis la carte (elle exige une Activity) avant `startScan`.
 */
@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val heartRate: HeartRateManager,
    private val cadence: CadenceSpeedManager,
) : ViewModel() {

    val hr: StateFlow<HrState> = heartRate.state
    val csc: StateFlow<CscState> = cadence.state

    // ---- ceinture --------------------------------------------------------

    fun startHrScan() = heartRate.startScan()
    fun stopHrScan() = heartRate.stopScan()
    fun connectHr(deviceId: String) = heartRate.connect(deviceId)
    fun disconnectHr() = heartRate.disconnect()

    // ---- capteurs vélo ---------------------------------------------------

    fun startCscScan() = cadence.startScan()
    fun stopCscScan() = cadence.stopScan()
    fun connectCsc(deviceId: String) = cadence.connect(deviceId)
    fun disconnectCsc(deviceId: String) = cadence.disconnect(deviceId)

    /** Circonférence bornée à [WHEEL_MM_MIN]..[WHEEL_MM_MAX] ; persistée par le gestionnaire. */
    fun setWheelMm(mm: Int) = cadence.setWheelCircumference(mm.coerceIn(WHEEL_MM_MIN, WHEEL_MM_MAX))

    /** Preset de pneu correspondant exactement à la circonférence courante, sinon `null` (« Personnalisé »). */
    fun currentWheelSize(): WheelSize? = matchWheelSize(csc.value.wheelCircumferenceMm)
}
