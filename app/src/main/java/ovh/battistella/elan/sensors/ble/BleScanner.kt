// Scan BLE : accès matériel ([LeScanner]) et coordinateur de scan partagé
// ([BleScanner], miroir de `acquireScan` / `releaseScan` dans lib/ble.ts).
//
// La ceinture cardiaque et les capteurs vélo partagent la MÊME radio : un seul
// scan matériel à la fois. Sans coordination, lancer le scan de l'un pendant
// celui de l'autre arrêterait le scan global et laisserait la première carte
// bloquée sur « scan en cours ». Ici, un seul propriétaire à la fois : quand un
// nouveau scan démarre, l'ancien propriétaire est notifié (« scan volé »).
package ovh.battistella.elan.sensors.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Appareil détecté au scan : `id` = adresse MAC, `name` = nom annoncé ou repli. */
data class ScannedDevice(val id: String, val name: String)

/** Nom de repli quand l'annonce ne porte pas de nom. */
const val UNKNOWN_DEVICE_NAME = "Capteur inconnu"

/** Échec du scan matériel (code `ScanCallback.SCAN_FAILED_*`, radio absente…). */
class BleScanException(message: String) : BleException(message)

/**
 * Accès brut au scan matériel. Le flow renvoyé démarre le scan à la collecte,
 * émet chaque annonce reçue (doublons compris) et l'arrête à l'annulation ;
 * il échoue par [BleScanException] si la radio refuse le scan.
 */
interface LeScanner {
    fun scan(serviceUuid: UUID): Flow<ScannedDevice>
}

/**
 * Implémentation sur `BluetoothLeScanner` : filtre par UUID de service et
 * `SCAN_MODE_LOW_LATENCY` (le scan est court et déclenché par l'utilisateur).
 * Permissions vérifiées en amont par [BlePermissionCheck].
 */
@Singleton
@SuppressLint("MissingPermission")
class AndroidLeScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : LeScanner {

    override fun scan(serviceUuid: UUID): Flow<ScannedDevice> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(BleScanException("Bluetooth désactivé."))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toScanned())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toScanned()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanException("Échec du scan Bluetooth ($errorCode)."))
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            close(BleScanException("Permissions Bluetooth refusées."))
            return@callbackFlow
        } catch (e: IllegalStateException) {
            close(BleScanException("Bluetooth désactivé."))
            return@callbackFlow
        }
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
                // radio déjà coupée
            }
        }
    }

    private fun ScanResult.toScanned(): ScannedDevice {
        // Le nom de l'annonce ne demande aucune permission ; `device.name`
        // exige BLUETOOTH_CONNECT (accordée avec BLUETOOTH_SCAN, mais on reste prudent).
        val name = scanRecord?.deviceName
            ?: runCatching { device.name }.getOrNull()
            ?: UNKNOWN_DEVICE_NAME
        return ScannedDevice(device.address, name)
    }
}

/**
 * Coordinateur de scan partagé. [acquire] prend possession de la radio (en
 * volant le scan d'un autre propriétaire) et renvoie le flux des appareils
 * détectés, dédupliqués par adresse ; ce flux se termine quand le scan
 * s'arrête (relâché, volé, ou 20 s écoulées) et échoue si la radio refuse.
 */
@Singleton
class BleScanner @Inject constructor(
    private val le: LeScanner,
    @BleScope private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var owner: Any? = null
    private var onStolen: (() -> Unit)? = null
    private var job: Job? = null

    /** Propriétaire courant du scan, ou `null` (diagnostic / tests). */
    val currentOwner: Any? get() = synchronized(lock) { owner }

    /**
     * Prend possession du scan partagé. Si un autre propriétaire scannait, il
     * est notifié via son `onStolen` et son scan est arrêté au préalable.
     */
    fun acquire(owner: Any, serviceUuid: UUID, onStolen: () -> Unit): Flow<ScannedDevice> {
        synchronized(lock) {
            val previous = this.owner
            if (previous != null && previous !== owner) this.onStolen?.invoke()
            job?.cancel()
            this.owner = owner
            this.onStolen = onStolen

            val channel = Channel<ScannedDevice>(Channel.BUFFERED)
            val scanJob = scope.launch {
                try {
                    withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                        val seen = HashSet<String>()
                        le.scan(serviceUuid).collect { if (seen.add(it.id)) channel.send(it) }
                    }
                    channel.close()
                } catch (e: CancellationException) {
                    channel.close()
                    throw e
                } catch (e: Exception) {
                    channel.close(e)
                }
            }
            scanJob.invokeOnCompletion {
                synchronized(lock) {
                    // Ne libère que si ce scan est encore celui en cours (un
                    // `acquire` plus récent l'a peut-être déjà remplacé).
                    if (job === scanJob) {
                        job = null
                        this.owner = null
                        this.onStolen = null
                    }
                }
            }
            job = scanJob
            return channel.receiveAsFlow()
        }
    }

    /**
     * Relâche le scan et arrête la radio — seulement si `owner` le détient
     * encore (ne coupe pas le scan d'un propriétaire qui l'aurait volé depuis).
     */
    fun release(owner: Any) {
        val toCancel: Job?
        synchronized(lock) {
            if (this.owner !== owner) return
            toCancel = job
            job = null
            this.owner = null
            onStolen = null
        }
        toCancel?.cancel()
    }
}
