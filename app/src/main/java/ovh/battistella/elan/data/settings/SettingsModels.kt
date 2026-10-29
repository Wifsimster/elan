package ovh.battistella.elan.data.settings

import ovh.battistella.elan.domain.ProgressionChange

data class BleDevice(val id: String, val name: String)

const val DEFAULT_S3_REGION = "us-east-1"

const val DEFAULT_BACKUP_OBJECT_KEY = "elan-backup.json"

data class BackupConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val region: String = DEFAULT_S3_REGION,
    val bucket: String = "",
    val objectKey: String = DEFAULT_BACKUP_OBJECT_KEY,
)

data class BackupLast(val at: Long, val ok: Boolean, val error: String? = null)

data class NotificationConfig(val enabled: Boolean = false, val hour: Int = 12)

data class AutoProgressionConfig(val enabled: Boolean = true)

data class AutoProgressionState(
    val week: String = "",
    val changes: List<ProgressionChange> = emptyList(),
    val dismissed: Boolean = false,
)
