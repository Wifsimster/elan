// Source de positions GPS brutes, à ~1 Hz.
//
// L'implémentation Android s'adosse à `LocationManager` (fournisseur GPS,
// sans Play Services : l'app reste 100 % locale). Le service de premier plan
// (`TrackingService`) est ce qui maintient les mises à jour écran éteint ; la
// permission « localisation en arrière-plan » n'est pas requise pour un
// service démarré app visible.
package ovh.battistella.elan.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ovh.battistella.elan.domain.GpsFix
import javax.inject.Inject
import javax.inject.Singleton

interface GpsSource {
    /**
     * Flux froid de fixes bruts : la collecte démarre les mises à jour, son
     * annulation les arrête. Se termine en erreur (`SecurityException`) si la
     * permission de localisation manque au moment de la collecte.
     */
    fun fixes(): Flow<GpsFix>

    /** Le fournisseur GPS est-il activé dans les réglages système ? */
    fun providerEnabled(): Boolean
}

@Singleton
class AndroidGpsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : GpsSource {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // La permission est vérifiée en amont par le contrôleur (LocationPermission) ;
    // ici on laisse la SecurityException fermer le flux plutôt que de la masquer.
    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<GpsFix> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            // Rappel déprécié mais encore invoqué par certains OEM : no-op.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        val manager = locationManager
        try {
            // Cadence régulière à 1 Hz sans filtre de distance natif : le lissage
            // et l'anti-dérive à l'arrêt (GpsConsolidator) ont besoin d'un flux
            // continu d'échantillons.
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                listener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    override fun providerEnabled(): Boolean =
        try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }

    companion object {
        const val MIN_TIME_MS = 1000L
        const val MIN_DISTANCE_M = 0f

        /** `Location` → `GpsFix` : chaque champ optionnel n'est pris que s'il est renseigné. */
        fun Location.toFix(): GpsFix = GpsFix(
            ts = time,
            lat = latitude,
            lon = longitude,
            altitude = if (hasAltitude()) altitude else null,
            accuracy = if (hasAccuracy()) accuracy.toDouble() else null,
            altitudeAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
            // Un Doppler négatif n'a pas de sens : traité comme inconnu.
            speed = if (hasSpeed() && speed >= 0f) speed.toDouble() else null,
        )
    }
}
