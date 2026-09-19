// Sauvegarde S3 automatique en tâche WorkManager : déclenchée après chaque
// séance enregistrée (`SessionFinalizer` → `BackupTrigger`), elle attend une
// connexion réseau et réessaie avec recul exponentel — un tunnel ou un mode
// avion au moment de l'arrêt du chrono ne fait plus perdre la sauvegarde.
package ovh.battistella.elan.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import ovh.battistella.elan.data.backup.BackupManager
import ovh.battistella.elan.data.backup.isConfigComplete

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = execute(manager, runAttemptCount)

    companion object {
        /** Nombre maximal de tentatives (la première comprise). */
        const val MAX_ATTEMPTS = 3

        /**
         * Cœur testable du worker : rien à faire si la sauvegarde auto est
         * désactivée ou incomplète ; sinon sauvegarde, réessai sur échec tant
         * que le budget de tentatives n'est pas épuisé. L'échec reste consigné
         * dans `backup_last` par le gestionnaire, l'écran Réglages le montre.
         */
        suspend fun execute(manager: BackupManager, runAttemptCount: Int): Result {
            val cfg = try {
                manager.currentConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.failure()
            }
            if (!cfg.enabled || !isConfigComplete(cfg)) return Result.success()
            return try {
                manager.runBackup(cfg)
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        }
    }
}
