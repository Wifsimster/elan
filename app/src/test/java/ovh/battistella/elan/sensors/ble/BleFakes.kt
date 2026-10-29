package ovh.battistella.elan.sensors.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class FakeLeScanner : LeScanner {
    val results = MutableSharedFlow<ScannedDevice>()

    var active = 0
        private set
    var starts = 0
        private set
    var lastServiceUuid: UUID? = null
        private set

    var failure: BleScanException? = null

    override fun scan(serviceUuid: UUID): Flow<ScannedDevice> = flow {
        starts++
        lastServiceUuid = serviceUuid
        active++
        try {
            failure?.let { throw it }
            results.collect { emit(it) }
        } finally {
            active--
        }
    }
}

class FakeGattLink(override val address: String, override val name: String? = null) : GattLink {
    var connectFailure: Exception? = null
    var discoverFailure: Exception? = null
    var notifyFailure: Exception? = null

    var connectDelayMs = 0L

    var connectCalls = 0
        private set
    var discovered = false
        private set
    var notifications: Pair<UUID, UUID>? = null
        private set
    var closed = false
        private set

    val frames = MutableSharedFlow<ByteArray>()
    private val _disconnected = MutableSharedFlow<Int>()
    override val disconnected: Flow<Int> = _disconnected

    suspend fun drop(status: Int = 8) = _disconnected.emit(status)

    suspend fun notify(bytes: ByteArray) = frames.emit(bytes)

    override suspend fun connect(timeoutMs: Long) {
        connectCalls++
        if (connectDelayMs > 0) delay(connectDelayMs)
        connectFailure?.let { throw it }
    }

    override suspend fun discoverServices() {
        discoverFailure?.let { throw it }
        discovered = true
    }

    override suspend fun enableNotifications(service: UUID, characteristic: UUID): Flow<ByteArray> {
        notifyFailure?.let { throw it }
        notifications = service to characteristic
        return frames.map { it }
    }

    override fun close() {
        closed = true
    }
}

class FakeGattLinkFactory : GattLinkFactory {
    val created = mutableListOf<FakeGattLink>()
    val names = mutableMapOf<String, String>()
    var configure: (FakeGattLink) -> Unit = {}

    override fun create(address: String): GattLink =
        FakeGattLink(address, names[address]).also { configure(it); created += it }

    fun last(address: String): FakeGattLink = created.last { it.address == address }
}

class FakeBleAdapterState(enabled: Boolean = true, override val supported: Boolean = true) : BleAdapterState {
    val flow = MutableStateFlow(enabled)
    override val enabled: Boolean get() = flow.value
    override val states: Flow<Boolean> = flow

    fun turnOff() { flow.value = false }
    fun turnOn() { flow.value = true }
}

fun inMemorySettings(): SettingsRepository = TestSupport.repositories(TestSupport.inMemoryDb()).settings

class MutableClock(var nowMs: Long = 1_700_000_000_000L) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    fun advance(ms: Long) { nowMs += ms }
}

fun hrFrame(bpm: Int): ByteArray = byteArrayOf(0x00, bpm.toByte())

fun crankFrame(revs: Int, time: Int): ByteArray = byteArrayOf(
    0x02,
    (revs and 0xFF).toByte(), ((revs shr 8) and 0xFF).toByte(),
    (time and 0xFF).toByte(), ((time shr 8) and 0xFF).toByte(),
)

fun wheelFrame(revs: Long, time: Int): ByteArray = byteArrayOf(
    0x01,
    (revs and 0xFF).toByte(), ((revs shr 8) and 0xFF).toByte(),
    ((revs shr 16) and 0xFF).toByte(), ((revs shr 24) and 0xFF).toByte(),
    (time and 0xFF).toByte(), ((time shr 8) and 0xFF).toByte(),
)

fun cscFrame(wheelRevs: Long, wheelTime: Int, crankRevs: Int, crankTime: Int): ByteArray =
    byteArrayOf(0x03) + wheelFrame(wheelRevs, wheelTime).drop(1) + crankFrame(crankRevs, crankTime).drop(1)
