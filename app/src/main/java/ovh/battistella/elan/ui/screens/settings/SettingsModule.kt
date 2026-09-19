// Liaisons Hilt des ports de l'écran Réglages. Tant que les services réels
// (`data/backup`, `data/export`, `health`, `sync/ReminderScheduler`, `maps`)
// ne sont pas livrés, des implémentations de transition sont liées ici : la
// configuration (S3, carte, Health Connect) est bien persistée via
// `SettingsRepository` / `SecretStore`, mais les opérations réseau ou fichier
// signalent proprement leur indisponibilité. À remplacer par les vraies
// classes, liaison par liaison.
package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject
import ovh.battistella.elan.data.secrets.SecretStore
import ovh.battistella.elan.data.settings.BackupConfig
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideBackupPort(settings: SettingsRepository, secrets: SecretStore): BackupPort =
        TransitionalBackupPort(settings, secrets)

    @Provides
    @Singleton
    fun provideExportPort(): ExportPort = UnavailableExportPort()

    @Provides
    @Singleton
    fun provideStravaImportPort(): StravaImportPort = UnavailableStravaImportPort()

    @Provides
    @Singleton
    fun provideHealthConnectPort(settings: SettingsRepository): HealthConnectPort =
        UnsupportedHealthConnectPort(settings)

    @Provides
    @Singleton
    fun provideRemindersPort(): RemindersPort = NoOpRemindersPort()

    @Provides
    @Singleton
    fun provideMapStylePort(settings: SettingsRepository): MapStylePort = SettingsMapStylePort(settings)
}

/** Message des opérations pas encore câblées. */
internal const val FEATURE_UNAVAILABLE = "Fonction indisponible dans cette version."

/**
 * Configuration S3 réelle (réglage `backup_s3` + secrets du [SecretStore]),
 * opérations réseau indisponibles. Le décodage QR suit le format documenté :
 * JSON à clés insensibles à la casse (alias tolérés) ou URL `s3://`.
 */
class TransitionalBackupPort(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) : BackupPort {
    // Les secrets ne sont pas observables : on les relit à chaque émission du
    // réglage, et après chaque écriture (tick).
    private val secretsTick = MutableStateFlow(0)

    override val config: Flow<BackupFormConfig> = settings.settings
        .map { it.backupConfig }
        .distinctUntilChanged()
        .combine(secretsTick) { c, _ -> c }
        .map { c ->
            BackupFormConfig(
                enabled = c.enabled,
                endpoint = c.endpoint,
                region = c.region,
                bucket = c.bucket,
                objectKey = c.objectKey,
                accessKeyId = secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID) ?: "",
                secretAccessKey = secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY) ?: "",
            )
        }

    override suspend fun updateConfig(patch: BackupPatch) {
        val current = config.first()
        val next = patch.applyTo(current)
        settings.setBackupConfig(
            BackupConfig(
                enabled = next.enabled,
                endpoint = next.endpoint,
                region = next.region,
                bucket = next.bucket,
                objectKey = next.objectKey,
            ),
        )
        patch.accessKeyId?.let { secrets.put(SecretStore.BACKUP_S3_ACCESS_KEY_ID, it) }
        patch.secretAccessKey?.let { secrets.put(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY, it) }
        secretsTick.value++
    }

    override suspend fun runBackup(): Unit = throw IllegalStateException(FEATURE_UNAVAILABLE)

    override suspend fun restoreBackup(): Int = throw IllegalStateException(FEATURE_UNAVAILABLE)

    override fun parseQr(text: String): BackupPatch? = parseBackupQrText(text)

    override fun describeQrPatch(patch: BackupPatch): String = describeBackupPatch(patch)
}

private val QR_ALIASES: Map<String, List<String>> = mapOf(
    "endpoint" to listOf("endpoint", "url", "endpoint_url"),
    "bucket" to listOf("bucket"),
    "accessKeyId" to listOf("accesskeyid", "accesskey", "access_key", "access_key_id", "aws_access_key_id"),
    "secretAccessKey" to listOf("secretaccesskey", "secretkey", "secret_key", "secret_access_key", "aws_secret_access_key"),
    "region" to listOf("region"),
    "objectKey" to listOf("objectkey", "object_key", "key", "object"),
)

