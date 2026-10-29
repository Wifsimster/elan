package ovh.battistella.elan.tracking

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.repository.TrackPointInput
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.di.ApplicationScope
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.ConsolidatedPoint
import ovh.battistella.elan.domain.GpsConsolidator
import ovh.battistella.elan.domain.GpsFix
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.domain.HrSample
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Timestamped
import ovh.battistella.elan.domain.estimateCalories
import ovh.battistella.elan.domain.haversineMeters
import ovh.battistella.elan.domain.movingTimeSec
import ovh.battistella.elan.domain.nearestSample
import ovh.battistella.elan.domain.pushDownsampled
import ovh.battistella.elan.domain.summarizeCadence
import ovh.battistella.elan.domain.summarizeHr
import java.time.Clock
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

enum class OutingPhase {
    IDLE,
    REQUESTING,
    ACTIVE,
    PAUSED,
    SAVING,
    SAVE_FAILED,
}

enum class BeginResult {
    STARTED,
    DENIED,
    COARSE,
    FAILED,
    BUSY,
}

data class OutingError(val title: String, val message: String)

data class OutingState(
    val phase: OutingPhase = OutingPhase.IDLE,
    val type: ActivityType = ActivityType.VELO,
    val sessionId: Long? = null,
    val startedAt: Long? = null,
    val elapsedSec: Int = 0,
    val distanceM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val pointCount: Int = 0,
    val accuracyM: Double? = null,
    val gpsStatus: GpsStatus = GpsStatus.IDLE,
    val livePath: List<ConsolidatedPoint> = emptyList(),
    val bpm: Int? = null,
    val cadenceRpm: Int? = null,
    val wheelSpeedKmh: Double? = null,
    val caloriesLive: Double = 0.0,
    val error: OutingError? = null,
    val savedSessionId: Long? = null,
) {
    val isLive: Boolean get() = phase == OutingPhase.ACTIVE || phase == OutingPhase.PAUSED

    val errorMessage: String? get() = error?.message
}

data class GpsResult(
    val points: List<ConsolidatedPoint>,
    val distanceM: Double,
    val speedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainM: Double,
    val pointCount: Int,
    val accuracyM: Double?,
)

data class CadenceSample(override val ts: Long, val cadence: Double) : Timestamped

