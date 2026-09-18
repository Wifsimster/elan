// Permissions Bluetooth (miroir de `requestBlePermissions` dans lib/ble.ts).
// La DEMANDE se fait depuis l'écran (elle exige une Activity) ; les
// gestionnaires ne font que VÉRIFIER, via [BlePermissionCheck] injectable.
package ovh.battistella.elan.sensors.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BlePermissions {
    /**
     * Permissions d'exécution requises pour scanner et se connecter :
     * Android 12+ (API 31) `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`, avant
     * `ACCESS_FINE_LOCATION` (le scan BLE révèle la position).
     */
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** `true` si toutes les permissions de [required] sont accordées. */
    fun granted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Vérification injectable (les tests la remplacent par une constante). */
fun interface BlePermissionCheck {
    fun granted(): Boolean
}
