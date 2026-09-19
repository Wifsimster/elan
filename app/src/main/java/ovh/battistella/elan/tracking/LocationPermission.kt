// État de la permission de localisation, tel que le suivi d'une sortie le lit.
package ovh.battistella.elan.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class LocationAccess {
    /** Position précise accordée. */
    GRANTED,
    /**
     * Seule la position APPROXIMATIVE est accordée (Android 12+). Inexploitable
     * pour un tracé : chaque fix dépasserait la porte de précision du filtre.
     */
    COARSE,
    DENIED,
}

object LocationPermission {
    fun check(context: Context): LocationAccess {
        if (granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) return LocationAccess.GRANTED
        return if (granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            LocationAccess.COARSE
        } else {
            LocationAccess.DENIED
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
