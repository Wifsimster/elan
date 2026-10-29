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
