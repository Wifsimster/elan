// Planification de la sauvegarde automatique : une seule tâche `auto_backup`
// en file, contrainte au réseau, avec recul exponentiel entre les réessais.
package ovh.battistella.elan.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    const val UNIQUE_WORK = "auto_backup"

    /**
     * Met une sauvegarde en file. Si une tâche est déjà en attente ou en
     * cours, la nouvelle est chaînée derrière elle (`APPEND_OR_REPLACE`) : une
     * sauvegarde déjà lancée a pu lire la base AVANT la séance qui vient d'être
     * enregistrée, la suivante la rattrape ; une tâche échouée est remplacée.
     */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
