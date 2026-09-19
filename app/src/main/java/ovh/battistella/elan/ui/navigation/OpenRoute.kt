package ovh.battistella.elan.ui.navigation

import android.content.Intent
import android.net.Uri
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.toActivityType
import ovh.battistella.elan.sync.ProgressionNotifier
import ovh.battistella.elan.tracking.LiveKind
import ovh.battistella.elan.tracking.LiveNotification

/** Schéma des liens profonds déclaré dans le manifeste (`elan://…`). */
const val DEEP_LINK_SCHEME = "elan"

/**
 * Route à ouvrir en réponse à un intent reçu par `MainActivity`, ou `null` si
 * l'intent n'en demande aucune (lancement ordinaire). Deux sources :
 *  - l'extra [LiveNotification.EXTRA_OPEN_ROUTE] posé par les notifications
 *    de séance (`outing` / `muscu`) ;
 *  - un lien profond `elan://outing/{type}` ou `elan://muscu` (action VIEW).
 *
 * La sortie ramenée est toujours celle du contrôleur : [outingType] est son
 * type courant et, si [outingLive] (sortie en cours), un lien vers un autre
 * type n'en démarre pas une deuxième. L'extra est retiré de l'intent : une
 * relecture (`getIntent()` après recréation) ne rejoue pas la navigation.
 */
fun openRouteFor(intent: Intent, outingType: ActivityType, outingLive: Boolean): String? {
    val extra = intent.getStringExtra(LiveNotification.EXTRA_OPEN_ROUTE)
    if (extra != null) {
        intent.removeExtra(LiveNotification.EXTRA_OPEN_ROUTE)
        return when (extra) {
            LiveKind.OUTING.route -> Routes.outing(outingType)
            LiveKind.MUSCU.route -> Routes.muscu()
            // Annonce hebdomadaire de la progression auto (`ProgressionNotifier`).
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
        Routes.outing(if (outingLive) outingType else requested)
    }
    LiveKind.MUSCU.route -> Routes.muscu()
    // Autres liens (import, partage, OAuth) : pas encore pris en charge.
    else -> null
}
