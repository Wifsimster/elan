package ovh.battistella.elan.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import ovh.battistella.elan.data.legacy.FreeSpaceProbe
import ovh.battistella.elan.data.local.BodyMeasurementDao
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.local.MuscuSetDao
import ovh.battistella.elan.data.local.SessionDao
import ovh.battistella.elan.data.local.SettingsDao
import ovh.battistella.elan.data.local.TrackPointDao
import ovh.battistella.elan.data.secrets.KeystoreSecretStore
import ovh.battistella.elan.data.secrets.SecretStore
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideFreeSpaceProbe(): FreeSpaceProbe = FreeSpaceProbe.STAT_FS

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ElanDatabase =
        Room.databaseBuilder(context, ElanDatabase::class.java, ElanDatabase.NAME)
            .build()

    @Provides
    fun provideSessionDao(db: ElanDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTrackPointDao(db: ElanDatabase): TrackPointDao = db.trackPointDao()

    @Provides
    fun provideMuscuSetDao(db: ElanDatabase): MuscuSetDao = db.muscuSetDao()

    @Provides
    fun provideBodyMeasurementDao(db: ElanDatabase): BodyMeasurementDao = db.bodyMeasurementDao()

    @Provides
    fun provideSettingsDao(db: ElanDatabase): SettingsDao = db.settingsDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun bindSecretStore(impl: KeystoreSecretStore): SecretStore
}
