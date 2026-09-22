package ovh.battistella.elan.ui.screens.settings.cards

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
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

/** Carte Réglages : gestion des données locales (effacement, réinitialisation), confirmées par l'écran. */
@Composable
fun DataCard(onClearSessions: () -> Unit, onResetAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.DatabaseOutline, color = colors.accent, title = stringResource(R.string.settings_data_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(painter = painterResource(MdiIcons.LockOutline), contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
            CardText(stringResource(R.string.settings_data_privacy), modifier = Modifier.weight(1f))
        }
        ElanButton(
            title = stringResource(R.string.settings_data_clear),
            icon = MdiIcons.TrashCanOutline,
            variant = ButtonVariant.Danger,
            onClick = onClearSessions,
            modifier = Modifier.fillMaxWidth(),
        )
        ElanButton(
            title = stringResource(R.string.settings_data_reset),
            icon = MdiIcons.DeleteForeverOutline,
            variant = ButtonVariant.Secondary,
            color = colors.danger,
            onClick = onResetAll,
            modifier = Modifier.fillMaxWidth(),
        )
        CardText(stringResource(R.string.settings_data_reset_hint), size = 12)
    }
}
