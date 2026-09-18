package ovh.battistella.elan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.ui.enableEdgeToEdgeCompat
import ovh.battistella.elan.ui.navigation.ElanRoot
import ovh.battistella.elan.ui.setSystemBarsAppearance
import ovh.battistella.elan.ui.theme.ElanTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var snackbarController: SnackbarController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        setContent {
            // Le thème suit le mode sombre du système ; un réglage explicite
            // (clair / sombre / système) viendra avec SettingsRepository.
            val darkTheme = isSystemInDarkTheme()
            LaunchedEffect(darkTheme) { window.setSystemBarsAppearance(darkTheme) }
            ElanTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ElanRoot(snackbar = snackbarController)
                }
            }
        }
    }
}
