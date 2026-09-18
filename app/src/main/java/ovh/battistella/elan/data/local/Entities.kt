// Entités Room = tables SQLite de l'app d'origine, à l'identique : mêmes noms
// de tables, de colonnes (camelCase) et d'index, mêmes types et nullabilités,
// même ordre de colonnes que le schéma v7 après migrations. Ce contrat est
// gardé par SchemaTest : une sauvegarde S3 ou un export coach produit par
// l'ancienne app doit rester relisible, colonne pour colonne.
//
// Les colonnes gardent leurs types bruts (`type`/`difficulty` en TEXT) ; la
// conversion vers les enums du domaine se fait dans les mappers ci-dessous,
// pas via des TypeConverters, pour que le schéma exporté reste celui d'origine.
package ovh.battistella.elan.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ovh.battistella.elan.domain.BodyMeasurement
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.toActivityType

/** Une séance. `endedAt IS NULL` = en cours (exclue de l'historique et des stats). */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["startedAt"], orders = [Index.Order.DESC], name = "idx_sessions_startedAt"),
        // L'index partiel d'origine (`WHERE externalId IS NOT NULL`) n'est pas
        // exprimable en Room ; un index unique plein a la même sémantique car
        // SQLite considère chaque NULL comme distinct.
        Index(value = ["externalId"], unique = true, name = "idx_sessions_external"),
    ],
)
data class SessionEntity(
    /** 0 = « pas encore attribué » : Room laisse alors SQLite auto-incrémenter. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val durationSec: Int = 0,
    val notes: String? = null,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val distanceM: Double? = null,
    val avgSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val elevationGainM: Double? = null,
    val calories: Double? = null,
    val avgCadence: Double? = null,
    val maxCadence: Double? = null,
    val source: String? = null,
    val externalId: String? = null,
    val movingTimeSec: Int? = null,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "ts"], name = "idx_track_session")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val speedKmh: Double? = null,
    val hr: Double? = null,
    val cadence: Double? = null,
)

@Entity(
    tableName = "muscu_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "exercise", "setIndex"], name = "idx_sets_session"),
        Index(value = ["exercise"], name = "idx_sets_exercise"),
    ],
)
data class MuscuSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val difficulty: String? = null,
)

@Entity(
    tableName = "body_measurements",
    indices = [Index(value = ["measuredAt"], orders = [Index.Order.DESC], name = "idx_body_measuredAt")],
)
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: Long,
    val weightKg: Double,
)

/** Réglages clé/valeur (profil, capteurs appairés, planning, brouillon…). */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

// ---------------------------------------------------------------------------
// Mappers entité <-> domaine
// ---------------------------------------------------------------------------

fun SessionEntity.toDomain(setCount: Int? = null, exerciseCount: Int? = null): Session = Session(
    id = id,
    type = toActivityType(type),
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
    setCount = setCount,
    exerciseCount = exerciseCount,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    type = type.key,
    startedAt = startedAt,
    endedAt = endedAt,
    durationSec = durationSec,
    notes = notes,
    avgHr = avgHr,
    maxHr = maxHr,
    distanceM = distanceM,
    avgSpeedKmh = avgSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    elevationGainM = elevationGainM,
    calories = calories,
    avgCadence = avgCadence,
    maxCadence = maxCadence,
    source = source,
    externalId = externalId,
    movingTimeSec = movingTimeSec,
)

fun TrackPointEntity.toDomain(): TrackPoint =
    TrackPoint(id, sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence)

fun TrackPoint.toEntity(): TrackPointEntity =
    TrackPointEntity(id, sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence)

fun MuscuSetEntity.toDomain(): MuscuSet =
    MuscuSet(id, sessionId, exercise, setIndex, reps, weightKg, Difficulty.fromKey(difficulty))

fun MuscuSet.toEntity(): MuscuSetEntity =
    MuscuSetEntity(id, sessionId, exercise, setIndex, reps, weightKg, difficulty?.key)

fun BodyMeasurementEntity.toDomain(): BodyMeasurement = BodyMeasurement(id, measuredAt, weightKg)

fun BodyMeasurement.toEntity(): BodyMeasurementEntity = BodyMeasurementEntity(id, measuredAt, weightKg)
