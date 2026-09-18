// Contrôleur de la sortie GPS en cours — l'unique état vivant d'une sortie
// (fusion de use-gps-tracker.ts, du chrono et du cycle de vie de sortie.tsx).
//
// Singleton de processus : l'écran n'en est qu'une vue. Il survit à la
// recréation de l'activité, et son cycle de vie est adossé au service de
// premier plan (`TrackingService`) qui maintient le GPS écran éteint.
//
// Garanties reproduites de l'app d'origine (port-spec 02-interface §10.1) :
//   - la séance est créée en base dès `begin()` (endedAt NULL = en cours) ;
//   - les points sont flushés toutes les 20 s et à la pause (survie au crash) ;
//   - `finish()` fige le résultat GPS une fois pour toutes : un réessai après
//     un échec d'écriture réutilise le même tracé et le même id de séance ;
//   - en pause le filtre reste alimenté (pas de saut à la reprise) mais rien
//     n'est cumulé ni enregistré ; un fix rejeté ne met à jour que la précision.
//
// Toutes les méthodes publiques sont `suspend` mais leur travail tourne sur la
// portée applicative (`scope.launch { … }.join()`) : l'appelant peut attendre
// le résultat, mais la disparition de sa propre portée (ViewModel détruit en
// plein enregistrement) n'interrompt pas la transition.
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

/** Phases de l'écran de sortie (`idle | active | paused | saving | save-failed` + `requesting`). */
enum class OutingPhase {
    IDLE,
    /** `begin()` en cours : permission vérifiée, séance créée. */
    REQUESTING,
    ACTIVE,
    PAUSED,
    SAVING,
    /** L'écriture finale a échoué : Réessayer (`retrySave`) ou Abandonner (`discard`). */
    SAVE_FAILED,
}

/** Résultat d'un `begin()`. */
enum class BeginResult {
    STARTED,
    /** Localisation refusée. */
    DENIED,
    /** Seule la position approximative est accordée : inexploitable pour un tracé. */
    COARSE,
    /** La séance n'a pas pu être créée en base : rien n'a démarré. */
    FAILED,
    /** Une sortie est déjà en cours. */
    BUSY,
}

/** Message d'erreur à présenter (titre + corps), tel que les alertes de l'app d'origine. */
data class OutingError(val title: String, val message: String)

/**
 * État observable de la sortie en cours. Les valeurs capteurs (`bpm`,
 * `cadenceRpm`, `wheelSpeedKmh`) reflètent la dernière trame reçue, quelle que
 * soit la phase ; les cumuls ne bougent qu'en phase `ACTIVE`.
 */
data class OutingState(
    val phase: OutingPhase = OutingPhase.IDLE,
    val type: ActivityType = ActivityType.VELO,
    /** Id de la séance en base (créée dès `begin()`), `null` hors sortie. */
    val sessionId: Long? = null,
    /** Début de la sortie (ms epoch), `null` hors sortie. */
    val startedAt: Long? = null,
    /** Temps actif écoulé (hors pauses), rafraîchi à ~4 Hz. */
    val elapsedSec: Int = 0,
    val distanceM: Double = 0.0,
    /** Vitesse instantanée du dernier point accepté (km/h). */
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val elevationGainM: Double = 0.0,
    /** Points consolidés retenus depuis le départ. */
    val pointCount: Int = 0,
    /** Précision horizontale du dernier fix reçu (m), accepté ou non ; `null` avant le premier. */
    val accuracyM: Double? = null,
    val gpsStatus: GpsStatus = GpsStatus.IDLE,
    /**
     * Tracé allégé pour la carte live : ancres figées tous les 8 m + curseur de
     * tête provisoire ré-émis au plus toutes les 2,5 s.
     */
    val livePath: List<ConsolidatedPoint> = emptyList(),
    /** Dernière FC reçue de la ceinture, `null` sans capteur. */
    val bpm: Int? = null,
    /** Dernière cadence reçue du capteur (tr/min), `null` sans capteur. */
    val cadenceRpm: Int? = null,
    /** Dernière vitesse roue reçue du capteur (km/h), `null` sans capteur. */
    val wheelSpeedKmh: Double? = null,
    /** Calories estimées sur la vitesse MOYENNE (distance / temps actif), pas l'instantanée. */
    val caloriesLive: Double = 0.0,
    /** Dernière erreur à présenter ; effacée au prochain `begin()`. */
    val error: OutingError? = null,
    /** Id de la dernière séance enregistrée avec succès (pour naviguer vers son détail). */
    val savedSessionId: Long? = null,
) {
    /** Une sortie est en cours (le service de premier plan doit tourner). */
    val isLive: Boolean get() = phase == OutingPhase.ACTIVE || phase == OutingPhase.PAUSED

    /** Corps du message d'erreur, raccourci pratique pour les snackbars. */
    val errorMessage: String? get() = error?.message
}

