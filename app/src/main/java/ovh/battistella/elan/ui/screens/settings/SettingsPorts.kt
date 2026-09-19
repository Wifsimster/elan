// Ports consommés par l'écran Réglages (et par le détail de séance pour les
// exports) vers des services livrés en parallèle : sauvegarde S3, exports
// coach/GPX, import Strava, Health Connect, rappels et fond de carte. Les
// écrans ne dépendent que de ces interfaces ; `SettingsModule` les lie à des
// implémentations (stubs tant que les services n'existent pas).
package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.flow.Flow
import ovh.battistella.elan.data.settings.DEFAULT_BACKUP_OBJECT_KEY
import ovh.battistella.elan.data.settings.DEFAULT_S3_REGION
import java.io.File

// ---- sauvegarde S3 ---------------------------------------------------------

/**
 * Configuration S3 telle que les écrans la saisissent : les champs de la table
 * `settings` (`backup_s3`) PLUS les deux secrets du `SecretStore`. Le port
 * fusionne les deux sources ; les écrans ne savent pas où vit chaque champ.
 */
data class BackupFormConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val region: String = DEFAULT_S3_REGION,
    val bucket: String = "",
    val objectKey: String = DEFAULT_BACKUP_OBJECT_KEY,
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
) {
    /** Endpoint, bucket et les deux clés renseignés : le serveur peut être contacté. */
    val complete: Boolean
        get() = endpoint.isNotBlank() && bucket.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()
}

/** Fusion partielle : seuls les champs non nuls sont réécrits (`Partial<BackupConfig>`). */
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

/**
 * Sauvegarde / restauration vers le stockage S3 auto-hébergé (`data/backup/`).
 * Les opérations réseau lèvent une exception dont le message est lisible en
 * français (`describeS3Failure` / `describeNetworkFailure`) ; les écrans
 * l'affichent tel quel. Le statut de la dernière sauvegarde (`backup_last`)
 * se lit dans `SettingsRepository`, pas ici.
 */
interface BackupPort {
    val config: Flow<BackupFormConfig>

    /** Fusionne [patch] et persiste (réglage + secrets). */
    suspend fun updateConfig(patch: BackupPatch)

    /** Sauvegarde immédiate ; lève en cas d'échec (config incomplète, réseau, S3). */
    suspend fun runBackup()

    /** Remplace les données locales par le snapshot du serveur ; renvoie le nombre de séances restaurées. */
    suspend fun restoreBackup(): Int

    /** Décode un QR de configuration (JSON ou `s3://…`) ; `null` si non reconnu (`parseBackupQr`). */
    fun parseQr(text: String): BackupPatch?

    /** Libellés FR des champs présents dans un patch QR (`describeQrPatch`). */
    fun describeQrPatch(patch: BackupPatch): String
}

// ---- exports ---------------------------------------------------------------

/**
 * Exports de fichiers (`data/export/`) : bilan coach Markdown, données brutes
 * JSON, GPX d'une sortie, et partage via la feuille système. Les fichiers
 * sont écrits dans le cache de l'app ; le partage passe par un `FileProvider`.
 */
interface ExportPort {
    /** `CoachExporter.exportMarkdown` */
    suspend fun exportMarkdown(context: Context): File

    /** `CoachExporter.exportJson` */
    suspend fun exportJson(context: Context): File

    /** `GpxExporter.exportSession` : `null` si la séance n'a pas de tracé exploitable (< 2 points). */
    suspend fun exportGpx(context: Context, sessionId: Long, privacyZoneM: Double): File?

    /** `FileShare.share` : ouvre la feuille de partage système sur [file]. */
    fun share(context: Context, file: File, mime: String, title: String)
}

// ---- import Strava ---------------------------------------------------------

/** Bilan d'un import de fichiers Strava (`StravaImporter.ImportReport`). */
data class ImportReport(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
    val details: List<String> = emptyList(),
)

/** Import de fichiers GPX/TCX/FIT choisis par l'utilisateur (`StravaImporter.importUris`). */
interface StravaImportPort {
    suspend fun importUris(uris: List<Uri>): ImportReport
}

// ---- Health Connect --------------------------------------------------------

/** Issue d'une demande de permissions Health Connect. */
enum class HealthConnectOutcome { Granted, Denied, Unavailable }

/**
 * Export opt-in des séances vers Android Health Connect (`health/`). La carte
 * n'est affichée que si [isSupported] ; les permissions ne sont demandées qu'à
 * l'activation du commutateur, via [permissionContract].
 */
interface HealthConnectPort {
    /** Health Connect existe sur cet appareil (sinon la carte est masquée). */
    val isSupported: Boolean

    /** Réglage `health_connect`. */
    val enabled: Flow<Boolean>

    /** Permissions d'écriture demandées (entrée du contrat). */
    val permissions: Set<String>

    /** Contrat système de demande de permissions (résultat = permissions accordées). */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>

    /** Applique le résultat du contrat : active l'export si tout est accordé. */
    suspend fun onPermissionResult(granted: Set<String>): HealthConnectOutcome

    suspend fun enable()

    suspend fun disable()
}

// ---- rappels ---------------------------------------------------------------

/** Replanification idempotente des rappels de séance (`ReminderScheduler.apply`). */
interface RemindersPort {
    suspend fun apply()
}

// ---- fond de carte ---------------------------------------------------------

/** Style MapLibre en ligne, opt-in (`maps/MapStyle.kt`) : '' = carte hors ligne. */
interface MapStylePort {
    /** `MapStyleRepository.styleUrl` — '' tant que la carte en ligne n'est pas activée. */
    val styleUrl: Flow<String>

    /** `MapStyleRepository.setStyleUrl` — l'appelant a validé l'URL (HTTPS ou vide). */
    suspend fun setStyleUrl(url: String)

    /** `OPENFREEMAP_STYLE_URL` : preset gratuit, sans clé. */
    val openFreeMapStyleUrl: String

    /** `isValidMapStyleUrl` : vide ou `https://…`. */
    fun isValidMapStyleUrl(url: String): Boolean
}
