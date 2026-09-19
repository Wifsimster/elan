// Import de fichiers Strava (GPX/TCX/FIT, y compris .gz de l'export en masse)
// — port de `use-strava-import.tsx` : lecture locale, décodage, déduplication
// et insertion. 100 % hors-ligne, aucun appel réseau. La sélection des
// fichiers (SAF) reste à l'écran, qui passe ici les URIs obtenues.
package ovh.battistella.elan.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ovh.battistella.elan.data.repository.ImportResult
import ovh.battistella.elan.data.repository.ImportedSession
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.TrackPointInput
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.strava.ImportedDraft
import ovh.battistella.elan.domain.strava.StravaImport
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Bilan d'un import multi-fichiers. */
data class ImportReport(
    val imported: Int,
    val duplicates: Int,
    val skipped: Int,
    val errors: Int,
    /** Détails par fichier/activité (motifs d'ignorés et erreurs), en français. */
    val details: List<String>,
)

@Singleton
class StravaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) {
    /**
     * Importe chaque fichier : une erreur (fichier illisible, trop volumineux)
     * n'interrompt pas les autres. Les activités déjà importées (même
     * `externalId`) sont comptées en doublons sans rien écrire.
     */
    suspend fun importUris(uris: List<Uri>): ImportReport = withContext(io) {
        var imported = 0
        var duplicates = 0
        var skipped = 0
        var errors = 0
        val details = ArrayList<String>()
        val weightKg = settings.getProfile().weightKg

        for (uri in uris) {
            val name = displayName(uri)
            try {
                val bytes = readBytes(uri)
                if (bytes == null) {
                    errors++
                    details.add("$name : fichier trop volumineux")
                    continue
                }
                val build = StravaImport.buildDrafts(bytes, weightKg)
                for (reason in build.skipped) {
                    skipped++
                    details.add("$name : $reason")
                }
                for (draft in build.drafts) {
                    when (sessions.insertImportedSession(draft.toImportedSession(), draft.toPoints())) {
                        is ImportResult.Imported -> imported++
                        ImportResult.Duplicate -> duplicates++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors++
                details.add("$name : ${e.message ?: "fichier illisible"}")
            }
        }
        ImportReport(imported, duplicates, skipped, errors, details)
    }

    /** Nom affichable du fichier (colonne `DISPLAY_NAME`, sinon dernier segment de l'URI). */
    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    /**
     * Lit le fichier entier, ou `null` s'il dépasse [MAX_BYTES] (taille
     * annoncée par le fournisseur, puis vérifiée pendant la lecture : la taille
     * annoncée n'est pas toujours fiable).
     */
    private fun readBytes(uri: Uri): ByteArray? {
        val declared = declaredSize(uri)
        if (declared != null && declared > MAX_BYTES) return null
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("fichier inaccessible")
        input.use { stream ->
            val out = ByteArrayOutputStream(declared?.toInt()?.coerceAtLeast(1024) ?: 64 * 1024)
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun declaredSize(uri: Uri): Long? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx)
                }
            }
        }
        return null
    }

    private fun ImportedDraft.toImportedSession() = ImportedSession(
        type = session.type,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
        durationSec = session.durationSec,
        movingTimeSec = session.movingTimeSec,
        notes = session.notes,
        avgHr = session.avgHr,
        maxHr = session.maxHr,
        distanceM = session.distanceM,
        avgSpeedKmh = session.avgSpeedKmh,
        maxSpeedKmh = session.maxSpeedKmh,
        elevationGainM = session.elevationGainM,
        avgCadence = session.avgCadence,
        maxCadence = session.maxCadence,
        calories = session.calories,
        source = session.source,
        externalId = session.externalId,
    )

    private fun ImportedDraft.toPoints() = points.map {
        TrackPointInput(it.ts, it.lat, it.lon, it.altitude, it.speedKmh, it.hr, it.cadence)
    }

    companion object {
        /** Plafond de taille par fichier (octets) — garde-fou mémoire / DoS. */
        const val MAX_BYTES = 30L * 1024 * 1024
    }
}