/** Résultat GPS figé au premier `finish()` (équivalent de `gps.stop()`). */
data class GpsResult(
    val points: List<ConsolidatedPoint>,
    val distanceM: Double,
    val speedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainM: Double,
    val pointCount: Int,
    val accuracyM: Double?,
)

/** Échantillon de cadence horodaté (tr/min), accumulé pendant la sortie. */
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

    /** Sérialise transitions de phase et accès aux tampons ci-dessous. */
    private val mutex = Mutex()

    // ---- tampons de la sortie (protégés par `mutex`) ---------------------
    private var consolidator: GpsConsolidator? = null
    private val points = ArrayList<ConsolidatedPoint>()
    /** Curseur du flush incrémental : index du premier point pas encore écrit. */
    private var flushedCount = 0
    private val livePath = ArrayList<ConsolidatedPoint>()
    private var lastLive: ConsolidatedPoint? = null
    private var lastLiveEmitTs = 0L
    private val hrSamples = ArrayList<HrSample>()
    private val cadenceSamples = ArrayList<CadenceSample>()
    /** Résultat figé au premier `finish()` : un réessai n'arrête pas le GPS deux fois. */
    private var frozen: GpsResult? = null
    private var profile = Profile()

    @Volatile private var flushing = false
    private var gpsJob: Job? = null
    private var flushJob: Job? = null

    init {
        scope.launch {
            stopwatch.elapsedSec.collect { sec -> _state.update { withLiveCalories(it.copy(elapsedSec = sec)) } }
        }
        // Trames brutes plutôt que valeur dédupliquée (voir SensorSamples.kt).
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

    // ---- cycle de vie -----------------------------------------------------

    /**
     * Démarre une sortie : vérifie la permission, crée la séance (endedAt NULL),
     * remet les tampons à zéro, lance le chrono, le service de premier plan, le
     * flux GPS et le flush périodique. En cas de refus ou d'échec, `state.error`
     * porte le message à présenter et rien n'a démarré.
     */
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

        // Séance créée immédiatement (déjà exclue de l'historique et des stats).
        // Si la base échoue, on ne démarre pas : mieux vaut un refus clair
        // qu'une sortie qu'on croit enregistrer sans filet.
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

    /** Met en pause : chrono figé, cumuls gelés, flush immédiat des points. */
    suspend fun pause() = scope.launch {
        val paused = mutex.withLock {
            if (_state.value.phase != OutingPhase.ACTIVE) return@withLock false
            stopwatch.pause()
            flushJob?.cancel()
            flushJob = null
            _state.update { it.copy(phase = OutingPhase.PAUSED) }
            true
        }
        if (paused) flush() // persiste sans attendre le prochain tick
    }.join()

    suspend fun resume() = scope.launch {
        mutex.withLock {
            if (_state.value.phase != OutingPhase.PAUSED) return@withLock
            stopwatch.start()
            _state.update { it.copy(phase = OutingPhase.ACTIVE) }
            flushJob = scope.launch { flushLoop() }
        }
    }.join()

    /**
     * Termine et enregistre la sortie (la confirmation est à la charge de
     * l'écran). Renvoie l'id de la séance enregistrée, ou `null` si
     * l'enregistrement a échoué (phase `SAVE_FAILED`, réessai possible) ou si
     * aucune sortie n'était en cours.
     */
    suspend fun finish(): Long? = scope.async { save(from = setOf(OutingPhase.ACTIVE, OutingPhase.PAUSED)) }.await()

    /** Réessaie l'enregistrement après un échec, avec le même id et le même tracé figé. */
    suspend fun retrySave(): Long? = scope.async { save(from = setOf(OutingPhase.SAVE_FAILED)) }.await()

    /**
     * Abandonne la sortie : GPS coupé, service arrêté (il suit l'état), séance
     * en cours supprimée pour qu'elle ne soit pas « récupérée » au prochain
     * lancement. Ignoré pendant un enregistrement.
     */
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

    /**
     * Points accumulés depuis le dernier appel (le curseur avance). Sert au
     * flush incrémental ; exposé pour les tests et un flush à la demande.
     */
    suspend fun takeUnflushed(): List<ConsolidatedPoint> = mutex.withLock { takeUnflushedLocked() }

    // ---- enregistrement ---------------------------------------------------

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

            // Figé au premier appel : un réessai réutilise le même tracé/agrégats.
            val result = frozen ?: stopGpsLocked()
            frozen = result
            stopwatch.pause()
            // Durée lue en direct au moment du « Terminer » (chrono figé ensuite :
            // un réessai obtient la même valeur).
            val durationSec = stopwatch.getElapsedSec()
            val (avgHr, maxHr) = summarizeHr(hrSamples)
            val (avgCadence, maxCadence) = summarizeCadence(cadenceSamples.map { it.cadence })
            // Temps en mouvement (hors arrêts) borné à la durée totale : base de
            // la vitesse moyenne et des calories. À défaut de tracé, la durée.
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
            // Écriture atomique : tout le tracé + agrégats + endedAt en une
            // transaction, sur l'id créé au démarrage (pas de doublon au réessai).
            sessions.finalizeSession(prep.id, prep.patch, prep.points)
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
            // Phase dédiée (Réessayer / Abandonner) — surtout PAS de « Reprendre » :
            // le GPS est arrêté. La séance reste en base (endedAt NULL), donc
            // récupérable au pire au prochain lancement.
            Log.w(TAG, "enregistrement de la sortie impossible", e)
            _state.update { it.copy(phase = OutingPhase.SAVE_FAILED, error = ERROR_SAVE) }
            null
        }
    }

    // ---- GPS ----------------------------------------------------------------

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
            // Fix rejeté (imprécis ou aberrant) : seul l'indicateur de précision bouge.
            _state.update { it.copy(accuracyM = fix.accuracy) }
            return@withLock
        }
        // En pause : le filtre reste alimenté (pas de saut à la reprise) mais on
        // ne cumule rien et on n'enregistre pas le point.
        if (_state.value.phase != OutingPhase.ACTIVE) return@withLock

        points.add(point)
        val instSpeed = point.speedKmh ?: 0.0
        val path = pushLivePathLocked(point)
        _state.update { s ->
            // Plafond de plausibilité : un Doppler aberrant ne s'octroie pas la vitesse max.
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

    /**
     * Alimente le tracé live décimé : une ancre est figée dès qu'on s'est
     * éloigné de 8 m de la précédente (le départ reste immuable) ; entre deux
     * ancres, la position courante est ré-émise en tête au plus toutes les
     * 2,5 s. Renvoie la liste à publier, ou `null` si rien ne change.
     */
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

    /** Coupe le flux GPS et le flush, et fige le résultat de la sortie. */
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

    // ---- flush incrémental ------------------------------------------------

    /** Flush périodique tant que la sortie est active (la boucle vit avec `flushJob`). */
    private suspend fun flushLoop() {
        while (true) {
            delay(FLUSH_INTERVAL_MS)
            flush()
        }
    }

    /**
     * Écrit les points accumulés depuis le dernier flush. Best-effort — un
     * échec n'est pas grave, l'enregistrement final réécrit tout le tracé.
     * Gardé contre le recouvrement (un flush lent ne se superpose pas au suivant).
     */
    private suspend fun flush() {
        val batch = mutex.withLock {
            val id = _state.value.sessionId ?: return@withLock null
            if (flushing) return@withLock null
            val slice = takeUnflushedLocked()
            if (slice.isEmpty()) return@withLock null
            flushing = true
            id to attachSensorsLocked(slice)
        } ?: return
        try {
            sessions.insertTrackPoints(batch.first, batch.second)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "flush incrémental ignoré", e)
        } finally {
            flushing = false
        }
    }

    private fun takeUnflushedLocked(): List<ConsolidatedPoint> {
        if (flushedCount >= points.size) return emptyList()
        val slice = points.subList(flushedCount, points.size).toList()
        flushedCount = points.size
        return slice
    }

    /** Attache la FC et la cadence les plus proches (± 10 s) à un lot de points. */
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

    // ---- capteurs ---------------------------------------------------------

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

    // ---- internes -----------------------------------------------------------

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
        flushing = false
    }

    /** État remis à neuf en conservant les lectures capteurs (indépendantes de la sortie). */
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

    /**
     * Calories live sur la vitesse MOYENNE (distance / temps actif), pas
     * l'instantanée : sinon la tuile bondit sur un simple sprint alors que
     * l'estimation doit refléter l'effort cumulé.
     */
    private fun withLiveCalories(s: OutingState): OutingState {
        if (!s.isLive && s.phase != OutingPhase.SAVING) return s.copy(caloriesLive = 0.0)
        val avgKmh = if (s.elapsedSec > 0) s.distanceM / 1000 / (s.elapsedSec / 3600.0) else 0.0
        return s.copy(caloriesLive = estimateCalories(s.type, profile.weightKg, s.elapsedSec, avgKmh))
    }

    private fun startForegroundService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        } catch (e: Exception) {
            // App en arrière-plan au moment du démarrage (interdit par Android) :
            // le suivi continue en mémoire tant que le processus vit.
            Log.w(TAG, "service de premier plan indisponible", e)
        }
    }

    companion object {
        private const val TAG = "TrackingController"

        /** Cadence du flush incrémental des points vers la base (survie au crash). */
        const val FLUSH_INTERVAL_MS = 20_000L
        /** Distance minimale entre deux ancres du tracé live. */
        const val LIVE_DECIMATE_M = 8.0
        /** Ré-émission du curseur de tête au plus toutes les 2,5 s. */
        const val LIVE_HEAD_THROTTLE_MS = 2_500L
        /** Au-delà, un Doppler est un glitch, pas un record (« 173 km/h à vélo »). */
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
