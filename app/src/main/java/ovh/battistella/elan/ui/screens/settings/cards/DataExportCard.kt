package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.PRIVACY_ZONE_OPTIONS
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.PulseChip
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.settings.ExportKind
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Carte Réglages : export du bilan (Markdown) / des données brutes (JSON) via
 * la feuille de partage système, et zone de confidentialité des tracés GPX.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataExportCard(
    privacyZoneM: Int,
    exporting: ExportKind?,
    error: String?,
    onPrivacyZone: (Int) -> Unit,
    onExportMarkdown: () -> Unit,
    onExportJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    PulseCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.ExportVariant, color = colors.accent, title = stringResource(R.string.settings_export_card_title))
        CardText(stringResource(R.string.settings_export_intro))

        // Zone de confidentialité : rogne le départ/arrivée des exports GPS.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SubTitle(stringResource(R.string.settings_privacy_title))
            CardText(stringResource(R.string.settings_privacy_text))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIVACY_ZONE_OPTIONS.forEach { m ->
                    PulseChip(
                        label = if (m == 0) stringResource(R.string.settings_privacy_off) else stringResource(R.string.settings_privacy_m, m),
                        selected = privacyZoneM == m,
                        color = colors.velo,
                        onClick = { onPrivacyZone(m) },
                    )
                }
            }
        }

        PulseButton(
            title = stringResource(R.string.settings_export_markdown),
            icon = MdiIcons.FileDocumentOutline,
            loading = exporting == ExportKind.Markdown,
            enabled = exporting == null,
            onClick = onExportMarkdown,
            modifier = Modifier.fillMaxWidth(),
        )
        PulseButton(
            title = stringResource(R.string.settings_export_json),
            icon = MdiIcons.CodeJson,
            variant = ButtonVariant.Secondary,
            loading = exporting == ExportKind.Json,
            enabled = exporting == null,
            onClick = onExportJson,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { CardError(it) }
    }
}
