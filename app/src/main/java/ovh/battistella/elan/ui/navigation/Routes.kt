package ovh.battistella.elan.ui.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.ui.icons.MdiIcons

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val OUTING = "outing/{type}"
    fun outing(type: ActivityType): String = "outing/${type.key}"

    const val MUSCU = "muscu?template={template}&add={add}"
    fun muscu(template: TemplateId? = null): String =
        if (template == null) "muscu" else "muscu?template=${template.key}"

    fun muscuAdd(catalogId: String): String = "muscu?add=${Uri.encode(catalogId)}"

    const val SESSION = "session/{id}"
    fun session(id: Long): String = "session/$id"

    const val SESSION_MAP = "session/{id}/map"
    fun sessionMap(id: Long): String = "session/$id/map"

    const val WEIGHT = "weight"
    const val PROGRESSION = "progression"
    const val CATALOG = "catalog"

    const val HEALTH_RATIONALE = "health/rationale"

    const val EXERCISE = "exercise/{name}"
    fun exercise(name: String): String = "exercise/${Uri.encode(name)}"
}

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
) {
    HOME(Routes.HOME, R.string.nav_home, MdiIcons.ViewDashboardOutline),
    HISTORY(Routes.HISTORY, R.string.nav_history, MdiIcons.History),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, MdiIcons.CogOutline),
}
