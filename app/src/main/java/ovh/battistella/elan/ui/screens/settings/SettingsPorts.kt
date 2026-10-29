package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.flow.Flow
import ovh.battistella.elan.data.settings.DEFAULT_BACKUP_OBJECT_KEY
import ovh.battistella.elan.data.settings.DEFAULT_S3_REGION
import java.io.File

data class BackupFormConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val region: String = DEFAULT_S3_REGION,
    val bucket: String = "",
    val objectKey: String = DEFAULT_BACKUP_OBJECT_KEY,
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
) {
    val complete: Boolean
        get() = endpoint.isNotBlank() && bucket.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()
}

data class BackupPatch(
    val enabled: Boolean? = null,
    val endpoint: String? = null,
    val region: String? = null,
    val bucket: String? = null,
    val objectKey: String? = null,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
) {
    val isEmpty: Boolean
        get() = enabled == null && endpoint == null && region == null && bucket == null &&
            objectKey == null && accessKeyId == null && secretAccessKey == null

    fun applyTo(c: BackupFormConfig): BackupFormConfig = c.copy(
        enabled = enabled ?: c.enabled,
        endpoint = endpoint ?: c.endpoint,
        region = region ?: c.region,
        bucket = bucket ?: c.bucket,
        objectKey = objectKey ?: c.objectKey,
        accessKeyId = accessKeyId ?: c.accessKeyId,
        secretAccessKey = secretAccessKey ?: c.secretAccessKey,
    )
}

interface BackupPort {
    val config: Flow<BackupFormConfig>

    suspend fun updateConfig(patch: BackupPatch)

    suspend fun runBackup()

    suspend fun restoreBackup(): Int

    fun parseQr(text: String): BackupPatch?

    fun describeQrPatch(patch: BackupPatch): String
}

interface ExportPort {
    suspend fun exportMarkdown(context: Context): File

    suspend fun exportJson(context: Context): File

    suspend fun exportGpx(context: Context, sessionId: Long, privacyZoneM: Double): File?

    fun share(context: Context, file: File, mime: String, title: String)
}

data class ImportReport(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
    val details: List<String> = emptyList(),
)

interface StravaImportPort {
    suspend fun importUris(uris: List<Uri>): ImportReport
}

enum class HealthConnectOutcome { Granted, Denied, Unavailable }

interface HealthConnectPort {
    val isSupported: Boolean

    val enabled: Flow<Boolean>

    val permissions: Set<String>

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>

    suspend fun onPermissionResult(granted: Set<String>): HealthConnectOutcome

    suspend fun enable()

    suspend fun disable()
}

interface RemindersPort {
    suspend fun apply()
}

interface MapStylePort {
    val styleUrl: Flow<String>

    suspend fun setStyleUrl(url: String)

    val openFreeMapStyleUrl: String

    fun isValidMapStyleUrl(url: String): Boolean
}
