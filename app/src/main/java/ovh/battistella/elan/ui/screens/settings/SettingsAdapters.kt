// Adaptateurs des ports de l'écran Réglages vers les services réels :
// `BackupManager` (S3), `CoachExporter` / `GpxExporter` / `FileShare`,
// `StravaImporter`, `HealthConnectManager`, `ReminderScheduler` et
// `MapStyleRepository`. Chaque adaptateur se limite à la traduction des types
// (les écrans ne connaissent que les modèles de `SettingsPorts.kt`) ; toute la
// logique vit dans les services. Liés par `SettingsModule`.
package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.selects.select
import ovh.battistella.elan.data.backup.BackupConfigFull
import ovh.battistella.elan.data.backup.BackupConfigPatch
import ovh.battistella.elan.data.backup.BackupField
import ovh.battistella.elan.data.backup.BackupManager
import ovh.battistella.elan.data.backup.BackupQrPatch
import ovh.battistella.elan.data.backup.describeQrPatch
import ovh.battistella.elan.data.backup.parseBackupQr
import ovh.battistella.elan.data.backup.toConfigPatch
import ovh.battistella.elan.data.export.CoachExporter
import ovh.battistella.elan.data.export.FileShare
import ovh.battistella.elan.data.export.GpxExporter
import ovh.battistella.elan.data.export.StravaImporter
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.health.HealthConnectManager
import ovh.battistella.elan.health.HealthEnableResult
import ovh.battistella.elan.health.HealthRecordType
import ovh.battistella.elan.maps.MapStyleRepository
import ovh.battistella.elan.maps.OPENFREEMAP_STYLE_URL
import ovh.battistella.elan.sync.ReminderScheduler
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import ovh.battistella.elan.data.export.ImportReport as ExportImportReport
import ovh.battistella.elan.maps.isValidMapStyleUrl as mapsIsValidMapStyleUrl

// ---- sauvegarde S3 ---------------------------------------------------------

internal fun BackupConfigFull.toForm() = BackupFormConfig(
    enabled = enabled,
    endpoint = endpoint,
    region = region,
    bucket = bucket,
    objectKey = objectKey,
    accessKeyId = accessKeyId,
    secretAccessKey = secretAccessKey,
)

internal fun BackupPatch.toConfigPatch() = BackupConfigPatch(
    enabled = enabled,
    endpoint = endpoint,
    region = region,
    bucket = bucket,
    objectKey = objectKey,
    accessKeyId = accessKeyId,
    secretAccessKey = secretAccessKey,
)

internal fun BackupConfigPatch.toBackupPatch() = BackupPatch(
    enabled = enabled,
    endpoint = endpoint,
    region = region,
    bucket = bucket,
    objectKey = objectKey,
    accessKeyId = accessKeyId,
    secretAccessKey = secretAccessKey,
)

/** Champs renseignés d'un patch, dans l'ordre des libellés FR de [BackupField]. */
internal fun BackupPatch.toQrPatch(): BackupQrPatch = buildMap {
    endpoint?.let { put(BackupField.ENDPOINT, it) }
    bucket?.let { put(BackupField.BUCKET, it) }
    accessKeyId?.let { put(BackupField.ACCESS_KEY_ID, it) }
    secretAccessKey?.let { put(BackupField.SECRET_ACCESS_KEY, it) }
    region?.let { put(BackupField.REGION, it) }
    objectKey?.let { put(BackupField.OBJECT_KEY, it) }
}

/** Décodage d'un QR (JSON ou `s3://…`) en patch d'écran ; `null` si non reconnu. */
internal fun parseBackupQrPatch(text: String): BackupPatch? = parseBackupQr(text)?.toConfigPatch()?.toBackupPatch()

/** Libellés FR des champs présents dans [patch]. */
internal fun describeBackupQrPatch(patch: BackupPatch): String = describeQrPatch(patch.toQrPatch())

@Singleton
class BackupManagerPort @Inject constructor(private val manager: BackupManager) : BackupPort {
    override val config: Flow<BackupFormConfig> = manager.config.map { it.toForm() }

    override suspend fun updateConfig(patch: BackupPatch) = manager.updateConfig(patch.toConfigPatch())

    override suspend fun runBackup() {
        manager.runBackup()
    }

    override suspend fun restoreBackup(): Int = manager.restoreBackup()

