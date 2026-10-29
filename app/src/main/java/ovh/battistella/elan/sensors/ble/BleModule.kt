package ovh.battistella.elan.sensors.ble

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import ovh.battistella.elan.tracking.CadenceSamples
import ovh.battistella.elan.tracking.HeartRateSamples
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BleScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    @Binds
    abstract fun bindLeScanner(impl: AndroidLeScanner): LeScanner

    @Binds
    abstract fun bindGattLinkFactory(impl: AndroidGattLinkFactory): GattLinkFactory

    @Binds
    abstract fun bindAdapterState(impl: AndroidBleAdapterState): BleAdapterState

    @Binds
    abstract fun bindHeartRateSamples(impl: HeartRateManager): HeartRateSamples

    @Binds
    abstract fun bindCadenceSamples(impl: CadenceSpeedManager): CadenceSamples

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        @Provides
        @Singleton
        @BleScope
        fun provideBleScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

        @Provides
        @Singleton
        fun provideBlePermissionCheck(@ApplicationContext context: Context): BlePermissionCheck =
            BlePermissionCheck { BlePermissions.granted(context) }
    }
}
