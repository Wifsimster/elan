package ovh.battistella.elan.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.TemplateId

/**
 * Routes de l'app (miroir du dossier `src/app` d'origine) : trois onglets, deux écrans live
 * plein écran (`outing`, `muscu`) et les écrans empilés. Les constructeurs
 * encodent les paramètres.
 */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    /** `outing/{type}` — sortie GPS (velo / course / marche). */
    const val OUTING = "outing/{type}"
    fun outing(type: ActivityType): String = "outing/${type.key}"

    /** `muscu?template=` — séance de musculation (plein écran). */
    const val MUSCU = "muscu?template={template}"
    fun muscu(template: TemplateId? = null): String =
        if (template == null) "muscu" else "muscu?template=${template.key}"

    const val SESSION = "session/{id}"
    fun session(id: Long): String = "session/$id"

    const val SESSION_MAP = "session/{id}/map"
    fun sessionMap(id: Long): String = "session/$id/map"

    const val WEIGHT = "weight"
    const val PROGRESSION = "progression"
    const val CATALOG = "catalog"

    const val EXERCISE = "exercise/{name}"
    fun exercise(name: String): String = "exercise/${Uri.encode(name)}"
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
