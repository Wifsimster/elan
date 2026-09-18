package ovh.battistella.elan

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Point d'entrée de l'application. Fournit la configuration WorkManager avec
 * la fabrique Hilt (l'initialiseur automatique est retiré du manifeste), pour
 * que les workers puissent recevoir leurs dépendances par injection.
 */
@HiltAndroidApp
class ElanApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
