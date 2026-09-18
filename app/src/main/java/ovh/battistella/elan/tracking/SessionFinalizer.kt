// Effets de bord best-effort déclenchés APRÈS l'enregistrement local d'une
// séance terminée (port de session-finalize.ts). Partagé par la sortie GPS et
// la séance muscu pour leur garantir un comportement identique : la séance en
// base reste la source de vérité, ces effets sont optionnels (opt-in) et ne
// doivent jamais bloquer la navigation.
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

/** Ce qu'une séance enregistrée expose aux miroirs externes (équivalent de `HealthSessionData`). */
data class SavedSessionData(
    val type: ActivityType,
    val startedAt: Long,
    val endedAt: Long,
    val distanceM: Double? = null,
    val calories: Double? = null,
    val hrSamples: List<HrSample> = emptyList(),
)

/**
 * Sauvegarde homelab automatique (`autoBackup`). L'implémentation réelle (M3)
 * se lie via `@Binds` dans son propre module ; sans liaison, aucun effet.
 */
interface BackupTrigger {
    /** Lance une sauvegarde si configurée ; ne bloque pas, avale ses erreurs. */
    fun autoBackup()
}

/**
 * Miroir Health Connect (opt-in). L'implémentation réelle (M3) se lie via
 * `@Binds` dans son propre module ; sans liaison, aucun effet.
 */
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
    /**
     * Effets post-enregistrement : sauvegarde S3 (si configurée) puis miroir
     * Health Connect (si activé). « Fire-and-forget » : rien n'est attendu,
     * aucune erreur ne remonte.
     */
    fun onSaved(data: SavedSessionData) {
        runBackup()
        scope.launch {
            guarded { health.orElse(null)?.export(data) }
        }
    }

    /**
     * Effets après un CHANGEMENT DE TYPE d'une séance déjà enregistrée. Le
     * miroir Health Connect de l'ancien type est d'abord supprimé, sans quoi la
     * réécriture ajouterait un second enregistrement (l'identifiant client porte
     * le type). L'ordre compte, d'où l'enchaînement séquentiel.
     */
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
