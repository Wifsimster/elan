package ovh.battistella.elan.ui.navigation

import android.content.Intent
import android.net.Uri
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.toActivityType
import ovh.battistella.elan.sync.ProgressionNotifier
import ovh.battistella.elan.tracking.LiveKind
import ovh.battistella.elan.tracking.LiveNotification

const val DEEP_LINK_SCHEME = "elan"

fun openRouteFor(intent: Intent, outingType: ActivityType, outingLive: Boolean): String? {
    val extra = intent.getStringExtra(LiveNotification.EXTRA_OPEN_ROUTE)
    if (extra != null) {
        intent.removeExtra(LiveNotification.EXTRA_OPEN_ROUTE)
        return when (extra) {
            LiveKind.OUTING.route -> Routes.outing(outingType)
            LiveKind.MUSCU.route -> Routes.muscu()
            ProgressionNotifier.ROUTE -> Routes.PROGRESSION
            else -> null
        }
    }
    val uri = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW && it.scheme == DEEP_LINK_SCHEME } ?: return null
    return deepLinkRoute(uri, outingType, outingLive)
}

private fun deepLinkRoute(uri: Uri, outingType: ActivityType, outingLive: Boolean): String? = when (uri.host) {
    LiveKind.OUTING.route -> {
        val requested = toActivityType(uri.pathSegments.firstOrNull(), fallback = outingType)
            .takeIf { isGpsActivity(it) } ?: outingType
        Routes.outing(if (outingLive) outingType else requested)
    }
    LiveKind.MUSCU.route -> Routes.muscu()
    else -> null
}
