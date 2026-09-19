package ovh.battistella.elan.di

import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ovh.battistella.elan.sync.BackupScheduler
import ovh.battistella.elan.tracking.BackupTrigger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Branche la sauvegarde automatique (`BackupTrigger`) sur WorkManager : la
 * liaison optionnelle déclarée dans `TrackingModule` reçoit désormais une
 * implémentation, sans rien changer au contrôleur de suivi.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    @Singleton
    abstract fun bindBackupTrigger(impl: WorkManagerBackupTrigger): BackupTrigger
}

/** Déclencheur : met la tâche `auto_backup` en file (best-effort, ne lève jamais). */
@Singleton
class WorkManagerBackupTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupTrigger {
    override fun autoBackup() {
        try {
            BackupScheduler.enqueue(context)
        } catch (e: Exception) {
            Log.w("BackupTrigger", "planification de la sauvegarde auto impossible", e)
        }
    }
}