/** JSON ou `s3://[AK[:SK]@]host[/bucket[/objectKey]]` ; valeurs trimées, vides ignorées ; `enabled` jamais renseigné. */
internal fun parseBackupQrText(text: String): BackupPatch? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    val fields = HashMap<String, String>()
    if (raw.startsWith("s3://", ignoreCase = true)) {
        val rest = raw.substring(5)
        val at = rest.lastIndexOf('@')
        val creds = if (at >= 0) rest.substring(0, at) else null
        val target = if (at >= 0) rest.substring(at + 1) else rest
        val parts = target.split('/', limit = 3)
        val host = parts[0]
        if (host.isBlank()) return null
        fields["endpoint"] = "https://$host"
        parts.getOrNull(1)?.let { fields["bucket"] = it }
        parts.getOrNull(2)?.let { fields["objectKey"] = it }
        if (creds != null) {
            val colon = creds.indexOf(':')
            fields["accessKeyId"] = decode(if (colon >= 0) creds.substring(0, colon) else creds)
            if (colon >= 0) fields["secretAccessKey"] = decode(creds.substring(colon + 1))
        }
    } else {
        val o = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return null
        }
        val byLower = o.keys().asSequence().associateBy { it.lowercase() }
        for ((field, aliases) in QR_ALIASES) {
            val key = aliases.firstNotNullOfOrNull { byLower[it] } ?: continue
            val v = o.opt(key)
            if (v is String || v is Number) fields[field] = v.toString()
        }
    }
    val clean = fields.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
    val patch = BackupPatch(
        endpoint = clean["endpoint"],
        bucket = clean["bucket"],
        accessKeyId = clean["accessKeyId"],
        secretAccessKey = clean["secretAccessKey"],
        region = clean["region"],
        objectKey = clean["objectKey"],
    )
    return if (patch.isEmpty) null else patch
}

private fun decode(s: String): String = try {
    Uri.decode(s) ?: s
} catch (e: Exception) {
    s
}

internal fun describeBackupPatch(p: BackupPatch): String = buildList {
    if (p.endpoint != null) add("endpoint")
    if (p.bucket != null) add("bucket")
    if (p.accessKeyId != null) add("access key")
    if (p.secretAccessKey != null) add("secret key")
    if (p.region != null) add("région")
    if (p.objectKey != null) add("nom de l'objet")
}.joinToString(", ")

class UnavailableExportPort : ExportPort {
    override suspend fun exportMarkdown(context: Context): File = throw IllegalStateException(FEATURE_UNAVAILABLE)
    override suspend fun exportJson(context: Context): File = throw IllegalStateException(FEATURE_UNAVAILABLE)
    override suspend fun exportGpx(context: Context, sessionId: Long, privacyZoneM: Double): File? =
        throw IllegalStateException(FEATURE_UNAVAILABLE)
    override fun share(context: Context, file: File, mime: String, title: String): Unit =
        throw IllegalStateException(FEATURE_UNAVAILABLE)
}

class UnavailableStravaImportPort : StravaImportPort {
    override suspend fun importUris(uris: List<Uri>): ImportReport = throw IllegalStateException(FEATURE_UNAVAILABLE)
}

/** Contrat inerte : aucune activité lancée, résultat vide immédiat. */
object NoOpPermissionContract : ActivityResultContract<Set<String>, Set<String>>() {
    override fun createIntent(context: Context, input: Set<String>): Intent = Intent()
    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = emptySet()
    override fun getSynchronousResult(context: Context, input: Set<String>): SynchronousResult<Set<String>> =
        SynchronousResult(emptySet())
}

/** Health Connect absent : carte masquée ; le réglage reste lisible/écrivable. */
class UnsupportedHealthConnectPort(private val settings: SettingsRepository) : HealthConnectPort {
    override val isSupported: Boolean = false
    override val enabled: Flow<Boolean> = settings.settings.map { it.healthConnect }.distinctUntilChanged()
    override val permissions: Set<String> = emptySet()
    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = NoOpPermissionContract
    override suspend fun onPermissionResult(granted: Set<String>): HealthConnectOutcome = HealthConnectOutcome.Unavailable
    override suspend fun enable() = settings.setHealthConnect(true)
    override suspend fun disable() = settings.setHealthConnect(false)
}

class NoOpRemindersPort : RemindersPort {
    override suspend fun apply() = Unit
}

/** Réglage `map_style_url` directement dans `SettingsRepository` (déjà validé HTTPS à la lecture). */
class SettingsMapStylePort(private val settings: SettingsRepository) : MapStylePort {
    override val styleUrl: Flow<String> = settings.settings.map { it.mapStyleUrl }.distinctUntilChanged()
    override suspend fun setStyleUrl(url: String) = settings.setMapStyleUrl(url)
    override val openFreeMapStyleUrl: String = "https://tiles.openfreemap.org/styles/liberty"
    override fun isValidMapStyleUrl(url: String): Boolean = SettingsJson.isValidMapStyleUrl(url)
}
