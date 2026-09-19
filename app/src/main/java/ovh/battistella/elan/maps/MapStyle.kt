// Configuration du fond de carte en ligne, OPT-IN (port de lib/map.ts).
//
// Par défaut, AUCUN fond de carte n'est chargé : le tracé s'affiche sur fond
// uni (`RouteCanvas`, 100 % hors-ligne, aucune donnée envoyée). L'utilisateur
// peut activer un fond en ligne : OpenFreeMap (gratuit, sans clé) ou sa propre
// URL de style MapLibre. Une fois activé, les requêtes de tuiles (zone du
// parcours + IP) sortent vers le serveur choisi — d'où le défaut hors-ligne.
package ovh.battistella.elan.maps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Style public OpenFreeMap (gratuit, open source, sans clé API, données
 * OpenStreetMap). Auto-hébergeable.
 * @see <a href="https://openfreemap.org">openfreemap.org</a>
 */
const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private val HTTPS = Regex("^https://", RegexOption.IGNORE_CASE)

/**
 * Vrai si l'URL de style est acceptable : vide (carte désactivée) ou HTTPS.
 * Le HTTP en clair est refusé — sinon les requêtes de tuiles (zone du parcours
 * + IP de l'appareil) partiraient en clair ; et un fond http « marcherait » en
 * debug mais échouerait silencieusement en release (cleartext bloqué).
 */
fun isValidMapStyleUrl(url: String): Boolean {
    val u = url.trim()
    return u.isEmpty() || HTTPS.containsMatchIn(u)
}

/**
 * Mention d'attribution à afficher sur la carte. **Obligatoire** dès qu'un fond
 * de carte est affiché : les tuiles dérivent des données OpenStreetMap (et,
 * pour le style public, d'OpenFreeMap / OpenMapTiles).
 */
fun mapAttribution(styleUrl: String): String =
    if (styleUrl.contains("openfreemap")) "© OpenFreeMap · OpenMapTiles · OpenStreetMap" else "© OpenStreetMap"

/**
 * URL de style courante, re-validée à la lecture (défense en profondeur : la
 * clé est exclue des sauvegardes, mais une valeur héritée en `http://` doit
 * quand même retomber sur le rendu hors-ligne) et tenue en mémoire pour que
 * chaque carte s'ouvre directement dans le bon rendu, sans flash Canvas→MapLibre.
 */
@Singleton
class MapStyleRepository @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    /** `""` = fond de carte désactivé (rendu Canvas hors-ligne). */
    val styleUrl: StateFlow<String> = settings.settings
        .map { s -> s.mapStyleUrl.trim().takeIf(::isValidMapStyleUrl) ?: "" }
        .stateIn(scope, SharingStarted.Eagerly, "")

    /** Enregistre l'URL (vide = désactiver). Refuse tout ce qui n'est pas HTTPS. */
    suspend fun setStyleUrl(url: String) {
        val trimmed = url.trim()
        if (!isValidMapStyleUrl(trimmed)) throw IllegalArgumentException("URL de style invalide : HTTPS requis.")
        settings.setMapStyleUrl(trimmed)
    }
}
