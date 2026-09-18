// Service de premier plan d'une sortie GPS (type `location`).
//
// Il ne fait rien lui-même : c'est `TrackingController` qui possède le flux
// GPS et l'état. Le service existe pour qu'Android maintienne le processus et
// le GPS actifs écran éteint (notification persistante, verrou partiel de
// veille), comme le faisait le service expo-location de l'app d'origine. Il se
// coupe tout seul dès que le contrôleur quitte les phases ACTIVE / PAUSED.
//
// Démarré par le contrôleur app visible (Android interdit le démarrage d'un
// service de premier plan depuis l'arrière-plan). `START_NOT_STICKY` : après un
// kill mémoire, le sauvetage se fait au prochain lancement (SessionRecovery),
// pas par un service ressuscité sans consommateur.
package ovh.battistella.elan.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.ServiceCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TrackingService : Service() {

    /**
     * Le contrôleur est résolu par un point d'entrée Hilt plutôt que par
     * `@AndroidEntryPoint` : les tests Robolectric tournent sans application
     * Hilt et fournissent le contrôleur par [controllerProvider].
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun controller(): TrackingController
    }

    private lateinit var controller: TrackingController
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        controller = controllerProvider?.invoke()
            ?: EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).controller()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // En premier, sans condition : Android exige `startForeground` dans les
        // secondes qui suivent `startForegroundService`.
        ServiceCompat.startForeground(
            this,
            LiveNotification.FOREGROUND_ID,
            LiveNotification.buildOutingNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (watchJob == null) {
            watchJob = scope.launch {
                controller.state.map { it.isLive }.distinctUntilChanged().collect { live ->
                    if (!live) stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        watchJob = null
        scope.cancel()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Verrou sans délai : la sortie dure ce qu'elle dure ; il est relâché avec le service.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also { it.acquire() }
        } catch (_: Exception) {
            // Sans verrou, le GPS continue tant que le service de premier plan vit.
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
            // Déjà relâché.
        } finally {
            wakeLock = null
        }
    }

    companion object {
        const val WAKE_LOCK_TAG = "elan:outing"

        /** Remplace la résolution Hilt du contrôleur (tests Robolectric sans application Hilt). */
        @VisibleForTesting
        internal var controllerProvider: (() -> TrackingController)? = null

        fun intent(context: Context): Intent = Intent(context, TrackingService::class.java)
    }
}
