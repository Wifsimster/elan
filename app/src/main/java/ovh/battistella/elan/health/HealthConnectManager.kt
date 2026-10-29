package ovh.battistella.elan.health

import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.tracking.HealthExport
import ovh.battistella.elan.tracking.SavedSessionData
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthEnableResult { GRANTED, DENIED, UNAVAILABLE }

@Singleton
class HealthConnectManager @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val settings: SettingsRepository,
) : HealthExport {

    val isSupported: Boolean
        get() = try {
            gateway.sdkStatus() != HealthSdkStatus.UNAVAILABLE
        } catch (e: Exception) {
            false
        }

    private val _permissionRequests = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    val permissionRequests: SharedFlow<Set<String>> = _permissionRequests.asSharedFlow()

    private val enableLock = Mutex()
    private var pendingResult: CompletableDeferred<Set<String>>? = null

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = gateway.requestPermissionContract()

    fun onPermissionResult(granted: Set<String>) {
        pendingResult?.complete(granted)
    }

    suspend fun isEnabled(): Boolean = settings.settings.map { it.healthConnect }.first()

    suspend fun enable(): HealthEnableResult = enableLock.withLock {
        try {
            if (gateway.sdkStatus() != HealthSdkStatus.AVAILABLE) return HealthEnableResult.UNAVAILABLE
            val required = HealthRecordType.WRITE_PERMISSIONS
            val granted = if (gateway.grantedPermissions().containsAll(required)) {
                required
            } else {
                val deferred = CompletableDeferred<Set<String>>()
                pendingResult = deferred
                try {
                    if (!_permissionRequests.tryEmit(required)) return HealthEnableResult.UNAVAILABLE
                    deferred.await()
                } finally {
                    pendingResult = null
                }
            }
            if (!granted.containsAll(required)) return HealthEnableResult.DENIED
            settings.setHealthConnect(true)
            HealthEnableResult.GRANTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "activation Health Connect impossible", e)
            HealthEnableResult.UNAVAILABLE
        }
    }

    suspend fun disable() = settings.setHealthConnect(false)

    suspend fun exportSession(data: HealthSessionData) {
        try {
            if (!isEnabled()) return
            if (gateway.sdkStatus() != HealthSdkStatus.AVAILABLE) return
            val granted = gateway.grantedPermissions()
            if (HealthRecordType.EXERCISE_SESSION.writePermission !in granted) return

            for (record in buildHealthRecords(data)) {
                if (record.type.writePermission !in granted) continue
                try {
                    gateway.insert(record)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "insertion ${record.type} ignorée", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "export Health Connect ignoré", e)
        }
    }

    suspend fun removeSession(type: ActivityType, startedAt: Long) {
        try {
            if (!isEnabled()) return
            if (gateway.sdkStatus() != HealthSdkStatus.AVAILABLE) return
            for (recordType in HealthRecordType.entries) {
                try {
                    gateway.deleteByClientIds(recordType, listOf(healthClientRecordId(type, startedAt, recordType.suffix)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "suppression ${recordType} ignorée", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "suppression Health Connect ignorée", e)
        }
    }

    override suspend fun export(data: SavedSessionData) = exportSession(
        HealthSessionData(
            type = data.type,
            startedAt = data.startedAt,
            endedAt = data.endedAt,
            distanceM = data.distanceM,
            calories = data.calories,
            hrSamples = data.hrSamples,
        ),
    )

    override suspend fun remove(type: ActivityType, startedAt: Long) = removeSession(type, startedAt)

    companion object {
        private const val TAG = "HealthConnect"

        const val ACTION_SHOW_PERMISSIONS_RATIONALE = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"

        val RATIONALE_ACTIONS: Set<String> = setOf(ACTION_SHOW_PERMISSIONS_RATIONALE, "android.intent.action.VIEW_PERMISSION_USAGE")
    }
}
