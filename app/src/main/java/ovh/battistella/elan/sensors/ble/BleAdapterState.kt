// État de l'adaptateur Bluetooth (miroir de `manager.onStateChange` dans les
// hooks d'origine) : les gestionnaires y réagissent pour couper les mesures
// quand le Bluetooth s'éteint et relancer la reconnexion quand il se rallume.
package ovh.battistella.elan.sensors.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

interface BleAdapterState {
    /** `false` sans radio BLE : les gestionnaires restent en `Unsupported`. */
    val supported: Boolean

    /** État courant de l'adaptateur. */
    val enabled: Boolean

    /**
     * Émet l'état courant à la souscription puis chaque bascule on/off
     * (équivalent de `onStateChange(cb, emitCurrentState = true)`).
     */
    val states: Flow<Boolean>
}

@Singleton
class AndroidBleAdapterState @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleAdapterState {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    override val supported: Boolean =
        adapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    override val enabled: Boolean
        get() = adapter?.isEnabled == true

    override val states: Flow<Boolean> = callbackFlow {
        trySend(enabled)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> trySend(true)
                    // « Turning off » compte déjà comme éteint : les liens GATT
                    // tombent à cet instant, autant couper les mesures tout de suite.
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> trySend(false)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
