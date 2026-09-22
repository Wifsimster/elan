// Ceinture cardiaque : une seule connexion BLE partagée par les écrans de
// séance et les réglages (miroir de `HeartRateProvider` dans use-heart-rate.tsx).
//
// Toute la machine d'états tourne sur la portée [BleScope], à parallélisme 1 :
// les appels publics ne font que poster du travail dessus, ce qui remplace les
// refs et la boucle d'événements JS d'origine sans verrou explicite.
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
    /** Dernière FC valide, `null` hors connexion ou contact perdu. */
    val bpm: Int? = null,
    /** Ceinture connectée. */
    val device: ScannedDevice? = null,
    /** Ceintures détectées pendant le scan (vidée à la connexion). */
    val scanned: List<ScannedDevice> = emptyList(),
    val error: String? = null,
)

/** Reconnexion auto : nombre max de tentatives après une coupure involontaire. */
internal const val MAX_RECONNECT_ATTEMPTS = 5

/** Délai avant la tentative `attempt` (0-indexée) : 1 s, 2 s, 4 s, 8 s, 15 s. */
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

    /**
     * Trames brutes : une émission par trame BLE reçue, **y compris quand la
     * valeur est identique à la précédente** (palier cardiaque). Un abonné à
     * `state.bpm` raterait ces trames (StateFlow déduplique) et appauvrirait
     * la moyenne et l'appariement FC ↔ point GPS.
     */
    private val _frames = MutableSharedFlow<HrFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun frames(): SharedFlow<HrFrame> = _frames.asSharedFlow()

    // ---- état interne, touché uniquement depuis `scope` ------------------

    private var link: GattLink? = null
    private var notifyJob: Job? = null
    private var lostJob: Job? = null
    private var scanJob: Job? = null

    /** Garde anti-double-connexion (tentative déjà en vol). */
    private var connecting = false

    /** Déconnexion volontaire : désarme la reconnexion auto. */
    private var userDisconnected = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    init {
        if (supported) {
            // L'état courant est émis à la souscription : il déclenche la
            // reconnexion initiale au lancement ; un cycle off→on la rejoue.
            scope.launch { adapter.states.collect { on -> if (on) onAdapterOn() else onAdapterOff() } }
        }
    }

    // ---- API publique ----------------------------------------------------

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

    // ---- scan ------------------------------------------------------------

    private fun doStartScan() {
        _state.update { it.copy(error = null) }
        if (!permissions.granted()) {
            _state.update { it.copy(status = SensorStatus.Error, error = PERMISSIONS_DENIED) }
            return
        }
        scanJob?.cancel()
        _state.update { it.copy(status = SensorStatus.Scanning, scanned = emptyList()) }
        // Coordination du scan partagé : si les capteurs vélo scannaient, ils
        // sont notifiés et arrêtés ; « scan volé » nous ramène à l'arrêt si
        // l'inverse se produit (au lieu de rester bloqué sur « scan en cours »).
        val flow = scanner.acquire(this, BleUuids.HEART_RATE_SERVICE) { idleIfScanning() }
        scanJob = scope.launch {
            try {
                flow.collect { dev -> _state.update { it.copy(scanned = it.scanned + dev) } }
                idleIfScanning() // arrêt automatique (20 s) ou scan volé
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

    /** Fin de scan : retour à `Connected` si une ceinture est reliée, sinon `Idle`. */
    private fun idleIfScanning() {
        val resting = if (link != null) SensorStatus.Connected else SensorStatus.Idle
        _state.update { if (it.status == SensorStatus.Scanning) it.copy(status = resting) else it }
    }

    // ---- connexion -------------------------------------------------------

    private suspend fun doConnect(deviceId: String) {
        // Une tentative en vol, ou une connexion au même appareil, ne doit pas
        // en lancer une seconde (deux moniteurs mêleraient leurs valeurs). Une
        // connexion à un AUTRE appareil déconnecte d'abord le précédent.
        if (connecting) return
        if (link?.address == deviceId) return
        connecting = true
        link?.let { previous ->
            link = null
            cancelLinkJobs()
            previous.close()
        }
        _state.update { it.copy(error = null) }
        // Tentative volontaire : réarme la reconnexion auto et annule une
        // tentative différée éventuellement en cours.
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
                // « Déconnecter » touché pendant la connexion : on n'y donne pas suite.
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
                // La liste de scan est devenue obsolète : ne plus la laisser tapable.
                it.copy(status = SensorStatus.Connected, device = info, scanned = emptyList())
            }
            reconnectAttempts = 0 // connexion établie : compteur remis à zéro
            connecting = false
            settings.setHrDevice(BleDevice(info.id, info.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: CONNECT_FAILED) }
            link = null
            cancelLinkJobs()
            // Découverte/notification échouée après connexion (GATT 133
            // fréquent) : l'appareil resterait semi-connecté et indétectable au
            // scan → on force la fermeture GATT.
            candidate?.close()
            connecting = false
            // Replanifie tant que le budget de tentatives n'est pas épuisé.
            scheduleReconnect(deviceId)
        }
    }

    private fun onFrame(bytes: ByteArray) {
        val bpm = parseHeartRate(bytes) ?: return // trame invalide ou FC 0 (contact perdu)
        _state.update { it.copy(bpm = bpm) }
        _frames.tryEmit(HrFrame(clock.millis(), bpm))
    }

    /** Coupure involontaire (capteur hors de portée, etc.) : back-off borné. */
    @Suppress("UNUSED_PARAMETER") // statut GATT : utile au débogage, pas exposé
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
            // Le Bluetooth est coupé : inutile d'insister, la reconnexion
            // repartira (compteur à zéro) quand il sera rallumé.
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
        // Déconnexion volontaire : désarme la reconnexion auto et annule une
        // tentative en attente AVANT de fermer le lien.
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

    // ---- adaptateur Bluetooth --------------------------------------------

    private fun onAdapterOff() {
        _state.update {
            val status = when (it.status) {
                SensorStatus.Connected, SensorStatus.Connecting, SensorStatus.Reconnecting -> SensorStatus.Error
                else -> it.status
            }
            it.copy(status = status, bpm = null, error = BLUETOOTH_OFF)
        }
    }

    /** BT allumé (ou déjà allumé au lancement) : reconnexion au dernier appareil mémorisé. */
    private suspend fun onAdapterOn() {
        _state.update { it.copy(error = null) }
        if (link != null || connecting) return
        val saved = SettingsJson.parseHrDevice(settings.getSetting(SettingsRepository.Keys.HR_DEVICE)) ?: return
        reconnectAttempts = 0
        // Dans une coroutine à part : la tentative peut durer 10 s et ne doit
        // pas retarder la lecture des bascules suivantes de l'adaptateur.
        scope.launch { doConnect(saved.id) }
    }
}
