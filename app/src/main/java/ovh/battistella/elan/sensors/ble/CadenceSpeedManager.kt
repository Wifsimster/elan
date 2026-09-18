// Capteurs vélo BLE « Cycling Speed and Cadence » (profil GATT CSC) : cadence
// (pédalier) et vitesse (roue). Gère un ou deux capteurs simultanés —
// typiquement un iGPSPORT CAD70 (cadence) + un SPD70 (vitesse), deux
// périphériques distincts (miroir de `CadenceSpeedProvider`).
//
// Comme [HeartRateManager], toute la machine d'états tourne sur [BleScope]
// (parallélisme 1) ; les appels publics y postent du travail.
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
    /** Cadence instantanée en tours/min, `null` sans capteur de cadence. */
    val cadenceRpm: Int? = null,
    /** Vitesse roue instantanée en km/h, `null` sans capteur de vitesse. */
    val speedKmh: Double? = null,
    /** Capteurs actuellement connectés. */
    val devices: List<ScannedDevice> = emptyList(),
    /** Capteurs détectés pendant le scan (hors capteurs déjà connectés). */
    val scanned: List<ScannedDevice> = emptyList(),
    val error: String? = null,
    /** Circonférence de roue en mm (convertit les tours en distance). */
    val wheelCircumferenceMm: Int = DEFAULT_WHEEL_MM,
)

/** 700×25c, valeur par défaut courante. */
const val DEFAULT_WHEEL_MM = 2105

/** Nombre max de capteurs CSC simultanés. */
const val MAX_CSC_DEVICES = 2

/** Unité de temps CSC : 1/1024 s. */
internal const val TIME_UNIT = 1024.0

/** Au-delà, on considère le capteur silencieux et on retombe à zéro. */
internal const val STALE_MS = 3_000L

/** Cadence max plausible (tr/min) ; au-delà, la mesure est ignorée. */
internal const val MAX_RPM = 250

/** Vitesse max plausible (km/h) ; au-delà, la mesure est ignorée. */
internal const val MAX_KMH = 120.0

