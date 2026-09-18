package ovh.battistella.elan.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ovh.battistella.elan.tracking.AndroidGpsSource
import ovh.battistella.elan.tracking.BackupTrigger
import ovh.battistella.elan.tracking.CadenceSamples
import ovh.battistella.elan.tracking.GpsSource
import ovh.battistella.elan.tracking.HealthExport
import ovh.battistella.elan.tracking.HeartRateSamples
import javax.inject.Singleton

/**
 * Liaisons du suivi GPS (`tracking/`).
 *
 * Les dépendances qui arrivent avec d'autres jalons sont déclarées en liaisons
 * OPTIONNELLES (`@BindsOptionalOf`) : tant qu'aucun module ne les lie, le
 * contrôleur reçoit `Optional.empty()` et se comporte comme sans capteur /
 * sans sauvegarde / sans Health Connect. Pour brancher l'implémentation
 * réelle, il suffit d'ajouter un `@Binds` dans SON PROPRE module — rien à
 * modifier ici :
 *
 *   - `HeartRateSamples`, `CadenceSamples` → `BleModule` (jalon capteurs) ;
 *   - `BackupTrigger`, `HealthExport`      → module du jalon M3.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @Singleton
    abstract fun bindGpsSource(impl: AndroidGpsSource): GpsSource

    @BindsOptionalOf
    abstract fun optionalHeartRateSamples(): HeartRateSamples

    @BindsOptionalOf
    abstract fun optionalCadenceSamples(): CadenceSamples

    @BindsOptionalOf
    abstract fun optionalBackupTrigger(): BackupTrigger

    @BindsOptionalOf
    abstract fun optionalHealthExport(): HealthExport
}
