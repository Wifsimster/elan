// Doublures des ports de l'écran Réglages : état en mémoire, appels
// journalisés, pannes injectables.
package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

open class FakeBackupPort(initial: BackupFormConfig = BackupFormConfig()) : BackupPort {
    val state = MutableStateFlow(initial)
    override val config: Flow<BackupFormConfig> = state
    val patches = mutableListOf<BackupPatch>()
    var backups = 0
    var restores = 0
    var restoreCount = 12
    var failure: Exception? = null
    var qrPatch: BackupPatch? = null

    override suspend fun updateConfig(patch: BackupPatch) {
        patches += patch
        state.value = patch.applyTo(state.value)
    }

    override suspend fun runBackup() {
        backups++
        failure?.let { throw it }
    }

    override suspend fun restoreBackup(): Int {
        restores++
        failure?.let { throw it }
        return restoreCount
    }

    override fun parseQr(text: String): BackupPatch? = qrPatch ?: parseBackupQrPatch(text)

    override fun describeQrPatch(patch: BackupPatch): String = describeBackupQrPatch(patch)
}

class FakeExportPort : ExportPort {
    val calls = mutableListOf<String>()
    val shared = mutableListOf<Triple<File, String, String>>()
    var gpxResult: File? = File("elan.gpx")
    var failure: Exception? = null

    override suspend fun exportMarkdown(context: Context): File {
        calls += "markdown"
        failure?.let { throw it }
        return File(context.cacheDir, "suivi-sport-coach.md")
    }

    override suspend fun exportJson(context: Context): File {
        calls += "json"
        failure?.let { throw it }
        return File(context.cacheDir, "suivi-sport-export.json")
    }

    override suspend fun exportGpx(context: Context, sessionId: Long, privacyZoneM: Double): File? {
        calls += "gpx:$sessionId:${privacyZoneM.toInt()}"
        failure?.let { throw it }
        return gpxResult
    }

    override fun share(context: Context, file: File, mime: String, title: String) {
        shared += Triple(file, mime, title)
    }
}

class FakeStravaImportPort(var report: ImportReport = ImportReport(imported = 2, duplicates = 1, skipped = 0, errors = 0)) : StravaImportPort {
    val imported = mutableListOf<List<Uri>>()
    var failure: Exception? = null

    override suspend fun importUris(uris: List<Uri>): ImportReport {
        imported += uris
        failure?.let { throw it }
        return report
    }
}

/** Contrat inerte : aucune activité lancée, résultat vide immédiat. */
object NoOpPermissionContract : ActivityResultContract<Set<String>, Set<String>>() {
    override fun createIntent(context: Context, input: Set<String>): Intent = Intent()
    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = emptySet()
    override fun getSynchronousResult(context: Context, input: Set<String>): SynchronousResult<Set<String>> =
        SynchronousResult(emptySet())
}

class FakeHealthConnectPort(
    override val isSupported: Boolean = true,
    var outcome: HealthConnectOutcome = HealthConnectOutcome.Granted,
) : HealthConnectPort {
    val state = MutableStateFlow(false)
    override val enabled: Flow<Boolean> = state
    override val permissions: Set<String> = setOf("android.permission.health.WRITE_EXERCISE")
    val results = mutableListOf<Set<String>>()

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = NoOpPermissionContract

    override suspend fun onPermissionResult(granted: Set<String>): HealthConnectOutcome {
        results += granted
        if (outcome == HealthConnectOutcome.Granted) state.value = true
        return outcome
    }

    override suspend fun enable() { state.value = true }
    override suspend fun disable() { state.value = false }
}

class FakeRemindersPort : RemindersPort {
    var applied = 0
    override suspend fun apply() { applied++ }
}

class FakeMapStylePort(initial: String = "") : MapStylePort {
    val state = MutableStateFlow(initial)
    override val styleUrl: Flow<String> = state
    override suspend fun setStyleUrl(url: String) { state.value = url }
    override val openFreeMapStyleUrl: String = "https://tiles.openfreemap.org/styles/liberty"
    override fun isValidMapStyleUrl(url: String): Boolean = url.isEmpty() || url.startsWith("https://", ignoreCase = true)
}
