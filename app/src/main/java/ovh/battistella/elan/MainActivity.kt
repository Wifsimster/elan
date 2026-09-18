package ovh.battistella.elan

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
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.legacy.MigrationGate
import ovh.battistella.elan.data.legacy.MigrationState
import ovh.battistella.elan.ui.enableEdgeToEdgeCompat
import ovh.battistella.elan.ui.navigation.ElanRoot
import ovh.battistella.elan.ui.screens.migration.MigrationScreen
import ovh.battistella.elan.ui.setSystemBarsAppearance
import ovh.battistella.elan.ui.theme.ElanTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var snackbarController: SnackbarController
    @Inject lateinit var migrationGate: MigrationGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        setContent {
            // Le thème suit le mode sombre du système ; un réglage explicite
            // (clair / sombre / système) viendra avec SettingsRepository.
            val darkTheme = isSystemInDarkTheme()
            val migration by migrationGate.state.collectAsStateWithLifecycle()
            // L'écran de migration est sombre quel que soit le thème.
            val darkBars = darkTheme || migration != MigrationState.Ready
            LaunchedEffect(darkBars) { window.setSystemBarsAppearance(darkBars) }
            ElanTheme(darkTheme = darkTheme) {
                if (migration == MigrationState.Ready) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ElanRoot(snackbar = snackbarController)
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
}
