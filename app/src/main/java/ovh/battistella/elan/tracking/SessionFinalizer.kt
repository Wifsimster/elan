package ovh.battistella.elan.tracking

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ovh.battistella.elan.di.ApplicationScope
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.HrSample
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

data class SavedSessionData(
    val type: ActivityType,
    val startedAt: Long,
    val endedAt: Long,
    val distanceM: Double? = null,
    val calories: Double? = null,
    val hrSamples: List<HrSample> = emptyList(),
)

interface BackupTrigger {
    fun autoBackup()
}

interface HealthExport {
    suspend fun export(data: SavedSessionData)
    suspend fun remove(type: ActivityType, startedAt: Long)
}

@Singleton
class SessionFinalizer @Inject constructor(
    private val backup: Optional<BackupTrigger>,
    private val health: Optional<HealthExport>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun onSaved(data: SavedSessionData) {
        runBackup()
        scope.launch {
            guarded { health.orElse(null)?.export(data) }
        }
    }

    fun onRetyped(previousType: ActivityType, data: SavedSessionData) {
        runBackup()
        scope.launch {
            guarded {
                val export = health.orElse(null) ?: return@guarded
                export.remove(previousType, data.startedAt)
                export.export(data)
            }
        }
    }

    private fun runBackup() {
        try {
            backup.orElse(null)?.autoBackup()
        } catch (e: Exception) {
            Log.w(TAG, "sauvegarde automatique ignorée", e)
        }
    }

    private suspend inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "miroir Health Connect ignoré", e)
        }
    }

    private companion object {
        const val TAG = "SessionFinalizer"
    }
}
