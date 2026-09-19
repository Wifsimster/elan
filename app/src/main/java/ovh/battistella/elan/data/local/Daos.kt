// DAO Room : une méthode par requête SQL de `db.ts`, avec le même SQL. Les
// opérations composites (finalisation, import, snapshot) sont orchestrées par
// les dépôts dans `db.withTransaction`.
package ovh.battistella.elan.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import ovh.battistella.elan.data.repository.DailyDuration

/** Une séance et ses agrégats de complétion muscu (`listSessions`). */
data class SessionWithCounts(
    @Embedded val session: SessionEntity,
    val setCount: Int,
    val exerciseCount: Int,
)

/** Ligne brute de `statsBetween` (les sommes SQL sont des entiers sur une fenêtre vide). */
data class StatsRow(
    val sessionCount: Int,
    val totalDurationSec: Int,
    val totalDistanceM: Double,
    val totalCalories: Double,
)

/** Ligne brute de `listMuscuExercises` (ressenti encore en TEXT). */
data class ExerciseSummaryRow(
    val exercise: String,
    val sessions: Int,
    val lastAt: Long,
    val lastWeightKg: Double,
    val lastDifficulty: String?,
)

/** Ligne brute de `exerciseHistory` (ressenti encore en TEXT). */
data class ExercisePointRow(
    val sessionId: Long,
    val startedAt: Long,
    val maxWeightKg: Double,
    val topReps: Int,
    val volume: Double,
    val sets: Int,
    val difficulty: String?,
)

/** Une série (exercice, charge) ordonnée par séance la plus récente puis charge décroissante. */
data class ExerciseWeightRow(val exercise: String, val weightKg: Double)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    /**
     * `INSERT OR IGNORE` : l'unique contrainte qu'une séance importée (id
     * auto-attribué, colonnes NOT NULL renseignées) peut violer est l'index
     * unique sur `externalId`, donc c'est l'équivalent exact du
     * `ON CONFLICT(externalId) WHERE externalId IS NOT NULL DO NOTHING`
     * d'origine. Renvoie -1 quand la ligne a été ignorée (doublon).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(session: SessionEntity): Long

    /** Insertion avec les ids fournis (restauration d'une sauvegarde). */
    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query(
        """
        SELECT * FROM sessions
        WHERE endedAt IS NULL AND (:type IS NULL OR type = :type)
        ORDER BY startedAt ASC
        """
    )
    suspend fun listInProgress(type: String?): List<SessionEntity>

    /** Historique filtré et paginé : requête construite par [SessionQueryBuilder]. */
    @RawQuery(observedEntities = [SessionEntity::class, MuscuSetEntity::class])
    suspend fun listWithCounts(query: SupportSQLiteQuery): List<SessionWithCounts>

    /** Changement de type : un seul UPDATE, pas d'état intermédiaire incohérent. */
    @Query(
        "UPDATE sessions SET type = :type, calories = :calories, avgCadence = :avgCadence, maxCadence = :maxCadence WHERE id = :id"
    )
    suspend fun retype(id: Long, type: String, calories: Double?, avgCadence: Double?, maxCadence: Double?)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    /**
     * Agrégats sur `[fromMs, toMs)`. Temps en mouvement quand il est connu
     * (vélo), sinon durée totale (muscu) : un chrono oublié à l'arrêt ne gonfle
     * pas le cumul d'effort.
     */
    @Query(
        """
        SELECT
            COUNT(*) AS sessionCount,
            COALESCE(SUM(COALESCE(movingTimeSec, durationSec)), 0) AS totalDurationSec,
            COALESCE(SUM(distanceM), 0) AS totalDistanceM,
            COALESCE(SUM(calories), 0) AS totalCalories
        FROM sessions
        WHERE endedAt IS NOT NULL AND startedAt >= :fromMs AND startedAt < :toMs
          AND (:type IS NULL OR type = :type)
        """
    )
    suspend fun statsBetween(fromMs: Long, toMs: Long, type: String?): StatsRow

    @Query(
        """
        SELECT date(startedAt / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(COALESCE(movingTimeSec, durationSec)), 0) AS durationSec
        FROM sessions
        WHERE endedAt IS NOT NULL AND startedAt >= :sinceMs
        GROUP BY day
        ORDER BY day ASC
        """
    )
    suspend fun dailyDurations(sinceMs: Long): List<DailyDuration>

    /**
     * Nombre de séances du même type faisant mieux que `value` sur la métrique
     * `column` (liste blanche fermée, voir [RecordColumn]), hors la séance
     * `id`, optionnellement bornées dans `[fromMs, toMs)`. Une valeur NULL
     * n'est jamais « meilleure » (NULL > x est NULL), comme dans l'original.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE type = :type AND endedAt IS NOT NULL AND id <> :id
          AND (:fromMs IS NULL OR startedAt >= :fromMs)
          AND (:toMs IS NULL OR startedAt < :toMs)
          AND CASE :column
                WHEN 'distanceM' THEN distanceM
                WHEN 'elevationGainM' THEN elevationGainM
                WHEN 'avgSpeedKmh' THEN avgSpeedKmh
                WHEN 'movingOrDuration' THEN COALESCE(movingTimeSec, durationSec)
                ELSE durationSec
              END > :value
        """
    )
    suspend fun countBetter(
        type: String,
        id: Long,
        column: String,
        value: Double,
        fromMs: Long?,
        toMs: Long?,
    ): Int
}

/** Expressions SQL des métriques de record (jamais d'entrée utilisateur). */
object RecordColumn {
    const val DISTANCE = "distanceM"
    const val ELEVATION = "elevationGainM"
    const val SPEED = "avgSpeedKmh"
    const val DURATION = "durationSec"

