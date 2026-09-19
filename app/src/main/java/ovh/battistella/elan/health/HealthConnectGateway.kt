// Passerelle vers le SDK Health Connect (androidx.health.connect:connect-client).
// L'interface isole le SDK pour que `HealthConnectManager` se teste avec une
// passerelle factice ; `AndroidHealthConnectGateway` traduit les
// `HealthRecordSpec` purs en `Record` androidx.
package ovh.battistella.elan.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Disponibilité du fournisseur Health Connect sur l'appareil. */
enum class HealthSdkStatus {
    /** Prêt : les permissions peuvent être demandées et les enregistrements écrits. */
    AVAILABLE,

    /** Le fournisseur (app Health Connect) est absent ou trop ancien : à installer / mettre à jour. */
    UPDATE_REQUIRED,

    /** Aucun fournisseur possible sur cet appareil. */
    UNAVAILABLE,
}

interface HealthConnectGateway {
    fun sdkStatus(): HealthSdkStatus

    /** Permissions Health Connect accordées à Élan (chaînes `android.permission.health.*`). */
    suspend fun grantedPermissions(): Set<String>

    /** Contrat système de demande de permissions (entrée : permissions voulues, sortie : accordées). */
    fun requestPermissionContract(): ActivityResultContract<Set<String>, Set<String>>

    /** Insère UN enregistrement (le SDK exige un seul type par appel). */
    suspend fun insert(spec: HealthRecordSpec)

    /** Supprime par identifiants client ; inoffensif si aucun n'existe. */
    suspend fun deleteByClientIds(type: HealthRecordType, clientIds: List<String>)
}

@Singleton
class AndroidHealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectGateway {

    private val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(context)

    override fun sdkStatus(): HealthSdkStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthSdkStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthSdkStatus.UPDATE_REQUIRED
        else -> HealthSdkStatus.UNAVAILABLE
    }

    override suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    override fun requestPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    override suspend fun insert(spec: HealthRecordSpec) {
        client.insertRecords(listOf(toRecord(spec)))
    }

    override suspend fun deleteByClientIds(type: HealthRecordType, clientIds: List<String>) {
        client.deleteRecords(recordClass(type), recordIdsList = emptyList(), clientRecordIdsList = clientIds)
    }

    private fun recordClass(type: HealthRecordType): KClass<out Record> = when (type) {
        HealthRecordType.EXERCISE_SESSION -> ExerciseSessionRecord::class
        HealthRecordType.DISTANCE -> DistanceRecord::class
        HealthRecordType.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord::class
        HealthRecordType.HEART_RATE -> HeartRateRecord::class
    }

    /**
     * Traduction d'un enregistrement pur en `Record` androidx. Métadonnées :
     * enregistré activement par le téléphone, `clientRecordId` stable et
     * `clientRecordVersion = 0` — un ré-export réécrit au lieu de dupliquer.
     * Les décalages horaires sont ceux de l'appareil aux instants concernés.
     */
    private fun toRecord(spec: HealthRecordSpec): Record {
        val start = Instant.ofEpochMilli(spec.startedAt)
        val end = Instant.ofEpochMilli(spec.endedAt)
        val metadata = Metadata.activelyRecorded(
            clientRecordId = spec.clientRecordId,
            clientRecordVersion = 0L,
            device = Device(type = Device.TYPE_PHONE),
        )
        return when (spec) {
            is HealthRecordSpec.ExerciseSession -> ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offsetAt(start),
                endTime = end,
                endZoneOffset = offsetAt(end),
                exerciseType = spec.exerciseType,
                title = spec.title,
                metadata = metadata,
            )
            is HealthRecordSpec.Distance -> DistanceRecord(
                startTime = start,
                startZoneOffset = offsetAt(start),
                endTime = end,
                endZoneOffset = offsetAt(end),
                distance = Length.meters(spec.meters),
                metadata = metadata,
            )
            is HealthRecordSpec.ActiveCalories -> ActiveCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = offsetAt(start),
                endTime = end,
                endZoneOffset = offsetAt(end),
                energy = Energy.kilocalories(spec.kilocalories),
                metadata = metadata,
            )
            is HealthRecordSpec.HeartRate -> HeartRateRecord(
                startTime = start,
                startZoneOffset = offsetAt(start),
                endTime = end,
                endZoneOffset = offsetAt(end),
                samples = spec.samples.map { HeartRateRecord.Sample(Instant.ofEpochMilli(it.ts), it.bpm.toLong()) },
                metadata = metadata,
            )
        }
    }

    private fun offsetAt(instant: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
}
