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
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import ovh.battistella.elan.data.local.BodyMeasurementDao
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.local.MuscuSetDao
import ovh.battistella.elan.data.local.SessionDao
import ovh.battistella.elan.data.local.SettingsDao
import ovh.battistella.elan.data.local.TrackPointDao
import ovh.battistella.elan.data.secrets.KeystoreSecretStore
import ovh.battistella.elan.data.secrets.SecretStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Module Hilt unique de l'application : dispatcher d'E/S, OkHttp, Room et ses
 * DAO. Les migrations futures viendront ici en `val MIGRATION_x_y` publiques
 * (testées par `MigrationTest` contre les schémas exportés).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Le dispatcher sur lequel les dépôts déportent les E/S bloquantes
     * (réseau, disque, base). Injecté plutôt que codé en dur pour que les
     * tests puissent le remplacer par un ordonnanceur déterministe. Comme
     * Ondes, il est fourni sans qualificatif : `CoroutineDispatcher` n'a
     * qu'une seule liaison dans le graphe.
     */
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
            // Volontairement AUCUN repli destructif : une migration oubliée doit
            // planter bruyamment (et être attrapée par MigrationTest en CI), pas
            // effacer en silence les séances de l'utilisateur. Les migrations
            // s'ajoutent ici : .addMigrations(MIGRATION_8_9, …)
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

/** Liaisons interface → implémentation (un module abstrait est requis pour `@Binds`). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun bindSecretStore(impl: KeystoreSecretStore): SecretStore
}
