package ovh.battistella.elan.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteStatement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.local.MuscuSetDao
import ovh.battistella.elan.data.local.MuscuSetEntity
import ovh.battistella.elan.data.local.RecordColumn
import ovh.battistella.elan.data.local.SessionDao
import ovh.battistella.elan.data.local.SessionEntity
import ovh.battistella.elan.data.local.SessionQueryBuilder
import ovh.battistella.elan.data.local.TrackPointDao
import ovh.battistella.elan.data.local.TrackPointEntity
import ovh.battistella.elan.data.local.toDomain
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.ExercisePoint
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.PeriodStats
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.retypeChanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val db: ElanDatabase,
    private val sessionDao: SessionDao,
    private val trackPointDao: TrackPointDao,
    private val muscuSetDao: MuscuSetDao,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) {
    suspend fun createSession(type: ActivityType, startedAt: Long): Long = withContext(io) {
        sessionDao.insert(SessionEntity(type = type.key, startedAt = startedAt))
    }

    suspend fun updateSession(id: Long, patch: SessionUpdate) = withContext(io) {
        if (patch.isEmpty) return@withContext
        db.withTransaction { applyPatch(id, patch) }
    }

    suspend fun changeSessionType(id: Long, to: ActivityType): Session? = withContext(io) {
        val session = getSession(id) ?: return@withContext null
        val changes = retypeChanges(session, to, settings.getProfile()) ?: return@withContext null
        sessionDao.retype(id, changes.type.key, changes.calories, changes.avgCadence, changes.maxCadence)
        session.copy(
            type = changes.type,
            calories = changes.calories,
            avgCadence = changes.avgCadence,
            maxCadence = changes.maxCadence,
        )
    }

    suspend fun getSession(id: Long): Session? = withContext(io) { sessionDao.getById(id)?.toDomain() }

    suspend fun finalizeSession(id: Long, patch: SessionUpdate, points: List<TrackPointInput>) =
        withContext(io) {
            db.withTransaction {
                trackPointDao.deleteForSession(id)
                insertTrackPointRows(id, points)
                if (!patch.isEmpty) applyPatch(id, patch)
            }
        }

    suspend fun listInProgressSessions(type: ActivityType? = null): List<Session> = withContext(io) {
        sessionDao.listInProgress(type?.key).map { it.toDomain() }
    }

    suspend fun listSessions(options: ListSessionsOptions = ListSessionsOptions()): List<Session> =
        withContext(io) {
            sessionDao.listWithCounts(SessionQueryBuilder.build(options)).map {
                it.session.toDomain(setCount = it.setCount, exerciseCount = it.exerciseCount)
            }
        }

    suspend fun deleteSession(id: Long) = withContext(io) { sessionDao.delete(id) }

    suspend fun insertTrackPoints(sessionId: Long, points: List<TrackPointInput>) = withContext(io) {
        if (points.isEmpty()) return@withContext
        db.withTransaction { insertTrackPointRows(sessionId, points) }
    }

    suspend fun getTrackPoints(sessionId: Long): List<TrackPoint> = withContext(io) {
        trackPointDao.getForSession(sessionId).map { it.toDomain() }
    }

    suspend fun insertImportedSession(session: ImportedSession, points: List<TrackPointInput>): ImportResult =
        withContext(io) {
            db.withTransaction {
                val rowId = sessionDao.insertIgnore(session.toEntity())
                if (rowId == -1L) {
                    ImportResult.Duplicate
                } else {
                    insertTrackPointRows(rowId, points)
                    ImportResult.Imported(rowId)
                }
            }
        }

    suspend fun replaceMuscuSets(sessionId: Long, sets: List<MuscuSetInput>) = withContext(io) {
        db.withTransaction { replaceMuscuSetsIn(sessionId, sets) }
    }

    suspend fun saveMuscuSession(
        id: Long?,
        startedAt: Long,
        patch: SessionUpdate,
        sets: List<MuscuSetInput>,
    ): Long = withContext(io) {
        db.withTransaction {
            val sessionId = id ?: sessionDao.insert(
                SessionEntity(type = ActivityType.MUSCU.key, startedAt = startedAt),
            )
            if (!patch.isEmpty) applyPatch(sessionId, patch)
            replaceMuscuSetsIn(sessionId, sets)
            sessionId
        }
    }

    suspend fun getMuscuSets(sessionId: Long): List<MuscuSet> = withContext(io) {
        muscuSetDao.getForSession(sessionId).map { it.toDomain() }
    }

    suspend fun lastWeightByExercise(names: List<String>): Map<String, Double> = withContext(io) {
        if (names.isEmpty()) return@withContext emptyMap()
        val out = LinkedHashMap<String, Double>()
        for (row in muscuSetDao.weightsByRecency(names)) out.putIfAbsent(row.exercise, row.weightKg)
        out
    }

    suspend fun listMuscuExercises(): List<ExerciseSummary> = withContext(io) {
        muscuSetDao.listExercises().map {
            ExerciseSummary(
                exercise = it.exercise,
                sessions = it.sessions,
                lastWeightKg = it.lastWeightKg,
                lastAt = it.lastAt,
                lastDifficulty = Difficulty.fromKey(it.lastDifficulty),
            )
        }
    }

    suspend fun exerciseHistory(name: String): List<ExercisePoint> = withContext(io) {
        muscuSetDao.exerciseHistory(name).map {
            ExercisePoint(
                sessionId = it.sessionId,
                startedAt = it.startedAt,
                maxWeightKg = it.maxWeightKg,
                topReps = it.topReps,
                volume = it.volume,
                sets = it.sets,
                difficulty = Difficulty.fromKey(it.difficulty),
            )
        }
    }

    suspend fun statsBetween(fromMs: Long, toMs: Long, type: ActivityType? = null): PeriodStats =
        withContext(io) {
            val r = sessionDao.statsBetween(fromMs, toMs, type?.key)
            PeriodStats(r.sessionCount, r.totalDurationSec, r.totalDistanceM, r.totalCalories)
        }

    suspend fun statsSince(sinceMs: Long): PeriodStats = statsBetween(sinceMs, Long.MAX_VALUE)

    suspend fun tonnageBetween(fromMs: Long, toMs: Long): Double = withContext(io) {
        muscuSetDao.tonnageBetween(fromMs, toMs)
    }

    suspend fun dailyDurations(days: Int, nowMs: Long = System.currentTimeMillis()): List<DailyDuration> =
        withContext(io) { sessionDao.dailyDurations(nowMs - days * DAY_MS) }

    suspend fun sessionRecords(s: Session): List<SessionRecord> = withContext(io) {
        val kinds = if (isGpsActivity(s.type)) RecordKind.entries else listOf(RecordKind.DURATION)

        val zone = ZoneId.systemDefault()
        val year = Instant.ofEpochMilli(s.startedAt).atZone(zone).year
        val yearStart = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val yearEnd = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val out = ArrayList<SessionRecord>()
        for (kind in kinds) {
            val (column, value) = recordColumn(kind, s)
            if (value == null || value <= 0) continue
            if (sessionDao.countBetter(s.type.key, s.id, column, value, null, null) == 0) {
                out += SessionRecord(kind, RecordScope.ALL)
                continue
            }
            if (sessionDao.countBetter(s.type.key, s.id, column, value, yearStart, yearEnd) == 0) {
                out += SessionRecord(kind, RecordScope.YEAR)
            }
        }
        out
    }

    private fun recordColumn(kind: RecordKind, s: Session): Pair<String, Double?> = when (kind) {
        RecordKind.DISTANCE -> RecordColumn.DISTANCE to s.distanceM
        RecordKind.ELEVATION -> RecordColumn.ELEVATION to s.elevationGainM
        RecordKind.SPEED -> RecordColumn.SPEED to s.avgSpeedKmh
        RecordKind.DURATION ->
            if (s.type == ActivityType.VELO) {
                RecordColumn.MOVING_OR_DURATION to (s.movingTimeSec ?: s.durationSec).toDouble()
            } else {
                RecordColumn.DURATION to s.durationSec.toDouble()
            }
    }

    suspend fun clearAllData() = withContext(io) {
        db.withTransaction {
            trackPointDao.deleteAll()
            muscuSetDao.deleteAll()
            sessionDao.deleteAll()
        }
    }

    suspend fun clearAllDataIncludingSettings() = withContext(io) {
        db.withTransaction {
            trackPointDao.deleteAll()
            muscuSetDao.deleteAll()
            sessionDao.deleteAll()
            db.bodyMeasurementDao().deleteAll()
            db.settingsDao().deleteAllExcept(SettingsRepository.Keys.RESET_KEEP.toList())
        }
    }

    private fun applyPatch(id: Long, patch: SessionUpdate) {
        val assignments = patch.columns.joinToString(", ") { "$it = ?" }
        val statement = db.compileStatement("UPDATE sessions SET $assignments WHERE id = ?")
        try {
            patch.values.forEachIndexed { i, v -> statement.bindValue(i + 1, v) }
            statement.bindLong(patch.values.size + 1, id)
            statement.executeUpdateDelete()
        } finally {
            statement.close()
        }
    }

    private fun SupportSQLiteStatement.bindValue(index: Int, value: Any?) {
        when (value) {
            null -> bindNull(index)
            is Int -> bindLong(index, value.toLong())
            is Long -> bindLong(index, value)
            is Double -> bindDouble(index, value)
            is String -> bindString(index, value)
            else -> throw IllegalArgumentException("type non lié : ${value::class}")
        }
    }

    private suspend fun insertTrackPointRows(sessionId: Long, points: List<TrackPointInput>) {
        if (points.isEmpty()) return
        for (chunk in points.chunked(TRACK_POINT_CHUNK)) {
            trackPointDao.insertAll(chunk.map { it.toEntity(sessionId) })
        }
    }

    private suspend fun replaceMuscuSetsIn(sessionId: Long, sets: List<MuscuSetInput>) {
        muscuSetDao.deleteForSession(sessionId)
        if (sets.isEmpty()) return
        muscuSetDao.insertAll(
            sets.map {
                MuscuSetEntity(
                    sessionId = sessionId,
                    exercise = it.exercise,
                    setIndex = it.setIndex,
                    reps = it.reps,
                    weightKg = it.weightKg,
                    difficulty = it.difficulty?.key,
                )
            }
        )
    }

    private fun TrackPointInput.toEntity(sessionId: Long) = TrackPointEntity(
        sessionId = sessionId,
        ts = ts,
        lat = lat,
        lon = lon,
        altitude = altitude,
        speedKmh = speedKmh,
        hr = hr,
        cadence = cadence,
    )

    private fun ImportedSession.toEntity() = SessionEntity(
        type = type.key,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSec = durationSec,
        movingTimeSec = movingTimeSec,
        notes = notes,
        avgHr = avgHr,
        maxHr = maxHr,
        distanceM = distanceM,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        elevationGainM = elevationGainM,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        calories = calories,
        source = source,
        externalId = externalId,
    )

    private companion object {
        const val TRACK_POINT_CHUNK = 100
        const val DAY_MS = 86_400_000L
    }
}
