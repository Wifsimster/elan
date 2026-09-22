package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.components.SettingField
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Carte Réglages : fond de carte MapLibre en ligne (opt-in, off par défaut).
 * [customUrl] est le contenu du champ « serveur personnel » (vide quand le
 * preset OpenFreeMap est utilisé).
 */
@Composable
fun MapCard(
    enabled: Boolean,
    custom: Boolean,
    customUrl: String,
    onEnabledChange: (Boolean) -> Unit,
    onCustomUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.MapOutline, color = colors.velo, title = stringResource(R.string.settings_map_title))
        SwitchRow(label = stringResource(R.string.settings_map_toggle), checked = enabled, onCheckedChange = onEnabledChange, color = colors.velo)
        CardText(stringResource(if (enabled) R.string.settings_map_on_text else R.string.settings_map_off_text))
        if (enabled) {
            CardText(stringResource(if (custom) R.string.settings_map_source_custom else R.string.settings_map_source_openfreemap), size = 12)
            SettingField(
                label = stringResource(R.string.settings_map_custom_label),
                placeholder = stringResource(R.string.settings_map_custom_placeholder),
                value = customUrl,
                onValueChange = onCustomUrlChange,
                keyboardType = KeyboardType.Uri,
            )
            CardText(stringResource(R.string.settings_map_custom_hint), size = 12)
        }
    }
}
