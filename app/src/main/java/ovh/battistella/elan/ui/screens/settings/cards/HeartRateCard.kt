package ovh.battistella.elan.ui.screens.settings.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.sensors.ble.BlePermissions
import ovh.battistella.elan.sensors.ble.HrState
import ovh.battistella.elan.sensors.ble.SensorStatus
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Demande les permissions Bluetooth puis lance [onGranted] ; [onDenied] sinon.
 * Renvoie l'action à brancher sur le bouton « Rechercher ».
 */
@Composable
internal fun rememberBleScanAction(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) onGranted() else onDenied()
    }
    return {
        if (BlePermissions.granted(context)) onGranted() else launcher.launch(BlePermissions.required())
    }
}

/** Carte Réglages : appairage et état de la ceinture cardiaque (BLE). */
@Composable
fun HeartRateCard(
    state: HrState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    var permissionDenied by remember { mutableStateOf(false) }
    val scan = rememberBleScanAction(
        onGranted = { permissionDenied = false; onStartScan() },
        onDenied = { permissionDenied = true },
    )

    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.HeartPulse, color = colors.heart, title = stringResource(R.string.settings_hr_title))

        HrStatusLine(state)

        when (state.status) {
            SensorStatus.Connected -> ElanButton(
                title = stringResource(R.string.settings_hr_disconnect),
                icon = MdiIcons.BluetoothOff,
                variant = ButtonVariant.Secondary,
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
            )
            // « Stop » pendant le scan plutôt qu'un bouton désactivé : la radio
            // ne reste pas à balayer si l'utilisateur change d'avis.
            SensorStatus.Scanning -> ElanButton(
                title = stringResource(R.string.settings_scan_stop),
                icon = MdiIcons.BluetoothOff,
                variant = ButtonVariant.Secondary,
                onClick = onStopScan,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> ElanButton(
                title = stringResource(if (state.status == SensorStatus.Reconnecting) R.string.settings_hr_reconnecting else R.string.settings_hr_scan),
                icon = MdiIcons.Bluetooth,
                loading = state.status == SensorStatus.Connecting || state.status == SensorStatus.Reconnecting,
                enabled = state.status != SensorStatus.Unsupported,
                onClick = scan,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (permissionDenied) CardError(stringResource(R.string.settings_ble_permission_denied))
        state.error?.let { CardError(it) }

        if (state.status == SensorStatus.Scanning && state.scanned.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                CardText(stringResource(R.string.settings_hr_scan_hint))
            }
        }

        state.scanned.forEach { d ->
            ScannedDeviceRow(icon = MdiIcons.HeartFlash, color = colors.heart, name = d.name, onClick = { onConnect(d.id) })
        }

        if (state.status == SensorStatus.Unsupported) CardText(stringResource(R.string.settings_ble_unsupported))
    }
}

/** Pastille colorée + libellé d'état + FC live si connectée. */
@Composable
private fun HrStatusLine(state: HrState) {
    val colors = ElanTheme.colors
    val (label, color) = when (state.status) {
        SensorStatus.Connected -> (
            state.device?.let { stringResource(R.string.settings_hr_connected_to, it.name) }
                ?: stringResource(R.string.settings_hr_connected)
            ) to colors.success
        SensorStatus.Connecting -> stringResource(R.string.settings_hr_connecting) to colors.warning
        SensorStatus.Reconnecting -> stringResource(R.string.settings_hr_reconnecting) to colors.warning
        SensorStatus.Scanning -> stringResource(R.string.settings_hr_scanning) to colors.warning
        SensorStatus.Error -> stringResource(R.string.settings_hr_error) to colors.danger
        SensorStatus.Idle -> stringResource(R.string.settings_hr_idle) to colors.textSecondary
        SensorStatus.Unsupported -> stringResource(R.string.settings_hr_unsupported) to colors.textSecondary
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            Text(label, color = colors.text, style = TextStyle(fontWeight = FontWeight.SemiBold))
        }
        if (state.status == SensorStatus.Connected) {
            Text(
                text = state.bpm?.let { stringResource(R.string.settings_bpm, it) } ?: "··",
                color = colors.heart,
                style = TextStyle(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum", fontSize = 15.sp),
            )
        }
    }
}
