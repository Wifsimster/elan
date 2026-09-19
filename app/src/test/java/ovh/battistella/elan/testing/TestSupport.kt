package ovh.battistella.elan.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ovh.battistella.elan.data.local.BodyMeasurementEntity
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.local.MuscuSetEntity
import ovh.battistella.elan.data.local.SessionEntity
import ovh.battistella.elan.data.local.TrackPointEntity
import ovh.battistella.elan.data.repository.BodyWeightRepository
import ovh.battistella.elan.data.repository.MuscuSetInput
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SnapshotRepository
import ovh.battistella.elan.data.repository.TrackPointInput
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.sync.AutoProgressionRunner
import ovh.battistella.elan.sync.ProgressionNotify
import java.util.concurrent.Executor

/**
 * Échafaudage partagé : une vraie base Room en mémoire branchée sur les vrais
 * dépôts (les requêtes des DAO sont exercées pour de bon), plus des fixtures
 * d'entités avec des défauts plausibles.
 */
object TestSupport {

    /** Exécuteur synchrone : Room tourne sur le thread appelant, tout reste déterministe. */
    private val directExecutor = Executor { it.run() }

    fun inMemoryDb(): ElanDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(ctx, ElanDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    /** Les dépôts de la couche données, tous sur la même base. */
    class Repositories(
        val settings: SettingsRepository,
        val sessions: SessionRepository,
        val bodyWeight: BodyWeightRepository,
        val snapshot: SnapshotRepository,
    )

    fun repositories(db: ElanDatabase, io: CoroutineDispatcher = Dispatchers.Unconfined): Repositories {
        val settings = SettingsRepository(db.settingsDao(), io)
        return Repositories(
            settings = settings,
            sessions = SessionRepository(
                db = db,
                sessionDao = db.sessionDao(),
                trackPointDao = db.trackPointDao(),
                muscuSetDao = db.muscuSetDao(),
                settings = settings,
                io = io,
            ),
            bodyWeight = BodyWeightRepository(db.bodyMeasurementDao(), settings, io),
            snapshot = SnapshotRepository(db, io),
        )
    }

    /** Progression auto sur les mêmes dépôts, notification remplacée par [notify] (muette par défaut). */
    fun progressionRunner(repos: Repositories, notify: ProgressionNotify = ProgressionNotify { _, _ -> }): AutoProgressionRunner =
        AutoProgressionRunner(repos.settings, repos.sessions, notify)

    // --- fixtures ---

    fun session(
        id: Long = 0,
        type: ActivityType = ActivityType.VELO,
        startedAt: Long = 1_700_000_000_000L,
        endedAt: Long? = startedAt + 3_600_000L,
        durationSec: Int = 3600,
        movingTimeSec: Int? = null,
        notes: String? = null,
        distanceM: Double? = null,
        avgSpeedKmh: Double? = null,
        elevationGainM: Double? = null,
        calories: Double? = null,
        source: String? = null,
        externalId: String? = null,
    ) = SessionEntity(
        id = id,
        type = type.key,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSec = durationSec,
        movingTimeSec = movingTimeSec,
        notes = notes,
        distanceM = distanceM,
        avgSpeedKmh = avgSpeedKmh,
        elevationGainM = elevationGainM,
        calories = calories,
        source = source,
        externalId = externalId,
    )

    fun trackPoint(
        sessionId: Long,
        ts: Long = 1_700_000_000_000L,
        lat: Double = 48.8566,
        lon: Double = 2.3522,
        altitude: Double? = 35.0,
        speedKmh: Double? = 18.0,
        hr: Double? = 120.0,
        cadence: Double? = null,
    ) = TrackPointEntity(
        sessionId = sessionId,
        ts = ts,
        lat = lat,
        lon = lon,
        altitude = altitude,
        speedKmh = speedKmh,
        hr = hr,
        cadence = cadence,
    )

    fun trackPointInput(
        ts: Long = 1_700_000_000_000L,
        lat: Double = 48.8566,
        lon: Double = 2.3522,
        altitude: Double? = 35.0,
        speedKmh: Double? = 18.0,
        hr: Double? = 120.0,
        cadence: Double? = null,
    ) = TrackPointInput(ts, lat, lon, altitude, speedKmh, hr, cadence)

    fun muscuSet(
        sessionId: Long,
        exercise: String = "Goblet squat",
        setIndex: Int = 1,
        reps: Int = 10,
        weightKg: Double = 20.0,
        difficulty: Difficulty? = null,
    ) = MuscuSetEntity(
        sessionId = sessionId,
        exercise = exercise,
        setIndex = setIndex,
        reps = reps,
        weightKg = weightKg,
        difficulty = difficulty?.key,
    )

    fun muscuSetInput(
        exercise: String = "Goblet squat",
        setIndex: Int = 1,
        reps: Int = 10,
        weightKg: Double = 20.0,
        difficulty: Difficulty? = null,
    ) = MuscuSetInput(exercise, setIndex, reps, weightKg, difficulty)

    fun bodyMeasurement(
        measuredAt: Long = 1_700_000_000_000L,
        weightKg: Double = 72.0,
    ) = BodyMeasurementEntity(measuredAt = measuredAt, weightKg = weightKg)
}
