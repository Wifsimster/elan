// Doublures de la couche BLE : radio, liens GATT, adaptateur, réglages et
// horloge pilotables depuis un `TestScope` (temps virtuel), sans Bluetooth.
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

/** Radio factice : les annonces sont poussées via [results] pendant qu'un scan est actif. */
class FakeLeScanner : LeScanner {
    val results = MutableSharedFlow<ScannedDevice>()

    /** Nombre de scans matériels en cours (0 ou 1 si l'arbitrage fonctionne). */
    var active = 0
        private set
    var starts = 0
        private set
    var lastServiceUuid: UUID? = null
        private set

    /** Fait échouer le prochain scan (radio coupée, permission manquante…). */
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

/** Lien GATT factice : chaque étape peut échouer ou traîner, les trames sont injectées via [frames]. */
class FakeGattLink(override val address: String, override val name: String? = null) : GattLink {
    var connectFailure: Exception? = null
    var discoverFailure: Exception? = null
    var notifyFailure: Exception? = null

    /** Durée simulée de la connexion (temps virtuel). */
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

    /** Coupure involontaire côté capteur (statut GATT). */
    suspend fun drop(status: Int = 8) = _disconnected.emit(status)

    /** Une trame de notification reçue (attend qu'un collecteur soit abonné). */
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

/** Fabrique factice : mémorise chaque lien créé ; [configure] règle les pannes par adresse. */
class FakeGattLinkFactory : GattLinkFactory {
    val created = mutableListOf<FakeGattLink>()
    val names = mutableMapOf<String, String>()
    var configure: (FakeGattLink) -> Unit = {}

    override fun create(address: String): GattLink =
        FakeGattLink(address, names[address]).also { configure(it); created += it }

    /** Dernier lien créé pour `address`. */
    fun last(address: String): FakeGattLink = created.last { it.address == address }
}

class FakeBleAdapterState(enabled: Boolean = true, override val supported: Boolean = true) : BleAdapterState {
    val flow = MutableStateFlow(enabled)
    override val enabled: Boolean get() = flow.value
    override val states: Flow<Boolean> = flow

    fun turnOff() { flow.value = false }
    fun turnOn() { flow.value = true }
}

/** Réglages sur une vraie base Room en mémoire (Robolectric), E/S inline. */
fun inMemorySettings(): SettingsRepository = TestSupport.repositories(TestSupport.inMemoryDb()).settings

/** Horloge réglable (ms), indépendante du temps virtuel des coroutines. */
class MutableClock(var nowMs: Long = 1_700_000_000_000L) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    fun advance(ms: Long) { nowMs += ms }
}

// ---- trames --------------------------------------------------------------

fun hrFrame(bpm: Int): ByteArray = byteArrayOf(0x00, bpm.toByte())

/** Trame CSC pédalier seul (drapeau 0x02). */
fun crankFrame(revs: Int, time: Int): ByteArray = byteArrayOf(
    0x02,
    (revs and 0xFF).toByte(), ((revs shr 8) and 0xFF).toByte(),
    (time and 0xFF).toByte(), ((time shr 8) and 0xFF).toByte(),
)

/** Trame CSC roue seule (drapeau 0x01). */
fun wheelFrame(revs: Long, time: Int): ByteArray = byteArrayOf(
    0x01,
    (revs and 0xFF).toByte(), ((revs shr 8) and 0xFF).toByte(),
    ((revs shr 16) and 0xFF).toByte(), ((revs shr 24) and 0xFF).toByte(),
    (time and 0xFF).toByte(), ((time shr 8) and 0xFF).toByte(),
)

/** Trame CSC roue + pédalier (drapeaux 0x03). */
fun cscFrame(wheelRevs: Long, wheelTime: Int, crankRevs: Int, crankTime: Int): ByteArray =
    byteArrayOf(0x03) + wheelFrame(wheelRevs, wheelTime).drop(1) + crankFrame(crankRevs, crankTime).drop(1)
