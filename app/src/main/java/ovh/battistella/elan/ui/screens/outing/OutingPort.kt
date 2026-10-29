package ovh.battistella.elan.ui.screens.outing

import kotlinx.coroutines.flow.StateFlow
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.GeoPoint
import ovh.battistella.elan.domain.GpsStatus

enum class OutingPhase { Idle, Requesting, Active, Paused, Saving, SaveFailed }

data class OutingUi(
    val phase: OutingPhase = OutingPhase.Idle,
    val type: ActivityType = ActivityType.VELO,
    val sessionId: Long? = null,
    val elapsedSec: Int = 0,
    val distanceM: Double = 0.0,
    val speedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val elevationGainM: Double = 0.0,
    val pointCount: Int = 0,
    val accuracyM: Double? = null,
    val gpsStatus: GpsStatus = GpsStatus.IDLE,
    val livePath: List<GeoPoint> = emptyList(),
    val bpm: Int? = null,
    val cadenceRpm: Int? = null,
    val wheelSpeedKmh: Double? = null,
    val caloriesLive: Double = 0.0,
    val errorMessage: String? = null,
    val savedSessionId: Long? = null,
)

interface OutingPort {
    val state: StateFlow<OutingUi>

    fun begin(type: ActivityType)
    fun pause()
    fun resume()

    fun finish()

    fun retrySave()

    fun discard()
}