@Singleton
class TrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val gps: GpsSource,
    heartRate: Optional<HeartRateSamples>,
    cadence: Optional<CadenceSamples>,
    private val finalizer: SessionFinalizer,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(OutingState())
    val state: StateFlow<OutingState> = _state.asStateFlow()

    private val stopwatch = Stopwatch(clock, scope)

    private val mutex = Mutex()

    private var consolidator: GpsConsolidator? = null
    private val points = ArrayList<ConsolidatedPoint>()
    private var flushedCount = 0
    private val livePath = ArrayList<ConsolidatedPoint>()
    private var lastLive: ConsolidatedPoint? = null
    private var lastLiveEmitTs = 0L
    private val hrSamples = ArrayList<HrSample>()
    private val cadenceSamples = ArrayList<CadenceSample>()
    private var frozen: GpsResult? = null
    private var profile = Profile()

    private val flushMutex = Mutex()
    private var gpsJob: Job? = null
    private var flushJob: Job? = null

    init {
        scope.launch {
            stopwatch.elapsedSec.collect { sec -> _state.update { withLiveCalories(it.copy(elapsedSec = sec)) } }
        }
        scope.launch {
            heartRate.orElse(HeartRateSamples.None).frames()
                .catch { Log.w(TAG, "flux FC interrompu", it) }
                .collect { onHrFrame(it) }
        }
        scope.launch {
            cadence.orElse(CadenceSamples.None).frames()
                .catch { Log.w(TAG, "flux cadence interrompu", it) }
                .collect { onCscFrame(it) }
        }
    }

    suspend fun begin(type: ActivityType): BeginResult = scope.async { beginNow(type) }.await()

    private suspend fun beginNow(type: ActivityType): BeginResult = mutex.withLock {
        if (_state.value.phase != OutingPhase.IDLE) return@withLock BeginResult.BUSY
        _state.update { it.fresh(type = type, phase = OutingPhase.REQUESTING, gpsStatus = GpsStatus.REQUESTING) }

        when (LocationPermission.check(context)) {
            LocationAccess.DENIED -> {
                _state.update { it.fresh(type = type, gpsStatus = GpsStatus.DENIED, error = ERROR_DENIED) }
                return@withLock BeginResult.DENIED
            }
            LocationAccess.COARSE -> {
                _state.update { it.fresh(type = type, gpsStatus = GpsStatus.DENIED, error = ERROR_COARSE) }
                return@withLock BeginResult.COARSE
            }
            LocationAccess.GRANTED -> Unit
        }

        profile = try {
            settings.getProfile()
        } catch (e: Exception) {
            Profile()
        }

        val startedAt = clock.millis()
        val id = try {
            sessions.createSession(type, startedAt)
        } catch (e: Exception) {
            Log.w(TAG, "création de séance impossible", e)
            _state.update { it.fresh(type = type, error = ERROR_START) }
            return@withLock BeginResult.FAILED
        }

        resetBuffersLocked()
        stopwatch.reset()
        stopwatch.start()
        _state.update {
            it.fresh(
                type = type,
                phase = OutingPhase.ACTIVE,
                gpsStatus = GpsStatus.TRACKING,
                sessionId = id,
                startedAt = startedAt,
            )
        }
        startForegroundService()
        gpsJob = scope.launch { collectGps() }
        flushJob = scope.launch { flushLoop() }
        BeginResult.STARTED
    }

    suspend fun pause() = scope.launch {
        val paused = mutex.withLock {
            if (_state.value.phase != OutingPhase.ACTIVE) return@withLock false
            stopwatch.pause()
            flushJob?.cancel()
            flushJob = null
            _state.update { it.copy(phase = OutingPhase.PAUSED) }
            true
        }
        if (paused) flush()
    }.join()

    suspend fun resume() = scope.launch {
        mutex.withLock {
            if (_state.value.phase != OutingPhase.PAUSED) return@withLock
            stopwatch.start()
            _state.update { it.copy(phase = OutingPhase.ACTIVE) }
            flushJob = scope.launch { flushLoop() }
        }
    }.join()

    suspend fun finish(): Long? = scope.async { save(from = setOf(OutingPhase.ACTIVE, OutingPhase.PAUSED)) }.await()

    suspend fun retrySave(): Long? = scope.async { save(from = setOf(OutingPhase.SAVE_FAILED)) }.await()

    suspend fun discard() = scope.launch {
        val id = mutex.withLock {
            val s = _state.value
            if (s.phase == OutingPhase.IDLE || s.phase == OutingPhase.SAVING) return@withLock null
            stopGpsLocked()
            stopwatch.reset()
            clearOutingLocked()
            _state.update { it.fresh(type = s.type) }
            s.sessionId
        }
        if (id != null) {
            try {
                sessions.deleteSession(id)
            } catch (e: Exception) {
                Log.w(TAG, "suppression de la séance abandonnée impossible", e)
            }
        }
    }.join()

    suspend fun takeUnflushed(): List<ConsolidatedPoint> = mutex.withLock { takeUnflushedLocked() }

    private class SavePrep(
        val id: Long,
        val patch: SessionUpdate,
        val points: List<TrackPointInput>,
        val data: SavedSessionData,
    )

    private suspend fun save(from: Set<OutingPhase>): Long? {
        val prep = mutex.withLock {
            val s = _state.value
            if (s.phase !in from) return@withLock null
            val id = s.sessionId ?: return@withLock null
            _state.update { it.copy(phase = OutingPhase.SAVING, error = null) }

            val result = frozen ?: stopGpsLocked()
            frozen = result
            flushedCount = points.size
            stopwatch.pause()
            val durationSec = stopwatch.getElapsedSec()
            val (avgHr, maxHr) = summarizeHr(hrSamples)
            val (avgCadence, maxCadence) = summarizeCadence(cadenceSamples.map { it.cadence })
            val moving = min(movingTimeSec(result.points), durationSec)
            val effectiveSec = if (moving > 0) moving else durationSec
            val avgSpeedKmh = if (effectiveSec > 0) result.distanceM / 1000 / (effectiveSec / 3600.0) else 0.0
            val calories = estimateCalories(
                type = s.type,
                weightKg = profile.weightKg,
                durationSec = effectiveSec,
                avgSpeedKmh = avgSpeedKmh,
                elevationGainM = result.elevationGainM,
                avgHr = avgHr,
                maxHr = profile.maxHr,
            )
            val endedAt = clock.millis()
            val patch = SessionUpdate {
                this.endedAt = endedAt
                this.durationSec = durationSec
                this.movingTimeSec = if (moving > 0) moving else null
                this.distanceM = result.distanceM
                this.avgSpeedKmh = avgSpeedKmh
                this.maxSpeedKmh = result.maxSpeedKmh
                this.elevationGainM = result.elevationGainM
                this.avgHr = avgHr
                this.maxHr = maxHr
                this.avgCadence = avgCadence
                this.maxCadence = maxCadence
                this.calories = calories
            }
            SavePrep(
                id = id,
                patch = patch,
                points = attachSensorsLocked(result.points),
                data = SavedSessionData(
                    type = s.type,
                    startedAt = s.startedAt ?: result.points.firstOrNull()?.ts ?: endedAt,
                    endedAt = endedAt,
                    distanceM = result.distanceM,
                    calories = calories,
                    hrSamples = hrSamples.toList(),
                ),
            )
        } ?: return null

        return try {
            flushMutex.withLock { sessions.finalizeSession(prep.id, prep.patch, prep.points) }
            finalizer.onSaved(prep.data)
            mutex.withLock {
                clearOutingLocked()
                stopwatch.reset()
                _state.update { it.fresh(type = it.type, savedSessionId = prep.id) }
            }
            prep.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "enregistrement de la sortie impossible", e)
            _state.update { it.copy(phase = OutingPhase.SAVE_FAILED, error = ERROR_SAVE) }
            null
        }
    }

    private suspend fun collectGps() {
        try {
            gps.fixes().collect { handleFix(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            _state.update { it.copy(gpsStatus = GpsStatus.DENIED) }
        } catch (e: Exception) {
            Log.w(TAG, "flux GPS interrompu", e)
        }
    }

    private suspend fun handleFix(fix: GpsFix) = mutex.withLock {
        val consolidator = consolidator ?: return@withLock
        val result = consolidator.process(fix)
        val point = result.point
        if (point == null) {
            _state.update { it.copy(accuracyM = fix.accuracy) }
            return@withLock
        }
        if (_state.value.phase != OutingPhase.ACTIVE) return@withLock

        points.add(point)
        val instSpeed = point.speedKmh ?: 0.0
        val path = pushLivePathLocked(point)
        _state.update { s ->
            val maxSpeed = if (instSpeed > s.maxSpeedKmh && instSpeed <= MAX_PLAUSIBLE_KMH) instSpeed else s.maxSpeedKmh
            withLiveCalories(
                s.copy(
                    distanceM = s.distanceM + result.deltaDistanceM,
                    speedKmh = instSpeed,
                    maxSpeedKmh = maxSpeed,
                    elevationGainM = s.elevationGainM + result.deltaElevationGainM,
                    pointCount = points.size,
                    accuracyM = fix.accuracy,
                    livePath = path ?: s.livePath,
                ),
            )
        }
    }

    private fun pushLivePathLocked(point: ConsolidatedPoint): List<ConsolidatedPoint>? {
        val last = lastLive
        return if (last == null || haversineMeters(last, point) >= LIVE_DECIMATE_M) {
            livePath.add(point)
            lastLive = point
            lastLiveEmitTs = point.ts
            livePath.toList()
        } else if (point.ts - lastLiveEmitTs >= LIVE_HEAD_THROTTLE_MS) {
            lastLiveEmitTs = point.ts
            livePath + point
        } else {
            null
        }
    }

    private fun stopGpsLocked(): GpsResult {
        gpsJob?.cancel()
        gpsJob = null
        flushJob?.cancel()
        flushJob = null
        val s = _state.value
        _state.update { it.copy(gpsStatus = GpsStatus.IDLE) }
        return GpsResult(
            points = points.toList(),
            distanceM = s.distanceM,
            speedKmh = s.speedKmh,
            maxSpeedKmh = s.maxSpeedKmh,
            elevationGainM = s.elevationGainM,
            pointCount = points.size,
            accuracyM = s.accuracyM,
        )
    }

    private suspend fun flushLoop() {
        while (true) {
            delay(FLUSH_INTERVAL_MS)
            flush()
        }
    }

    private suspend fun flush() {
        if (!flushMutex.tryLock()) return
        try {
            val batch = mutex.withLock {
                val s = _state.value
                val id = s.sessionId ?: return@withLock null
                if (s.phase != OutingPhase.ACTIVE && s.phase != OutingPhase.PAUSED) return@withLock null
                val from = flushedCount
                val slice = takeUnflushedLocked()
                if (slice.isEmpty()) return@withLock null
                Triple(id, from, attachSensorsLocked(slice))
            } ?: return
            val (id, from, slice) = batch
            try {
                sessions.insertTrackPoints(id, slice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "flush incrémental ignoré", e)
                mutex.withLock {
                    if (_state.value.sessionId == id && flushedCount == from + slice.size) flushedCount = from
                }
            }
        } finally {
            flushMutex.unlock()
        }
    }

    private fun takeUnflushedLocked(): List<ConsolidatedPoint> {
        if (flushedCount >= points.size) return emptyList()
        val slice = points.subList(flushedCount, points.size).toList()
        flushedCount = points.size
        return slice
    }

    private fun attachSensorsLocked(batch: List<ConsolidatedPoint>): List<TrackPointInput> = batch.map { p ->
        TrackPointInput(
            ts = p.ts,
            lat = p.lat,
            lon = p.lon,
            altitude = p.altitude,
            speedKmh = p.speedKmh,
            hr = nearestSample(hrSamples, p.ts, { it.hr }),
            cadence = nearestSample(cadenceSamples, p.ts, { it.cadence }),
        )
    }

    private suspend fun onHrFrame(frame: HrFrame) = mutex.withLock {
        _state.update { it.copy(bpm = frame.bpm) }
        if (_state.value.phase != OutingPhase.ACTIVE) return@withLock
        pushDownsampled(hrSamples, HrSample(frame.ts, frame.bpm.toDouble()), { it.hr })
    }

    private suspend fun onCscFrame(frame: CscFrame) = mutex.withLock {
        _state.update {
            it.copy(
                cadenceRpm = frame.cadenceRpm ?: it.cadenceRpm,
                wheelSpeedKmh = frame.speedKmh ?: it.wheelSpeedKmh,
            )
        }
        val rpm = frame.cadenceRpm ?: return@withLock
        if (_state.value.phase != OutingPhase.ACTIVE) return@withLock
        pushDownsampled(cadenceSamples, CadenceSample(frame.ts, rpm.toDouble()), { it.cadence })
    }

    private fun resetBuffersLocked() {
        consolidator = GpsConsolidator()
        clearOutingLocked()
    }

    private fun clearOutingLocked() {
        points.clear()
        flushedCount = 0
        livePath.clear()
        lastLive = null
        lastLiveEmitTs = 0L
        hrSamples.clear()
        cadenceSamples.clear()
        frozen = null
    }

    private fun OutingState.fresh(
        type: ActivityType,
        phase: OutingPhase = OutingPhase.IDLE,
        gpsStatus: GpsStatus = GpsStatus.IDLE,
        sessionId: Long? = null,
        startedAt: Long? = null,
        error: OutingError? = null,
        savedSessionId: Long? = null,
    ) = OutingState(
        phase = phase,
        type = type,
        sessionId = sessionId,
        startedAt = startedAt,
        gpsStatus = gpsStatus,
        bpm = bpm,
        cadenceRpm = cadenceRpm,
        wheelSpeedKmh = wheelSpeedKmh,
        error = error,
        savedSessionId = savedSessionId,
    )

    private fun withLiveCalories(s: OutingState): OutingState {
        if (!s.isLive && s.phase != OutingPhase.SAVING) return s.copy(caloriesLive = 0.0)
        val avgKmh = if (s.elapsedSec > 0) s.distanceM / 1000 / (s.elapsedSec / 3600.0) else 0.0
        return s.copy(caloriesLive = estimateCalories(s.type, profile.weightKg, s.elapsedSec, avgKmh))
    }

    private fun startForegroundService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "service de premier plan indisponible", e)
        }
    }

    companion object {
        private const val TAG = "TrackingController"

        const val FLUSH_INTERVAL_MS = 20_000L
        const val LIVE_DECIMATE_M = 8.0
        const val LIVE_HEAD_THROTTLE_MS = 2_500L
        const val MAX_PLAUSIBLE_KMH = 120.0

        val ERROR_DENIED = OutingError(
            "Localisation refusée",
            "Autorise l'accès à la position pour mesurer ta sortie. Tu peux l'activer dans les réglages Android.",
        )
        val ERROR_COARSE = OutingError(
            "Position précise requise",
            "Élan a besoin de la position précise pour tracer ta sortie. Choisis « Précise » dans les réglages de localisation de l'application.",
        )
        val ERROR_START = OutingError("Impossible de démarrer", "La sortie n'a pas pu être initialisée. Réessaie.")
        val ERROR_SAVE = OutingError(
            "Échec de l'enregistrement",
            "La sortie n'a pas pu être enregistrée. Réessaie, ou abandonne.",
        )
    }
}
