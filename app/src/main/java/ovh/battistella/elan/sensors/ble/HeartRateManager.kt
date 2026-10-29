package ovh.battistella.elan.sensors.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.settings.BleDevice
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.tracking.HeartRateSamples
import ovh.battistella.elan.tracking.HrFrame
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class HrState(
    val status: SensorStatus,
    val bpm: Int? = null,
    val device: ScannedDevice? = null,
    val scanned: List<ScannedDevice> = emptyList(),
    val error: String? = null,
)

internal const val MAX_RECONNECT_ATTEMPTS = 5

internal fun reconnectDelayMs(attempt: Int): Long = minOf(1000L shl attempt, 15_000L)

internal const val PERMISSIONS_DENIED = "Permissions Bluetooth refusées."
internal const val BLUETOOTH_OFF = "Bluetooth désactivé."
internal const val CONNECT_FAILED = "Échec de connexion."

@Singleton
class HeartRateManager @Inject constructor(
    private val scanner: BleScanner,
    private val links: GattLinkFactory,
    private val adapter: BleAdapterState,
    private val permissions: BlePermissionCheck,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @BleScope private val scope: CoroutineScope,
) : HeartRateSamples {

    private val supported = adapter.supported

    private val _state = MutableStateFlow(HrState(if (supported) SensorStatus.Idle else SensorStatus.Unsupported))
    val state: StateFlow<HrState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<HrFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun frames(): SharedFlow<HrFrame> = _frames.asSharedFlow()

    private var link: GattLink? = null
    private var notifyJob: Job? = null
    private var lostJob: Job? = null
    private var scanJob: Job? = null

    private var connecting = false

    private var userDisconnected = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    init {
        if (supported) {
            scope.launch { adapter.states.collect { on -> if (on) onAdapterOn() else onAdapterOff() } }
        }
    }

    fun startScan() {
        if (!supported) return
        scope.launch { doStartScan() }
    }

    fun stopScan() {
        if (!supported) return
        scope.launch { doStopScan() }
    }

    fun connect(deviceId: String) {
        if (!supported) return
        scope.launch { doConnect(deviceId) }
    }

    fun disconnect() {
        if (!supported) return
        scope.launch { doDisconnect() }
    }

    private fun doStartScan() {
        _state.update { it.copy(error = null) }
        if (!permissions.granted()) {
            _state.update { it.copy(status = SensorStatus.Error, error = PERMISSIONS_DENIED) }
            return
        }
        scanJob?.cancel()
        _state.update { it.copy(status = SensorStatus.Scanning, scanned = emptyList()) }
        val flow = scanner.acquire(this, BleUuids.HEART_RATE_SERVICE) { idleIfScanning() }
        scanJob = scope.launch {
            try {
                flow.collect { dev -> _state.update { it.copy(scanned = it.scanned + dev) } }
                idleIfScanning()
            } catch (e: BleException) {
                _state.update { it.copy(status = SensorStatus.Error, error = e.message) }
            }
        }
    }

    private fun doStopScan() {
        scanJob?.cancel()
        scanJob = null
        scanner.release(this)
        idleIfScanning()
    }

    private fun idleIfScanning() {
        val resting = if (link != null) SensorStatus.Connected else SensorStatus.Idle
        _state.update { if (it.status == SensorStatus.Scanning) it.copy(status = resting) else it }
    }

    private suspend fun doConnect(deviceId: String) {
        if (connecting) return
        if (link?.address == deviceId) return
        connecting = true
        link?.let { previous ->
            link = null
            cancelLinkJobs()
            previous.close()
        }
        _state.update { it.copy(error = null) }
        userDisconnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        doStopScan()
        _state.update { it.copy(status = SensorStatus.Connecting) }

        var candidate: GattLink? = null
        try {
            if (!permissions.granted()) throw BleException(PERMISSIONS_DENIED)
            if (!adapter.enabled) throw BleException(BLUETOOTH_OFF)
            val l = links.create(deviceId)
            candidate = l
            l.connect(CONNECT_TIMEOUT_MS)
            l.discoverServices()
            val frames = l.enableNotifications(BleUuids.HEART_RATE_SERVICE, BleUuids.HEART_RATE_MEASUREMENT)
            if (userDisconnected) {
                l.close()
                connecting = false
                return
            }

            link = l
            lostJob = scope.launch {
                val status = l.disconnected.first()
                onLost(l, deviceId, status)
            }
            notifyJob = scope.launch { frames.collect { onFrame(it) } }

            val info = ScannedDevice(deviceId, l.name ?: "Ceinture cardiaque")
            _state.update {
                it.copy(status = SensorStatus.Connected, device = info, scanned = emptyList())
            }
            reconnectAttempts = 0
            connecting = false
            settings.setHrDevice(BleDevice(info.id, info.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: CONNECT_FAILED) }
            link = null
            cancelLinkJobs()
            candidate?.close()
            connecting = false
            scheduleReconnect(deviceId)
        }
    }

    private fun onFrame(bytes: ByteArray) {
        val bpm = parseHeartRate(bytes) ?: return
        _state.update { it.copy(bpm = bpm) }
        _frames.tryEmit(HrFrame(clock.millis(), bpm))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onLost(l: GattLink, deviceId: String, status: Int) {
        if (link !== l) return
        link = null
        cancelLinkJobs()
        l.close()
        _state.update { it.copy(bpm = null, device = null) }
        scheduleReconnect(deviceId)
    }

    private fun scheduleReconnect(deviceId: String) {
        if (!adapter.enabled) {
            _state.update { it.copy(status = SensorStatus.Error, error = BLUETOOTH_OFF) }
            return
        }
        if (userDisconnected || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _state.update { it.copy(status = SensorStatus.Idle) }
            return
        }
        val attempt = reconnectAttempts++
        _state.update { it.copy(status = SensorStatus.Reconnecting) }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMs(attempt))
            reconnectJob = null
            doConnect(deviceId)
        }
    }

    private fun doDisconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        val l = link
        link = null
        cancelLinkJobs()
        _state.update { it.copy(status = SensorStatus.Idle, bpm = null, device = null) }
        l?.close()
    }

    private fun cancelLinkJobs() {
        notifyJob?.cancel()
        notifyJob = null
        lostJob?.cancel()
        lostJob = null
    }

    private fun onAdapterOff() {
        _state.update {
            val status = when (it.status) {
                SensorStatus.Connected, SensorStatus.Connecting, SensorStatus.Reconnecting -> SensorStatus.Error
                else -> it.status
            }
            it.copy(status = status, bpm = null, error = BLUETOOTH_OFF)
        }
    }

    private suspend fun onAdapterOn() {
        _state.update { it.copy(error = null) }
        if (link != null || connecting) return
        val saved = SettingsJson.parseHrDevice(settings.getSetting(SettingsRepository.Keys.HR_DEVICE)) ?: return
        reconnectAttempts = 0
        scope.launch { doConnect(saved.id) }
    }
}
