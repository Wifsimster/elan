// Lien GATT vers un périphérique : abstraction ([GattLink]) et implémentation
// sur `BluetoothGatt` ([AndroidGattLink]). Remplace `connectToDevice` +
// `discoverAllServicesAndCharacteristics` + `monitorCharacteristicForService`
// de react-native-ble-plx, sans bibliothèque tierce.
package ovh.battistella.elan.sensors.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Erreur BLE lisible (le message est affiché tel quel dans les réglages). */
open class BleException(message: String) : Exception(message)

/** Opération GATT terminée avec un statut d'erreur (133 = `GATT_ERROR`, fréquent). */
class GattException(val status: Int) : BleException("Erreur GATT $status.")

/** Connexion ou opération GATT sans réponse dans le délai imparti. */
class BleTimeoutException : BleException("Délai de connexion dépassé.")

/**
 * Un lien GATT vers UN périphérique. Cycle : [connect] → [discoverServices] →
 * [enableNotifications] ; [close] libère toujours le client GATT (obligatoire
 * sur Android, sinon l'appareil reste semi-connecté et invisible au scan).
 */
interface GattLink {
    /** Adresse MAC du périphérique. */
    val address: String

    /** Nom du périphérique tel que connu du système, ou `null`. */
    val name: String?

    /** Ouvre la connexion (`autoConnect = false`, transport LE) ; échoue au-delà de `timeoutMs`. */
    suspend fun connect(timeoutMs: Long = CONNECT_TIMEOUT_MS)

    suspend fun discoverServices()

    /**
     * Active les notifications d'une caractéristique (`setCharacteristicNotification`
     * + écriture du CCCD) et renvoie le flux de ses valeurs brutes, une émission
     * par trame reçue, doublons compris.
     */
    suspend fun enableNotifications(service: UUID, characteristic: UUID): Flow<ByteArray>

    /** Coupures APRÈS connexion établie, avec le statut GATT. */
    val disconnected: Flow<Int>

    /** Ferme le client GATT ; idempotent, jamais bloquant. */
    fun close()
}

interface GattLinkFactory {
    fun create(address: String): GattLink
}

/** Délai max d'une opération GATT hors connexion (découverte, écriture CCCD). */
private const val OP_TIMEOUT_MS = 8_000L

/**
 * Implémentation sur `BluetoothGatt`. Les opérations sont SÉRIALISÉES (une à
 * la fois, la pile Bluetooth ignore une seconde requête en vol) et les rappels
 * sont livrés sur un unique `HandlerThread` plutôt que sur le thread binder.
 * Permissions vérifiées en amont par [BlePermissionCheck].
 */
@SuppressLint("MissingPermission")
class AndroidGattLink(
    private val context: Context,
    private val device: BluetoothDevice,
    private val handler: Handler,
) : GattLink {

    override val address: String = device.address

    override val name: String?
        get() = runCatching { device.name }.getOrNull()

    private val ops = Mutex()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var connected = false

    /** Opération en vol : complétée avec le statut GATT par le rappel correspondant. */
    @Volatile
    private var pending: CancellableContinuation<Int>? = null

    private val _disconnected = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    override val disconnected: Flow<Int> = _disconnected

    private val notifications = MutableSharedFlow<Pair<UUID, ByteArray>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true
                complete(status)
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTING) return
            // Déconnexion ou échec de connexion : on libère le client (sinon
            // le périphérique reste semi-connecté) et on signale la coupure.
            val wasConnected = connected
            connected = false
            closeGatt()
            fail(if (status == BluetoothGatt.GATT_SUCCESS) BleException("Connexion perdue.") else GattException(status))
            if (wasConnected) _disconnected.tryEmit(status)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = complete(status)

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
            complete(status)

        // API 33+ : la valeur est fournie en paramètre (la lecture différée de
        // `characteristic.value` n'est plus sûre). Ne PAS appeler super : la
        // version par défaut relaie vers la surcharge dépréciée ci-dessous.
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            notifications.tryEmit(characteristic.uuid to value)
        }

        @Deprecated("Rappel d'avant API 33")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            notifications.tryEmit(characteristic.uuid to value.copyOf())
        }
    }

    override suspend fun connect(timeoutMs: Long) {
        try {
            await(timeoutMs) {
                gatt = device.connectGatt(
                    context,
                    false, // autoConnect : une connexion directe, bornée par le délai
                    callback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK,
                    handler,
                )
                gatt != null
            }
        } catch (e: Exception) {
            closeGatt()
            throw e
        }
    }

    override suspend fun discoverServices() {
        val g = gatt ?: throw BleException("Non connecté.")
        await(OP_TIMEOUT_MS) { g.discoverServices() }
    }

    override suspend fun enableNotifications(service: UUID, characteristic: UUID): Flow<ByteArray> {
        val g = gatt ?: throw BleException("Non connecté.")
        val ch = g.getService(service)?.getCharacteristic(characteristic)
            ?: throw BleException("Caractéristique introuvable.")
        val cccd = ch.getDescriptor(BleUuids.CCCD)
            ?: throw BleException("Descripteur de notification introuvable.")
        await(OP_TIMEOUT_MS) {
            g.setCharacteristicNotification(ch, true) &&
                writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
        return notifications.filter { it.first == characteristic }.map { it.second }
    }

    override fun close() {
        connected = false
        closeGatt()
        fail(BleException("Connexion fermée."))
    }

    // ---- interne ---------------------------------------------------------

    /**
     * Lance une opération GATT et attend son rappel. Sérialisé par [ops] ;
     * `issue` renvoie `false` si la pile a refusé la requête (aucun rappel ne
     * viendra). Un statut GATT non nul devient une [GattException].
     */
    private suspend fun await(timeoutMs: Long, issue: () -> Boolean) {
        ops.withLock {
            val status = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Int> { cont ->
                    pending = cont
                    cont.invokeOnCancellation { if (pending === cont) pending = null }
                    val accepted = try {
                        issue()
                    } catch (e: Exception) {
                        false
                    }
                    if (!accepted && pending === cont) {
                        pending = null
                        cont.resumeWithException(BleException("Opération GATT refusée."))
                    }
                }
            } ?: throw BleTimeoutException()
            if (status != BluetoothGatt.GATT_SUCCESS) throw GattException(status)
        }
    }

    private fun complete(status: Int) {
        val cont = pending ?: return
        pending = null
        cont.resume(status)
    }

    private fun fail(e: Exception) {
        val cont = pending ?: return
        pending = null
        cont.resumeWithException(e)
    }

    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        try {
            g.disconnect()
        } catch (_: Exception) {
            // déjà coupé
        }
        try {
            g.close()
        } catch (_: Exception) {
            // déjà fermé
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            writeDescriptorLegacy(g, d, value)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptorLegacy(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        d.setValue(value) && g.writeDescriptor(d)
}

/** Un `HandlerThread` unique pour tous les liens : les rappels GATT y sont livrés dans l'ordre. */
@Singleton
class AndroidGattLinkFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : GattLinkFactory {

    private val handler: Handler by lazy {
        Handler(HandlerThread("elan-ble").apply { start() }.looper)
    }

    override fun create(address: String): GattLink {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw BleException("Bluetooth indisponible.")
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw BleException("Adresse Bluetooth invalide.")
        }
        return AndroidGattLink(context, device, handler)
    }
}
