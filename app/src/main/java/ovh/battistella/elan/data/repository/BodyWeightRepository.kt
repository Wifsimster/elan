package ovh.battistella.elan.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ovh.battistella.elan.data.local.BodyMeasurementDao
import ovh.battistella.elan.data.local.BodyMeasurementEntity
import ovh.battistella.elan.data.local.toDomain
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.BodyMeasurement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyWeightRepository @Inject constructor(
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) {
    suspend fun logBodyWeight(weightKg: Double, measuredAt: Long) = withContext(io) {
        bodyMeasurementDao.insert(BodyMeasurementEntity(measuredAt = measuredAt, weightKg = weightKg))
        syncProfileWeight()
    }

    suspend fun deleteBodyMeasurement(id: Long) = withContext(io) {
        bodyMeasurementDao.delete(id)
        syncProfileWeight()
    }

    suspend fun listBodyMeasurements(limit: Int = 1000): List<BodyMeasurement> = withContext(io) {
        bodyMeasurementDao.list(limit).map { it.toDomain() }
    }

    suspend fun latestBodyMeasurement(): BodyMeasurement? = withContext(io) {
        bodyMeasurementDao.latest()?.toDomain()
    }

    private suspend fun syncProfileWeight() {
        val latest = bodyMeasurementDao.latest() ?: return
        val profile = settings.getProfile()
        if (profile.weightKg != latest.weightKg) {
            settings.saveProfile(profile.copy(weightKg = latest.weightKg))
        }
    }
}
