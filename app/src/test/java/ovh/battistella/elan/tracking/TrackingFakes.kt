package ovh.battistella.elan.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import ovh.battistella.elan.domain.GpsFix
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Source GPS pilotée par le test : `emit` pousse un fix au contrôleur. */
class FakeGpsSource : GpsSource {
    val flow = MutableSharedFlow<GpsFix>()
    var enabled = true
    override fun fixes(): Flow<GpsFix> = flow
    override fun providerEnabled(): Boolean = enabled
}

class FakeHeartRate : HeartRateSamples {
    val flow = MutableSharedFlow<HrFrame>()
    override fun frames(): Flow<HrFrame> = flow
}

class FakeCadence : CadenceSamples {
    val flow = MutableSharedFlow<CscFrame>()
    override fun frames(): Flow<CscFrame> = flow
}

/**
 * Horloge calée sur le temps virtuel de l'ordonnanceur de test : `advanceTimeBy`
 * fait avancer le chrono et les horodatages de la même façon.
 */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val baseMs: Long,
) : Clock() {
    override fun millis(): Long = baseMs + scheduler.currentTime
    override fun instant(): Instant = Instant.ofEpochMilli(millis())
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}

/** Degrés de latitude pour un mètre, à l'équateur comme à Paris (nord-sud). */
const val DEG_PER_METER_LAT = 1.0 / 111_320.0

/** Un fix sur une ligne plein nord depuis (48.8566, 2.3522), à `northM` mètres du départ. */
fun fixAt(
    ts: Long,
    northM: Double,
    speedMs: Double? = 5.0,
    accuracy: Double? = 5.0,
    altitude: Double? = 35.0,
) = GpsFix(
    ts = ts,
    lat = 48.8566 + northM * DEG_PER_METER_LAT,
    lon = 2.3522,
    altitude = altitude,
    accuracy = accuracy,
    altitudeAccuracy = 3.0,
    speed = speedMs,
)
