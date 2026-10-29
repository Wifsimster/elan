package ovh.battistella.elan.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.local.SettingEntity
import ovh.battistella.elan.data.local.toDomain
import ovh.battistella.elan.data.local.toEntity
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.BodyMeasurement
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import javax.inject.Inject
import javax.inject.Singleton

data class SettingEntry(val key: String, val value: String)

data class DbSnapshot(
    val sessions: List<Session>,
    val trackPoints: List<TrackPoint>,
    val muscuSets: List<MuscuSet>,
    val bodyMeasurements: List<BodyMeasurement>?,
    val settings: List<SettingEntry>,
)

@Singleton
class SnapshotRepository @Inject constructor(
    private val db: ElanDatabase,
    private val io: CoroutineDispatcher,
) {
    suspend fun exportAll(): DbSnapshot = withContext(io) {
        db.withTransaction {
            DbSnapshot(
                sessions = db.sessionDao().getAll().map { it.toDomain() },
                trackPoints = db.trackPointDao().getAll().map { it.toDomain() },
                muscuSets = db.muscuSetDao().getAll().map { it.toDomain() },
                bodyMeasurements = db.bodyMeasurementDao().getAll().map { it.toDomain() },
                settings = db.settingsDao().getAll()
                    .filter { it.key !in SettingsRepository.Keys.BACKUP_EXCLUDED }
                    .map { SettingEntry(it.key, it.value) },
            )
        }
    }

    suspend fun importAll(snapshot: DbSnapshot) = withContext(io) {
        db.withTransaction {
            db.trackPointDao().deleteAll()
            db.muscuSetDao().deleteAll()
            db.sessionDao().deleteAll()
            db.bodyMeasurementDao().deleteAll()

            db.sessionDao().insertAll(snapshot.sessions.map { it.toEntity() })
            db.trackPointDao().insertAll(snapshot.trackPoints.map { it.toEntity() })
            db.bodyMeasurementDao().insertAll((snapshot.bodyMeasurements ?: emptyList()).map { it.toEntity() })
            db.muscuSetDao().insertAll(snapshot.muscuSets.map { it.toEntity() })

            for (entry in snapshot.settings) {
                if (entry.key in SettingsRepository.Keys.BACKUP_EXCLUDED) continue
                db.settingsDao().upsert(SettingEntity(entry.key, entry.value))
            }
        }
    }
}
