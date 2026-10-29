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

const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private val HTTPS = Regex("^https://", RegexOption.IGNORE_CASE)

fun isValidMapStyleUrl(url: String): Boolean {
    val u = url.trim()
    return u.isEmpty() || HTTPS.containsMatchIn(u)
}

fun mapAttribution(styleUrl: String): String =
    if (styleUrl.contains("openfreemap")) "© OpenFreeMap · OpenMapTiles · OpenStreetMap" else "© OpenStreetMap"

@Singleton
class MapStyleRepository @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val styleUrl: StateFlow<String> = settings.settings
        .map { s -> s.mapStyleUrl.trim().takeIf(::isValidMapStyleUrl) ?: "" }
        .stateIn(scope, SharingStarted.Eagerly, "")

    suspend fun setStyleUrl(url: String) {
        val trimmed = url.trim()
        if (!isValidMapStyleUrl(trimmed)) throw IllegalArgumentException("URL de style invalide : HTTPS requis.")
        settings.setMapStyleUrl(trimmed)
    }
}
