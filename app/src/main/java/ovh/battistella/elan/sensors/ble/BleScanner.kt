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

data class ScannedDevice(val id: String, val name: String)

const val UNKNOWN_DEVICE_NAME = "Capteur inconnu"

class BleScanException(message: String) : BleException(message)

interface LeScanner {
    fun scan(serviceUuid: UUID): Flow<ScannedDevice>
}

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
            }
        }
    }

    private fun ScanResult.toScanned(): ScannedDevice {
        val name = scanRecord?.deviceName
            ?: runCatching { device.name }.getOrNull()
            ?: UNKNOWN_DEVICE_NAME
        return ScannedDevice(device.address, name)
    }
}

@Singleton
class BleScanner @Inject constructor(
    private val le: LeScanner,
    @BleScope private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var owner: Any? = null
    private var onStolen: (() -> Unit)? = null
    private var job: Job? = null

    val currentOwner: Any? get() = synchronized(lock) { owner }

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
