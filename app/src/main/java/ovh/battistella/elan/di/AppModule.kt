package ovh.battistella.elan.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Module Hilt unique de l'application. Room, ses DAO et les migrations
 * publiques (`val MIGRATION_x_y`) viendront ici au jalon des données.
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
}