    /** `COALESCE(movingTimeSec, durationSec)` : durée vélo hors arrêts. */
    const val MOVING_OR_DURATION = "movingOrDuration"
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY ts ASC")
    suspend fun getForSession(sessionId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points")
    suspend fun getAll(): List<TrackPointEntity>

    @Query("DELETE FROM track_points")
    suspend fun deleteAll()
}

@Dao
interface MuscuSetDao {
    @Insert
    suspend fun insertAll(sets: List<MuscuSetEntity>)

    @Query("DELETE FROM muscu_sets WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("SELECT * FROM muscu_sets WHERE sessionId = :sessionId ORDER BY setIndex ASC, id ASC")
    suspend fun getForSession(sessionId: Long): List<MuscuSetEntity>

    @Query("SELECT * FROM muscu_sets")
    suspend fun getAll(): List<MuscuSetEntity>

    @Query("DELETE FROM muscu_sets")
    suspend fun deleteAll()

    /**
     * Séries des exercices demandés, sur les séances muscu terminées, de la
     * séance la plus récente à la plus ancienne puis de la charge la plus
     * lourde à la plus légère : la première ligne de chaque exercice est la
     * charge max de sa dernière séance. Même tri que la fonction de fenêtrage
     * `ROW_NUMBER() OVER (PARTITION BY exercise ORDER BY startedAt DESC,
     * weightKg DESC)` d'origine — le dédoublonnage `rn = 1` se fait côté
     * Kotlin car les fonctions de fenêtrage n'existent qu'à partir de SQLite
     * 3.25, absent des appareils sous Android 8–9 (minSdk 26).
     */
    @Query(
        """
        SELECT ms.exercise AS exercise, ms.weightKg AS weightKg
        FROM muscu_sets ms
        JOIN sessions s ON s.id = ms.sessionId
        WHERE s.type = 'muscu' AND s.endedAt IS NOT NULL AND ms.exercise IN (:names)
        ORDER BY s.startedAt DESC, ms.weightKg DESC
        """
    )
    suspend fun weightsByRecency(names: List<String>): List<ExerciseWeightRow>

    /** Exercices connus et leur dernier état, les plus récents d'abord. */
    @Query(
        """
        SELECT ms.exercise AS exercise,
               COUNT(DISTINCT ms.sessionId) AS sessions,
               MAX(s.startedAt) AS lastAt,
               (SELECT m2.weightKg
                  FROM muscu_sets m2
                  JOIN sessions s2 ON s2.id = m2.sessionId
                 WHERE m2.exercise = ms.exercise AND s2.endedAt IS NOT NULL
                 ORDER BY s2.startedAt DESC, m2.weightKg DESC
                 LIMIT 1) AS lastWeightKg,
               (SELECT m3.difficulty
                  FROM muscu_sets m3
                  JOIN sessions s3 ON s3.id = m3.sessionId
                 WHERE m3.exercise = ms.exercise AND s3.endedAt IS NOT NULL
                 ORDER BY s3.startedAt DESC
                 LIMIT 1) AS lastDifficulty
          FROM muscu_sets ms
          JOIN sessions s ON s.id = ms.sessionId
         WHERE s.endedAt IS NOT NULL
         GROUP BY ms.exercise
         ORDER BY lastAt DESC
        """
    )
    suspend fun listExercises(): List<ExerciseSummaryRow>

    /**
     * Historique d'un exercice, une ligne par séance terminée, du plus ancien
     * au plus récent. `topReps` et `difficulty` viennent de sous-requêtes
     * corrélées explicites : la garantie SQLite « colonne nue = ligne du MAX »
     * ne vaut qu'avec un seul agrégat min/max, sinon `topReps` pouvait venir
     * d'une série d'échauffement.
     */
    @Query(
        """
        SELECT s.id AS sessionId,
               s.startedAt AS startedAt,
               MAX(ms.weightKg) AS maxWeightKg,
               (SELECT m2.reps
                  FROM muscu_sets m2
                 WHERE m2.sessionId = s.id AND m2.exercise = ms.exercise
                 ORDER BY m2.weightKg DESC, m2.reps DESC
                 LIMIT 1) AS topReps,
               SUM(ms.reps * ms.weightKg) AS volume,
               COUNT(*) AS sets,
               (SELECT m3.difficulty
                  FROM muscu_sets m3
                 WHERE m3.sessionId = s.id AND m3.exercise = ms.exercise
                   AND m3.difficulty IS NOT NULL
                 LIMIT 1) AS difficulty
          FROM muscu_sets ms
          JOIN sessions s ON s.id = ms.sessionId
         WHERE s.endedAt IS NOT NULL AND ms.exercise = :name
         GROUP BY s.id
         ORDER BY s.startedAt ASC
        """
    )
    suspend fun exerciseHistory(name: String): List<ExercisePointRow>

    /** Tonnage (Σ reps × charge) des séances muscu terminées dans `[fromMs, toMs)`. */
    @Query(
        """
        SELECT COALESCE(SUM(ms.reps * ms.weightKg), 0)
          FROM muscu_sets ms
          JOIN sessions s ON s.id = ms.sessionId
         WHERE s.type = 'muscu' AND s.endedAt IS NOT NULL
           AND s.startedAt >= :fromMs AND s.startedAt < :toMs
        """
    )
    suspend fun tonnageBetween(fromMs: Long, toMs: Long): Double
}

@Dao
interface BodyMeasurementDao {
    @Insert
    suspend fun insert(measurement: BodyMeasurementEntity): Long

    @Insert
    suspend fun insertAll(measurements: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM body_measurements ORDER BY measuredAt DESC, id DESC LIMIT :limit")
    suspend fun list(limit: Int): List<BodyMeasurementEntity>

    @Query("SELECT * FROM body_measurements ORDER BY measuredAt DESC, id DESC LIMIT 1")
    suspend fun latest(): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements")
    suspend fun getAll(): List<BodyMeasurementEntity>

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    /** Insertion ou mise à jour (équivalent de `ON CONFLICT(key) DO UPDATE`). */
    @Upsert
    suspend fun upsert(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    @Query("DELETE FROM settings WHERE `key` NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)
}
