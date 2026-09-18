package ovh.battistella.elan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.legacy.MigrationGate
import ovh.battistella.elan.data.legacy.MigrationState
import ovh.battistella.elan.tracking.TrackingController
import ovh.battistella.elan.ui.enableEdgeToEdgeCompat
import ovh.battistella.elan.ui.navigation.ElanRoot
import ovh.battistella.elan.ui.navigation.openRouteFor
import ovh.battistella.elan.ui.screens.migration.MigrationScreen
import ovh.battistella.elan.ui.setSystemBarsAppearance
import ovh.battistella.elan.ui.theme.ElanTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var snackbarController: SnackbarController
    @Inject lateinit var migrationGate: MigrationGate
    @Inject lateinit var trackingController: TrackingController

    /**
     * Route demandée par l'intent (notification de séance, lien profond) et pas
     * encore ouverte. Consommée par [ElanRoot] une fois la migration passée ;
     * sauvegardée avec l'instance pour survivre à une recréation entre-temps.
     */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        pendingRoute.value = if (savedInstanceState == null) {
            routeFrom(intent)
        } else {
            // L'intent de lancement a déjà été consommé par l'instance précédente.
            savedInstanceState.getString(STATE_PENDING_ROUTE)
        }
        setContent {
            // Le thème suit le mode sombre du système ; un réglage explicite
            // (clair / sombre / système) viendra avec SettingsRepository.
            val darkTheme = isSystemInDarkTheme()
            val migration by migrationGate.state.collectAsStateWithLifecycle()
            val route by pendingRoute.collectAsStateWithLifecycle()
            // L'écran de migration est sombre quel que soit le thème.
            val darkBars = darkTheme || migration != MigrationState.Ready
            LaunchedEffect(darkBars) { window.setSystemBarsAppearance(darkBars) }
            ElanTheme(darkTheme = darkTheme) {
                if (migration == MigrationState.Ready) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ElanRoot(
                            snackbar = snackbarController,
                            pendingRoute = route,
                            onPendingRouteConsumed = { pendingRoute.value = null },
                        )
                    }
                } else {
                    // Tant que l'ancienne base n'est pas reprise, aucun écran
                    // ne doit lire une base à moitié remplie.
                    MigrationScreen(
                        state = migration,
                        onRetry = { migrationGate.retry() },
                        onSkip = { migrationGate.skip() },
                    )
                }
            }
        }
    }

    /** Activité `singleTop` : un appui sur la notification arrive ici, sans recréation. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let { pendingRoute.value = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_ROUTE, pendingRoute.value)
    }

    private fun routeFrom(intent: Intent?): String? {
        val outing = trackingController.state.value
        return intent?.let { openRouteFor(it, outing.type, outing.isLive) }
    }

    private companion object {
        const val STATE_PENDING_ROUTE = "pendingRoute"
    }
}
