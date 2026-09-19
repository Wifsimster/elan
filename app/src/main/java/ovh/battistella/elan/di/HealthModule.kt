package ovh.battistella.elan.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ovh.battistella.elan.health.AndroidHealthConnectGateway
import ovh.battistella.elan.health.HealthConnectGateway
import ovh.battistella.elan.health.HealthConnectManager
import ovh.battistella.elan.tracking.HealthExport
import javax.inject.Singleton

/**
 * Liaisons Health Connect : la passerelle SDK et le miroir `HealthExport`
 * attendu (en optionnel) par `SessionFinalizer` — voir `TrackingModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    @Singleton
    abstract fun bindGateway(impl: AndroidHealthConnectGateway): HealthConnectGateway

    @Binds
    abstract fun bindHealthExport(impl: HealthConnectManager): HealthExport
}
