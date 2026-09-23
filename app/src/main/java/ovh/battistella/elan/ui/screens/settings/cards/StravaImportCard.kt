package ovh.battistella.elan.ui.screens.settings.cards

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.settings.STRAVA_IMPORT_MIME_TYPES
import ovh.battistella.elan.ui.screens.settings.StravaLastImport
import ovh.battistella.elan.ui.theme.ElanTheme

/** Nombre maximal de motifs détaillés affichés sous le bilan du dernier import. */
private const val STRAVA_DETAILS_MAX = 5

/** Carte Réglages : import de séances depuis des fichiers Strava (GPX/TCX/FIT), choisis via le sélecteur système. */
@Composable
fun StravaImportCard(
    importing: Boolean,
    error: String?,
    last: StravaLastImport?,
    onImport: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }

    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.CloudDownloadOutline, color = colors.velo, title = stringResource(R.string.settings_strava_title))
        CardText(stringResource(R.string.settings_strava_intro))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(painter = painterResource(MdiIcons.InformationOutline), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            CardText(stringResource(R.string.settings_strava_note), muted = true, size = 12, modifier = Modifier.weight(1f))
        }
        ElanButton(
            title = stringResource(R.string.settings_strava_import),
            icon = MdiIcons.FileImportOutline,
            color = colors.velo,
            loading = importing,
            onClick = { picker.launch(STRAVA_IMPORT_MIME_TYPES) },
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { CardError(it) }
        last?.let { r ->
            val errors = if (r.errors > 0) stringResource(R.string.settings_strava_last_errors, r.errors) else ""
            CardText(stringResource(R.string.settings_strava_last, r.imported, r.duplicates, r.skipped) + errors)
            // Motifs des activités ignorées / fichiers en erreur de l'import qui
            // vient d'avoir lieu, plafonnés : un export en masse peut en produire des dizaines.
            if (r.details.isNotEmpty()) {
                val shown = r.details.take(STRAVA_DETAILS_MAX)
                val more = r.details.size - shown.size
                val lines = shown.joinToString("\n") { "• $it" } +
                    if (more > 0) "\n" + stringResource(R.string.settings_strava_details_more, more) else ""
                CardText(lines, muted = true, size = 12)
            }
        }
    }
}
