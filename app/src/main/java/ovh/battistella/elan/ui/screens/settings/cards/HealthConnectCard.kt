package ovh.battistella.elan.ui.screens.settings.cards

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Carte Réglages : export opt-in des séances vers Android Health Connect. Les
 * permissions système ne sont demandées qu'à l'activation du commutateur
 * (jamais au lancement). Masquée par l'écran quand Health Connect n'est pas
 * supporté.
 */
@Composable
fun HealthConnectCard(
    enabled: Boolean,
    busy: Boolean,
    permissions: Set<String>,
    contract: ActivityResultContract<Set<String>, Set<String>>,
    onDisable: () -> Unit,
    onRequestStarted: () -> Unit,
    onPermissionResult: (Set<String>) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val launcher = rememberLauncherForActivityResult(contract) { granted -> onPermissionResult(granted) }

    PulseCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.HeartPlusOutline, color = colors.accent, title = stringResource(R.string.settings_health_title))
        SwitchRow(
            label = stringResource(R.string.settings_health_toggle),
            checked = enabled,
            enabled = !busy,
            onCheckedChange = { on ->
                if (!on) {
                    onDisable()
                } else {
                    onRequestStarted()
                    try {
                        launcher.launch(permissions)
                    } catch (e: ActivityNotFoundException) {
                        // Health Connect absent : aucune activité ne répond au contrat.
                        onUnavailable()
                    }
                }
            },
        )
        CardText(stringResource(if (enabled) R.string.settings_health_on_text else R.string.settings_health_off_text))
    }
}
