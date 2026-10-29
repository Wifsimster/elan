package ovh.battistella.elan.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class LocationAccess {
    GRANTED,
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
