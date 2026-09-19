// Liaisons Hilt des ports de l'écran Réglages vers leurs adaptateurs
// (`SettingsAdapters.kt`), eux-mêmes branchés sur les services réels :
// `data/backup`, `data/export`, `health`, `sync/ReminderScheduler` et `maps`.
// Une liaison par port ; les écrans ne voient jamais les services.
package ovh.battistella.elan.ui.screens.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun bindBackupPort(impl: BackupManagerPort): BackupPort

    @Binds
    abstract fun bindExportPort(impl: ExportersPort): ExportPort

    @Binds
    abstract fun bindStravaImportPort(impl: StravaImporterPort): StravaImportPort

    @Binds
    abstract fun bindHealthConnectPort(impl: HealthConnectManagerPort): HealthConnectPort

    @Binds
    abstract fun bindRemindersPort(impl: ReminderSchedulerPort): RemindersPort

    @Binds
    abstract fun bindMapStylePort(impl: MapStyleRepositoryPort): MapStylePort
}
