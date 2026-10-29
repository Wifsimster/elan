package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

@Composable
fun ProgressionAutoCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.TrendingUp, color = colors.muscu, title = stringResource(R.string.settings_progression_title))
        CardText(stringResource(R.string.settings_progression_text))
        SwitchRow(
            label = stringResource(R.string.settings_progression_toggle),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            color = colors.muscu,
        )
    }
}