/** Différence de compteurs cumulés avec bouclage (`mod` = 2¹⁶ ou 2³²). */
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

    /**
     * Mesures brutes : une émission par trame exploitable, même à valeur
     * identique (nécessaire pour ne pas perdre d'échantillons sur un palier
     * de cadence/vitesse).
     */
    private val _frames = MutableSharedFlow<CscFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun frames(): SharedFlow<CscFrame> = _frames.asSharedFlow()

    // ---- état interne, touché uniquement depuis `scope` ------------------

    private class Connection(val link: GattLink, val name: String) {
        var notifyJob: Job? = null
        var lostJob: Job? = null
    }

    private val connections = LinkedHashMap<String, Connection>()

    /** Dernière mesure brute par capteur (pour les deltas). */
    private val lastRaw = HashMap<String, CscRaw>()

    /** Connexions en vol (garde anti-double-connexion, par capteur). */
    private val connecting = HashSet<String>()

    /** Capteurs déconnectés volontairement : reconnexion auto désarmée. */
    private val intentional = HashSet<String>()
    private val reconnectJobs = HashMap<String, Job>()
    private val reconnectAttempts = HashMap<String, Int>()

    private var scanJob: Job? = null
    private var staleJob: Job? = null
    private var crankMoveTs = 0L

    /** Circonférence choisie par l'utilisateur avant la fin du chargement initial : ne pas l'écraser. */
    private var wheelSetByUser = false
    private var wheelMoveTs = 0L

    init {
        if (supported) {
            scope.launch {
                // Circonférence mémorisée, puis reconnexion pilotée par l'adaptateur.
                val mm = SettingsJson.parseWheelMm(settings.getSetting(SettingsRepository.Keys.CSC_WHEEL_MM))
                if (!wheelSetByUser) _state.update { it.copy(wheelCircumferenceMm = mm.roundToInt()) }
                adapter.states.collect { on -> if (on) onAdapterOn() else onAdapterOff() }
            }
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

    /** Connecte un capteur et le mémorise (`csc_devices`). */
    fun connect(deviceId: String) {
        if (!supported) return
        scope.launch { doConnect(deviceId) }
    }

    /** Déconnecte un capteur et l'oublie. */
    fun disconnect(deviceId: String) {
        if (!supported) return
        scope.launch { doDisconnect(listOf(deviceId)) }
    }

    /** Déconnecte et oublie tous les capteurs. */
    fun disconnectAll() {
        if (!supported) return
        scope.launch { doDisconnect(connections.keys.toList()) } // lu sur `scope`, pas ici
    }

    /** Alias de [connect] : vocabulaire « liste de capteurs » des réglages. */
    fun addDevice(deviceId: String) = connect(deviceId)

    /** Alias de [disconnect]. */
    fun removeDevice(deviceId: String) = disconnect(deviceId)

    fun setWheelCircumference(mm: Int) {
        scope.launch {
            wheelSetByUser = true
            _state.update { it.copy(wheelCircumferenceMm = mm) }
            settings.setCscWheelMm(mm.toDouble())
        }
    }

    // ---- scan ------------------------------------------------------------

    private fun doStartScan() {
        _state.update { it.copy(error = null) }
        if (!permissions.granted()) {
            _state.update { it.copy(status = SensorStatus.Error, error = PERMISSIONS_DENIED) }
            return
        }
        scanJob?.cancel()
        // Coordination du scan partagé : notifie/arrête le scan de la ceinture
        // s'il tournait ; « scan volé » nous ramène à l'arrêt dans le cas inverse.
        val flow = scanner.acquire(this, BleUuids.CSC_SERVICE) { restIfScanning() }
        _state.update { it.copy(status = SensorStatus.Scanning, scanned = emptyList()) }
        scanJob = scope.launch {
            try {
                flow.collect { dev ->
                    if (!connections.containsKey(dev.id)) _state.update { it.copy(scanned = it.scanned + dev) }
                }
                restIfScanning() // arrêt automatique (20 s) ou scan volé
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

    /** Statut de repos : `Connected` si au moins un capteur est relié, sinon `Idle`. */
    private fun restingStatus(): SensorStatus =
        if (connections.isNotEmpty()) SensorStatus.Connected else SensorStatus.Idle

    private fun restIfScanning() {
        val resting = restingStatus()
        _state.update { if (it.status == SensorStatus.Scanning) it.copy(status = resting) else it }
    }

    /** Recalcule `devices` et le statut à partir des connexions actives. */
    private fun syncDevices() {
        val devices = connections.values.map { ScannedDevice(it.link.address, it.name) }
        _state.update { it.copy(devices = devices, status = restingStatus()) }
        if (connections.isEmpty()) stopStaleWatch() else startStaleWatch()
    }

    // ---- connexion -------------------------------------------------------

    private suspend fun doConnect(deviceId: String) {
        // Garde anti-double-connexion : déjà connecté OU tentative en vol.
        if (connections.containsKey(deviceId) || deviceId in connecting) return
        if (connections.size >= MAX_CSC_DEVICES) {
            _state.update { it.copy(error = "Deux capteurs maximum.") }
            return
        }
        connecting += deviceId
        _state.update { it.copy(error = null) }
        // Tentative volontaire : réarme la reconnexion auto et annule une
        // tentative différée pour ce capteur.
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

            val conn = Connection(l, l.name ?: "Capteur vélo")
            connections[deviceId] = conn
            conn.lostJob = scope.launch {
                val status = l.disconnected.first()
                onLost(deviceId, l, status)
            }
            conn.notifyJob = scope.launch { frames.collect { onFrame(deviceId, it) } }
            reconnectAttempts.remove(deviceId) // connexion établie
            connecting -= deviceId
            syncDevices()
            persistDevices()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: CONNECT_FAILED) }
            // Découverte/notification échouée (GATT 133) : ferme le GATT
            // semi-connecté, sinon l'appareil devient indétectable au scan.
            candidate?.close()
            connecting -= deviceId
            // Replanifie tant que le budget de tentatives n'est pas épuisé.
            scheduleReconnect(deviceId)
        }
    }

    @Suppress("UNUSED_PARAMETER") // statut GATT : utile au débogage, pas exposé
    private fun onLost(deviceId: String, l: GattLink, status: Int) {
        val conn = connections[deviceId] ?: return
        if (conn.link !== l) return
        dropConnection(deviceId)
        scheduleReconnect(deviceId) // coupure involontaire : back-off borné
    }

    /** Retire une connexion (jobs, lien, dernière mesure) ; remet les mesures à `null` si plus aucun capteur. */
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
            // Déconnexion volontaire : désarme la reconnexion auto de ce capteur.
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

    // ---- mesures ---------------------------------------------------------

    /** Traite une mesure CSC d'un capteur donné et met à jour cadence/vitesse. */
    private fun onFrame(deviceId: String, bytes: ByteArray) {
        val raw = parseCsc(bytes) ?: return
        val prev = lastRaw.put(deviceId, raw) ?: return // il faut deux mesures pour un delta
        val now = clock.millis()
        var cadence = _state.value.cadenceRpm
        var speed = _state.value.speedKmh
        var updated = false

        // Cadence (pédalier).
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

        // Vitesse (roue).
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
        // Notifie à chaque trame exploitable (même valeur égale).
        _frames.tryEmit(CscFrame(now, cadence, speed))
    }

    /** Retombe à zéro quand un capteur cesse d'émettre (arrêt prolongé). */
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

    // ---- adaptateur Bluetooth --------------------------------------------

    private fun onAdapterOff() {
        _state.update {
            val status = when (it.status) {
                SensorStatus.Connected, SensorStatus.Connecting, SensorStatus.Reconnecting -> SensorStatus.Error
                else -> it.status
            }
            it.copy(status = status, cadenceRpm = null, speedKmh = null, error = BLUETOOTH_OFF)
        }
    }

    /**
     * BT allumé (ou déjà allumé au lancement) : reconnexion aux capteurs
     * mémorisés, en PARALLÈLE pour ne pas cumuler les délais capteur par capteur.
     */
    private suspend fun onAdapterOn() {
        _state.update { it.copy(error = null) }
        val saved = SettingsJson.parseCscDevices(settings.getSetting(SettingsRepository.Keys.CSC_DEVICES))
        for (d in saved) {
            reconnectAttempts.remove(d.id)
            scope.launch { doConnect(d.id) }
        }
    }
}