    override fun parseQr(text: String): BackupPatch? = parseBackupQrPatch(text)

    override fun describeQrPatch(patch: BackupPatch): String = describeBackupQrPatch(patch)
}

// ---- exports ---------------------------------------------------------------

@Singleton
class ExportersPort @Inject constructor(
    private val coach: CoachExporter,
    private val gpx: GpxExporter,
) : ExportPort {
    override suspend fun exportMarkdown(context: Context): File = coach.exportMarkdown(context)

    override suspend fun exportJson(context: Context): File = coach.exportJson(context)

    override suspend fun exportGpx(context: Context, sessionId: Long, privacyZoneM: Double): File? =
        gpx.exportSession(context, sessionId, privacyZoneM)

    override fun share(context: Context, file: File, mime: String, title: String) = FileShare.share(context, file, mime, title)
}

// ---- import Strava ---------------------------------------------------------

internal fun ExportImportReport.toPort() = ImportReport(
    imported = imported,
    duplicates = duplicates,
    skipped = skipped,
    errors = errors,
    details = details,
)

@Singleton
class StravaImporterPort @Inject constructor(private val importer: StravaImporter) : StravaImportPort {
    override suspend fun importUris(uris: List<Uri>): ImportReport = importer.importUris(uris).toPort()
}

// ---- Health Connect --------------------------------------------------------

internal fun HealthEnableResult.toOutcome() = when (this) {
    HealthEnableResult.GRANTED -> HealthConnectOutcome.Granted
    HealthEnableResult.DENIED -> HealthConnectOutcome.Denied
    HealthEnableResult.UNAVAILABLE -> HealthConnectOutcome.Unavailable
}

/**
 * Le gestionnaire attend que l'UI réponde à sa demande de permissions
 * (`permissionRequests` → `onPermissionResult`) pendant `enable()`. La carte
 * Réglages, elle, lance le contrat système d'abord et ne rapporte que le
 * résultat : l'adaptateur rejoue donc `enable()` en répondant aussitôt à la
 * demande avec les permissions accordées, ce qui laisse au gestionnaire la
 * décision finale (disponibilité, ensemble complet, persistance du réglage).
 */
@Singleton
class HealthConnectManagerPort @Inject constructor(
    private val manager: HealthConnectManager,
    settings: SettingsRepository,
) : HealthConnectPort {
    override val isSupported: Boolean
        get() = manager.isSupported

    override val enabled: Flow<Boolean> = settings.settings.map { it.healthConnect }.distinctUntilChanged()

    override val permissions: Set<String>
        get() = HealthRecordType.WRITE_PERMISSIONS

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = manager.permissionContract()

    override suspend fun onPermissionResult(granted: Set<String>): HealthConnectOutcome = enableAnswering(granted)

    /** Activation sans UI : n'aboutit que si les permissions sont déjà accordées. */
    override suspend fun enable() {
        enableAnswering(emptySet())
    }

    override suspend fun disable() = manager.disable()

    private suspend fun enableAnswering(granted: Set<String>): HealthConnectOutcome = coroutineScope {
        // Abonnement AVANT `enable()` : le flux ne rejoue rien, une demande émise
        // sans abonné serait perdue et l'activation resterait suspendue.
        val request = async(start = CoroutineStart.UNDISPATCHED) { manager.permissionRequests.first() }
        val result = async { manager.enable() }
        select<Unit> {
            request.onAwait { manager.onPermissionResult(granted) }
            // Permissions déjà accordées ou Health Connect absent : aucune demande n'est émise.
            result.onJoin { request.cancel() }
        }
        result.await().toOutcome()
    }
}

// ---- rappels ---------------------------------------------------------------

@Singleton
class ReminderSchedulerPort @Inject constructor(private val scheduler: ReminderScheduler) : RemindersPort {
    override suspend fun apply() = scheduler.apply()
}

// ---- fond de carte ---------------------------------------------------------

@Singleton
class MapStyleRepositoryPort @Inject constructor(private val repository: MapStyleRepository) : MapStylePort {
    override val styleUrl: Flow<String> = repository.styleUrl

    override suspend fun setStyleUrl(url: String) = repository.setStyleUrl(url)

    override val openFreeMapStyleUrl: String = OPENFREEMAP_STYLE_URL

    override fun isValidMapStyleUrl(url: String): Boolean = mapsIsValidMapStyleUrl(url)
}
