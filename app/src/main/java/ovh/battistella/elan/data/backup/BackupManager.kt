package ovh.battistella.elan.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.data.remote.S3Client
import ovh.battistella.elan.data.remote.S3Config
import ovh.battistella.elan.data.repository.SnapshotRepository
import ovh.battistella.elan.data.secrets.SecretStore
import ovh.battistella.elan.data.settings.BackupConfig
import ovh.battistella.elan.data.settings.BackupLast
import ovh.battistella.elan.data.settings.DEFAULT_BACKUP_OBJECT_KEY
import ovh.battistella.elan.data.settings.DEFAULT_S3_REGION
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class BackupConfigFull(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val region: String = DEFAULT_S3_REGION,
    val bucket: String = "",
    val objectKey: String = DEFAULT_BACKUP_OBJECT_KEY,
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
) {
    fun toSettings(): BackupConfig = BackupConfig(enabled, endpoint, region, bucket, objectKey)

    fun toS3Config(): S3Config = S3Config(endpoint, region, bucket, accessKeyId, secretAccessKey, objectKey)
}

data class BackupConfigPatch(
    val enabled: Boolean? = null,
    val endpoint: String? = null,
    val region: String? = null,
    val bucket: String? = null,
    val objectKey: String? = null,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
)

fun BackupQrPatch.toConfigPatch(): BackupConfigPatch = BackupConfigPatch(
    endpoint = this[BackupField.ENDPOINT],
    bucket = this[BackupField.BUCKET],
    accessKeyId = this[BackupField.ACCESS_KEY_ID],
    secretAccessKey = this[BackupField.SECRET_ACCESS_KEY],
    region = this[BackupField.REGION],
    objectKey = this[BackupField.OBJECT_KEY],
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun effectiveConfig(c: BackupConfigFull): BackupConfigFull = c.copy(
    endpoint = c.endpoint.trim(),
    bucket = c.bucket.trim(),
    accessKeyId = c.accessKeyId.trim(),
    secretAccessKey = c.secretAccessKey.trim(),
    region = c.region.trim().ifEmpty { DEFAULT_S3_REGION },
    objectKey = c.objectKey.trim().ifEmpty { DEFAULT_BACKUP_OBJECT_KEY },
)

fun isConfigComplete(c: BackupConfigFull): Boolean {
    val e = effectiveConfig(c)
    return e.endpoint.isNotEmpty() && e.bucket.isNotEmpty() && e.accessKeyId.isNotEmpty() && e.secretAccessKey.isNotEmpty()
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val snapshots: SnapshotRepository,
    private val s3: S3Client,
    private val clock: Clock,
) {
    private data class Secrets(val accessKeyId: String, val secretAccessKey: String)

    private val secretsState = MutableStateFlow<Secrets?>(null)
    private val secretsLock = Mutex()

    private val backupLock = Mutex()

    val config: Flow<BackupConfigFull> = flow {
        loadSecrets()
        emitAll(
            settings.settings.map { it.backupConfig }.distinctUntilChanged()
                .combine(secretsState.filterNotNull()) { c, s -> full(c, s) },
        )
    }

    suspend fun currentConfig(): BackupConfigFull = full(settings.snapshot().backupConfig, loadSecrets())

    suspend fun secretsMissing(): Boolean = settings.getSetting(Keys.BACKUP_SECRETS_MISSING) == "1"

    suspend fun updateConfig(patch: BackupConfigPatch) {
        val current = currentConfig()
        val next = current.copy(
            enabled = patch.enabled ?: current.enabled,
            endpoint = patch.endpoint ?: current.endpoint,
            region = patch.region ?: current.region,
            bucket = patch.bucket ?: current.bucket,
            objectKey = patch.objectKey ?: current.objectKey,
            accessKeyId = patch.accessKeyId ?: current.accessKeyId,
            secretAccessKey = patch.secretAccessKey ?: current.secretAccessKey,
        )
        if (patch.accessKeyId != null || patch.secretAccessKey != null) {
            persistSecrets(next.accessKeyId, next.secretAccessKey)
        }
        settings.setBackupConfig(next.toSettings())
        if (next.accessKeyId.isNotEmpty() && next.secretAccessKey.isNotEmpty()) {
            settings.deleteSetting(Keys.BACKUP_SECRETS_MISSING)
        }
    }

    suspend fun runBackup(config: BackupConfigFull? = null): BackupLast = backupLock.withLock {
        val cfg = effectiveConfig(config ?: currentConfig())
        if (!isConfigComplete(cfg)) throw BackupException("Configuration S3 incomplète.")

        val file = File(context.cacheDir, "backup/$UPLOAD_FILE")
        try {
            BackupSnapshotCodec.write(snapshots.exportAll(), clock.millis(), file)
            s3.putObject(cfg.toS3Config(), file)
            val last = BackupLast(at = clock.millis(), ok = true)
            settings.setBackupLast(last)
            last
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val last = BackupLast(at = clock.millis(), ok = false, error = e.message ?: "Échec de la sauvegarde.")
            settings.setBackupLast(last)
            throw if (e is BackupException) e else BackupException(last.error!!, e)
        } finally {
            file.delete()
        }
    }

    suspend fun autoBackup() {
        try {
            val cfg = currentConfig()
            if (!cfg.enabled || !isConfigComplete(cfg)) return
            runBackup(cfg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    suspend fun restoreBackup(config: BackupConfigFull? = null): Int = backupLock.withLock {
        val cfg = effectiveConfig(config ?: currentConfig())
        if (!isConfigComplete(cfg)) throw BackupException("Configuration S3 incomplète.")

        val file = File(context.cacheDir, "backup/$DOWNLOAD_FILE")
        try {
            s3.getObject(cfg.toS3Config(), file) ?: throw BackupException("Aucune sauvegarde trouvée sur le serveur.")
            val parsed = try {
                BackupSnapshotCodec.read(file)
            } catch (e: BackupParseException) {
                throw BackupException("Sauvegarde illisible (JSON invalide).", e)
            }
            val data = parsed.data
            if (parsed.app != BACKUP_APP || data == null) throw BackupException("Format de sauvegarde non reconnu.")
            if ((parsed.format ?: 1) > BACKUP_FORMAT) {
                throw BackupException(
                    "Sauvegarde créée par une version plus récente (format ${parsed.format}). Mets l'application à jour.",
                )
            }
            if ((parsed.schema ?: BACKUP_SCHEMA) > BACKUP_SCHEMA) {
                throw BackupException(
                    "Sauvegarde créée par une version plus récente (schéma ${parsed.schema}). Mets l'application à jour.",
                )
            }
            snapshots.importAll(data)
            data.sessions.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(e.message ?: "Échec de la restauration.", e)
        } finally {
            file.delete()
        }
    }

    private suspend fun loadSecrets(): Secrets {
        secretsState.value?.let { return it }
        return secretsLock.withLock {
            secretsState.value?.let { return it }
            val raw = settings.getSetting(Keys.BACKUP_S3)
            val legacy = SettingsJson.legacyBackupSecrets(raw)
            if (legacy != null) {
                persistSecrets(legacy.first, legacy.second)
                settings.setBackupConfig(SettingsJson.parseBackupConfig(raw))
            }
            val loaded = Secrets(
                accessKeyId = secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID) ?: "",
                secretAccessKey = secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY) ?: "",
            )
            secretsState.value = loaded
            loaded
        }
    }

    private suspend fun persistSecrets(accessKeyId: String, secretAccessKey: String) {
        if (accessKeyId.isNotEmpty()) secrets.put(SecretStore.BACKUP_S3_ACCESS_KEY_ID, accessKeyId)
        else secrets.remove(SecretStore.BACKUP_S3_ACCESS_KEY_ID)
        if (secretAccessKey.isNotEmpty()) secrets.put(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY, secretAccessKey)
        else secrets.remove(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY)
        secretsState.value = Secrets(accessKeyId, secretAccessKey)
    }

    private fun full(c: BackupConfig, s: Secrets) = BackupConfigFull(
        enabled = c.enabled,
        endpoint = c.endpoint,
        region = c.region,
        bucket = c.bucket,
        objectKey = c.objectKey,
        accessKeyId = s.accessKeyId,
        secretAccessKey = s.secretAccessKey,
    )

    private companion object {
        const val UPLOAD_FILE = "elan-backup-upload.json"
        const val DOWNLOAD_FILE = "elan-backup-download.json"
    }
}
