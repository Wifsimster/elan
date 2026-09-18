package ovh.battistella.elan.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import ovh.battistella.elan.R

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

/**
 * Les destinations de la barre de navigation. Icônes Material provisoires
 * (`DateRange` faute de `History` dans le jeu d'icônes de base) ; les icônes
 * PULSE arrivent avec le jalon des assets.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Default.DateRange),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
}
