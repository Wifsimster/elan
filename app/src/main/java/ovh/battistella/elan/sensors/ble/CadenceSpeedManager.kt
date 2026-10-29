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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.settings.BleDevice
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.tracking.CadenceSamples
import ovh.battistella.elan.tracking.CscFrame
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class CscState(
    val status: SensorStatus,
    val cadenceRpm: Int? = null,
    val speedKmh: Double? = null,
    val devices: List<ScannedDevice> = emptyList(),
    val scanned: List<ScannedDevice> = emptyList(),
    val error: String? = null,
    val wheelCircumferenceMm: Int = DEFAULT_WHEEL_MM,
)

const val DEFAULT_WHEEL_MM = 2105

const val MAX_CSC_DEVICES = 2

internal const val TIME_UNIT = 1024.0

internal const val STALE_MS = 3_000L

internal const val MAX_RPM = 250

internal const val MAX_KMH = 120.0

internal fun delta(curr: Long, prev: Long, mod: Long): Long = (curr - prev + mod) % mod

@Singleton
class CadenceSpeedManager @Inject constructor(
    private val scanner: BleScanner,
    private val links: GattLinkFactory,
    private val adapter: BleAdapterState,
    private val permissions: BlePermissionCheck,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @BleScope private val scope: CoroutineScope,
) : CadenceSamples {

    private val supported = adapter.supported

    private val _state = MutableStateFlow(CscState(if (supported) SensorStatus.Idle else SensorStatus.Unsupported))
    val state: StateFlow<CscState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<CscFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun frames(): SharedFlow<CscFrame> = _frames.asSharedFlow()

    private class Connection(val link: GattLink, val name: String) {
        var notifyJob: Job? = null
        var lostJob: Job? = null
    }

    private val connections = LinkedHashMap<String, Connection>()

    private val lastRaw = HashMap<String, CscRaw>()

    private val connecting = HashSet<String>()

    private val intentional = HashSet<String>()
    private val reconnectJobs = HashMap<String, Job>()
    private val reconnectAttempts = HashMap<String, Int>()

    private var scanJob: Job? = null
    private var staleJob: Job? = null
    private var crankMoveTs = 0L

    private var wheelSetByUser = false
    private var wheelMoveTs = 0L

    init {
        if (supported) {
            scope.launch {
                val mm = SettingsJson.parseWheelMm(settings.getSetting(SettingsRepository.Keys.CSC_WHEEL_MM))
                if (!wheelSetByUser) _state.update { it.copy(wheelCircumferenceMm = mm.roundToInt()) }
                adapter.states.collect { on -> if (on) onAdapterOn() else onAdapterOff() }
            }
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

    fun disconnect(deviceId: String) {
        if (!supported) return
        scope.launch { doDisconnect(listOf(deviceId)) }
    }

    fun disconnectAll() {
        if (!supported) return
        scope.launch { doDisconnect(connections.keys.toList()) }
    }

    fun addDevice(deviceId: String) = connect(deviceId)

    fun removeDevice(deviceId: String) = disconnect(deviceId)

    fun setWheelCircumference(mm: Int) {
        scope.launch {
            wheelSetByUser = true
            _state.update { it.copy(wheelCircumferenceMm = mm) }
            settings.setCscWheelMm(mm.toDouble())
        }
    }

    private fun doStartScan() {
        _state.update { it.copy(error = null) }
        if (!permissions.granted()) {
            _state.update { it.copy(status = SensorStatus.Error, error = PERMISSIONS_DENIED) }
            return
        }
        scanJob?.cancel()
        val flow = scanner.acquire(this, BleUuids.CSC_SERVICE) { restIfScanning() }
        _state.update { it.copy(status = SensorStatus.Scanning, scanned = emptyList()) }
        scanJob = scope.launch {
            try {
                flow.collect { dev ->
                    if (!connections.containsKey(dev.id)) _state.update { it.copy(scanned = it.scanned + dev) }
                }
                restIfScanning()
            } catch (e: BleException) {
                _state.update { it.copy(status = SensorStatus.Error, error = e.message) }
            }
        }
    }

    private fun doStopScan() {
        scanJob?.cancel()
        scanJob = null
        scanner.release(this)
        restIfScanning()
    }

    private fun restingStatus(): SensorStatus =
        if (connections.isNotEmpty()) SensorStatus.Connected else SensorStatus.Idle

    private fun restIfScanning() {
        val resting = restingStatus()
        _state.update { if (it.status == SensorStatus.Scanning) it.copy(status = resting) else it }
    }

    private fun syncDevices() {
        val devices = connections.values.map { ScannedDevice(it.link.address, it.name) }
        _state.update { it.copy(devices = devices, status = restingStatus()) }
        if (connections.isEmpty()) stopStaleWatch() else startStaleWatch()
    }

    private suspend fun doConnect(deviceId: String) {
        if (connections.containsKey(deviceId) || deviceId in connecting) return
        if (connections.size >= MAX_CSC_DEVICES) {
            _state.update { it.copy(error = "Deux capteurs maximum.") }
            return
        }
        connecting += deviceId
        _state.update { it.copy(error = null) }
        intentional -= deviceId
        reconnectJobs.remove(deviceId)?.cancel()
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
            val frames = l.enableNotifications(BleUuids.CSC_SERVICE, BleUuids.CSC_MEASUREMENT)
            if (deviceId in intentional) {
                l.close()
                connecting -= deviceId
                syncDevices()
                return
            }

            val conn = Connection(l, l.name ?: "Capteur vélo")
            connections[deviceId] = conn
            conn.lostJob = scope.launch {
                val status = l.disconnected.first()
                onLost(deviceId, l, status)
            }
            conn.notifyJob = scope.launch { frames.collect { onFrame(deviceId, it) } }
            reconnectAttempts.remove(deviceId)
            connecting -= deviceId
            syncDevices()
            persistDevices()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: CONNECT_FAILED) }
            candidate?.close()
            connecting -= deviceId
            scheduleReconnect(deviceId)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onLost(deviceId: String, l: GattLink, status: Int) {
        val conn = connections[deviceId] ?: return
        if (conn.link !== l) return
        dropConnection(deviceId)
        scheduleReconnect(deviceId)
    }

    private fun dropConnection(deviceId: String) {
        val conn = connections.remove(deviceId) ?: return
        conn.notifyJob?.cancel()
        conn.lostJob?.cancel()
        conn.link.close()
        lastRaw.remove(deviceId)
        val devices = connections.values.map { ScannedDevice(it.link.address, it.name) }
        _state.update { it.copy(devices = devices) }
        if (connections.isEmpty()) resetMeasures()
    }

    private fun resetMeasures() {
        crankMoveTs = 0L
        wheelMoveTs = 0L
        _state.update { it.copy(cadenceRpm = null, speedKmh = null) }
    }

    private fun scheduleReconnect(deviceId: String) {
        if (!adapter.enabled) {
            _state.update { it.copy(status = SensorStatus.Error, error = BLUETOOTH_OFF) }
            return
        }
        val attempts = reconnectAttempts[deviceId] ?: 0
        if (deviceId in intentional || attempts >= MAX_RECONNECT_ATTEMPTS) {
            syncDevices()
            return
        }
        reconnectAttempts[deviceId] = attempts + 1
        reconnectJobs.remove(deviceId)?.cancel()
        reconnectJobs[deviceId] = scope.launch {
            delay(reconnectDelayMs(attempts))
            reconnectJobs.remove(deviceId)
            doConnect(deviceId)
        }
        if (connections.isEmpty()) {
            _state.update { it.copy(status = SensorStatus.Reconnecting) }
        } else {
            syncDevices()
        }
    }

    private suspend fun doDisconnect(ids: List<String>) {
        for (id in ids) {
            intentional += id
            reconnectJobs.remove(id)?.cancel()
            reconnectAttempts.remove(id)
            connecting -= id
            dropConnection(id)
        }
        syncDevices()
        persistDevices()
    }

    private suspend fun persistDevices() {
        val list = connections.values.map { BleDevice(it.link.address, it.name) }
        settings.setCscDevices(list)
    }

    private fun onFrame(deviceId: String, bytes: ByteArray) {
        val raw = parseCsc(bytes) ?: return
        val prev = lastRaw.put(deviceId, raw) ?: return
        val now = clock.millis()
        var cadence = _state.value.cadenceRpm
        var speed = _state.value.speedKmh
        var updated = false

        if (raw.crankRevs != null && prev.crankRevs != null && raw.crankTime != null && prev.crankTime != null) {
            val dRev = delta(raw.crankRevs.toLong(), prev.crankRevs.toLong(), 0x10000L)
            val dT = delta(raw.crankTime.toLong(), prev.crankTime.toLong(), 0x10000L)
            if (dRev == 0L) {
                cadence = 0
                updated = true
            } else if (dT > 0) {
                val rpm = (dRev / (dT / TIME_UNIT) * 60).roundToInt()
                if (rpm <= MAX_RPM) {
                    cadence = rpm
                    crankMoveTs = now
                    updated = true
                }
            }
        }

        if (raw.wheelRevs != null && prev.wheelRevs != null && raw.wheelTime != null && prev.wheelTime != null) {
            val dRev = delta(raw.wheelRevs, prev.wheelRevs, 0x1_0000_0000L)
            val dT = delta(raw.wheelTime.toLong(), prev.wheelTime.toLong(), 0x10000L)
            if (dRev == 0L) {
                speed = 0.0
                updated = true
            } else if (dT > 0) {
                val mps = dRev * (_state.value.wheelCircumferenceMm / 1000.0) / (dT / TIME_UNIT)
                val kmh = mps * 3.6
                if (kmh <= MAX_KMH) {
                    speed = kmh
                    wheelMoveTs = now
                    updated = true
                }
            }
        }

        if (!updated) return
        _state.update { it.copy(cadenceRpm = cadence, speedKmh = speed) }
        _frames.tryEmit(CscFrame(now, cadence, speed))
    }

    private fun startStaleWatch() {
        if (staleJob?.isActive == true) return
        staleJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val now = clock.millis()
                _state.update { s ->
                    var next = s
                    if (crankMoveTs != 0L && now - crankMoveTs > STALE_MS && (s.cadenceRpm ?: 0) != 0) {
                        next = next.copy(cadenceRpm = 0)
                    }
                    if (wheelMoveTs != 0L && now - wheelMoveTs > STALE_MS && (s.speedKmh ?: 0.0) != 0.0) {
                        next = next.copy(speedKmh = 0.0)
                    }
                    next
                }
            }
        }
    }

    private fun stopStaleWatch() {
        staleJob?.cancel()
        staleJob = null
    }

    private fun onAdapterOff() {
        _state.update {
            val status = when (it.status) {
                SensorStatus.Connected, SensorStatus.Connecting, SensorStatus.Reconnecting -> SensorStatus.Error
                else -> it.status
            }
            it.copy(status = status, cadenceRpm = null, speedKmh = null, error = BLUETOOTH_OFF)
        }
    }

    private suspend fun onAdapterOn() {
        _state.update { it.copy(error = null) }
        val saved = SettingsJson.parseCscDevices(settings.getSetting(SettingsRepository.Keys.CSC_DEVICES))
        for (d in saved) {
            reconnectAttempts.remove(d.id)
            scope.launch { doConnect(d.id) }
        }
    }
}
