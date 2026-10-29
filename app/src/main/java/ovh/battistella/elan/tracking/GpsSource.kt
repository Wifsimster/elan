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
    fun fixes(): Flow<GpsFix>

    fun providerEnabled(): Boolean
}

@Singleton
class AndroidGpsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : GpsSource {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<GpsFix> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        val manager = locationManager
        try {
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

        fun Location.toFix(): GpsFix = GpsFix(
            ts = time,
            lat = latitude,
            lon = longitude,
            altitude = if (hasAltitude()) altitude else null,
            accuracy = if (hasAccuracy()) accuracy.toDouble() else null,
            altitudeAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
            speed = if (hasSpeed() && speed >= 0f) speed.toDouble() else null,
        )
    }
}
