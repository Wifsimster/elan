package ovh.battistella.elan

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ovh.battistella.elan.di.ApplicationScope
import javax.inject.Inject

/**
 * Point d'entrée de l'application. Fournit la configuration WorkManager avec
 * la fabrique Hilt (l'initialiseur automatique est retiré du manifeste), pour
 * que les workers puissent recevoir leurs dépendances par injection, et lance
 * les tâches de démarrage sur la portée applicative.
 */
@HiltAndroidApp
class ElanApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var startupTasks: StartupTasks
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Hors du fil principal : l'import de l'ancienne base peut durer ; la
        // barrière de migration tient l'interface en attente pendant ce temps.
        appScope.launch { startupTasks.run() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
