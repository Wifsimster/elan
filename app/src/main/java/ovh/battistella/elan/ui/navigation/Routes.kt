package ovh.battistella.elan.ui.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.ui.icons.MdiIcons

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

    /** `muscu?template=&add=` — séance de musculation (plein écran). */
    const val MUSCU = "muscu?template={template}&add={add}"
    fun muscu(template: TemplateId? = null): String =
        if (template == null) "muscu" else "muscu?template=${template.key}"

    /** Séance muscu démarrée avec un exercice du catalogue (`catalogId`) ajouté. */
    fun muscuAdd(catalogId: String): String = "muscu?add=${Uri.encode(catalogId)}"

    const val SESSION = "session/{id}"
    fun session(id: Long): String = "session/$id"

    const val SESSION_MAP = "session/{id}/map"
    fun sessionMap(id: Long): String = "session/$id/map"

    const val WEIGHT = "weight"
    const val PROGRESSION = "progression"
    const val CATALOG = "catalog"

    /** Justification des permissions Health Connect (ouverte par le système). */
    const val HEALTH_RATIONALE = "health/rationale"

    const val EXERCISE = "exercise/{name}"
    fun exercise(name: String): String = "exercise/${Uri.encode(name)}"
}

/**
 * Les destinations de la barre de navigation, avec les icônes MDI de l'app
 * d'origine (`view-dashboard-outline`, `history`, `cog-outline`).
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
) {
    HOME(Routes.HOME, R.string.nav_home, MdiIcons.ViewDashboardOutline),
    HISTORY(Routes.HISTORY, R.string.nav_history, MdiIcons.History),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, MdiIcons.CogOutline),
}
