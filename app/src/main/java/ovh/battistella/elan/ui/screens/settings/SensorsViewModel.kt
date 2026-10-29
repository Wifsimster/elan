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

const val WHEEL_MM_MIN = 1000
const val WHEEL_MM_MAX = 2400

@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val heartRate: HeartRateManager,
    private val cadence: CadenceSpeedManager,
) : ViewModel() {

    val hr: StateFlow<HrState> = heartRate.state
    val csc: StateFlow<CscState> = cadence.state

    fun startHrScan() = heartRate.startScan()
    fun stopHrScan() = heartRate.stopScan()
    fun connectHr(deviceId: String) = heartRate.connect(deviceId)
    fun disconnectHr() = heartRate.disconnect()

    fun startCscScan() = cadence.startScan()
    fun stopCscScan() = cadence.stopScan()
    fun connectCsc(deviceId: String) = cadence.connect(deviceId)
    fun disconnectCsc(deviceId: String) = cadence.disconnect(deviceId)

    fun setWheelMm(mm: Int) = cadence.setWheelCircumference(mm.coerceIn(WHEEL_MM_MIN, WHEEL_MM_MAX))

    fun currentWheelSize(): WheelSize? = matchWheelSize(csc.value.wheelCircumferenceMm)
}
