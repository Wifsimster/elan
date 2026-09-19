package ovh.battistella.elan.ui.screens.settings.cards

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import ovh.battistella.elan.R
import ovh.battistella.elan.data.settings.NotificationConfig
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.components.SettingStepper
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Carte Réglages : rappels locaux de séance (opt-in, jour du planning + heure).
 * Sur Android 13+, l'activation demande d'abord `POST_NOTIFICATIONS` ; un
 * refus laisse le commutateur éteint avec une erreur en ligne.
 */
@Composable
fun NotificationsCard(
    config: NotificationConfig,
    error: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDenied: () -> Unit,
    onHourChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEnabledChange(true) else onDenied()
    }

    PulseCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.BellOutline, color = colors.accent, title = stringResource(R.string.settings_notif_title))
        CardText(stringResource(R.string.settings_notif_text))
        SwitchRow(
            label = stringResource(R.string.settings_notif_toggle),
            checked = config.enabled,
            onCheckedChange = { on ->
                if (!on) {
                    onEnabledChange(false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onEnabledChange(true)
                }
            },
        )
        SettingStepper(
            label = stringResource(R.string.settings_notif_hour),
            value = config.hour,
            unit = "h",
            min = 0,
            max = 23,
            onChange = onHourChange,
        )
        if (error) CardError(stringResource(R.string.settings_notif_denied))
    }
}
