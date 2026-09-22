package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.WHEEL_SIZES
import ovh.battistella.elan.domain.matchWheelSize
import ovh.battistella.elan.sensors.ble.CscState
import ovh.battistella.elan.sensors.ble.SensorStatus
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.components.SettingStepper
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.settings.WHEEL_MM_MAX
import ovh.battistella.elan.ui.screens.settings.WHEEL_MM_MIN
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import java.util.Locale

/** Carte Réglages : capteurs vélo BLE (cadence / vitesse) + taille de pneu. */
@Composable
fun CadenceSensorCard(
    state: CscState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onWheelMm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    var permissionDenied by remember { mutableStateOf(false) }
    val scan = rememberBleScanAction(
        onGranted = { permissionDenied = false; onStartScan() },
        onDenied = { permissionDenied = true },
    )

    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.RotateRight, color = colors.velo, title = stringResource(R.string.settings_csc_title))
        CardText(stringResource(R.string.settings_csc_intro))

        state.devices.forEach { d ->
            val disconnectLabel = stringResource(R.string.settings_csc_disconnect, d.name)
            HairlineRow {
                StatusDot(colors.success, size = 9)
                Text(d.name, color = colors.text, style = ElanType.subtitle, modifier = Modifier.weight(1f))
                val live = when {
                    state.cadenceRpm != null -> stringResource(R.string.settings_csc_rpm, state.cadenceRpm)
                    state.speedKmh != null -> stringResource(R.string.settings_csc_kmh, String.format(Locale.FRANCE, "%.1f", state.speedKmh))
                    else -> null
                }
                if (live != null) {
                    Text(live, color = colors.velo, style = ElanType.metricSm)
                }
                Icon(
                    painter = painterResource(MdiIcons.BluetoothOff),
                    contentDescription = disconnectLabel,
                    tint = colors.textMuted,
                    modifier = Modifier
                        .pressableScale(haptic = null, onClick = { onDisconnect(d.id) })
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
        }

        if (state.status == SensorStatus.Scanning) {
            ElanButton(
                title = stringResource(R.string.settings_scan_stop),
                icon = MdiIcons.BluetoothOff,
                variant = ButtonVariant.Secondary,
                color = colors.velo,
                onClick = onStopScan,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ElanButton(
                title = stringResource(if (state.status == SensorStatus.Reconnecting) R.string.settings_hr_reconnecting else R.string.settings_csc_scan),
                icon = MdiIcons.Bluetooth,
                color = colors.velo,
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
                CardText(stringResource(R.string.settings_csc_scan_hint))
            }
        }

        state.scanned.forEach { d ->
            ScannedDeviceRow(icon = MdiIcons.BikeFast, color = colors.velo, name = d.name, onClick = { onConnect(d.id) })
        }

        if (state.status != SensorStatus.Unsupported) {
            Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                SubTitle(stringResource(R.string.settings_wheel_title))
                WheelSizePicker(valueMm = state.wheelCircumferenceMm, onSelect = onWheelMm)
                SettingStepper(
                    label = stringResource(R.string.settings_wheel_circumference),
                    value = state.wheelCircumferenceMm,
                    unit = "mm",
                    min = WHEEL_MM_MIN,
                    max = WHEEL_MM_MAX,
                    onChange = onWheelMm,
                )
                CardText(stringResource(R.string.settings_wheel_hint), size = 12)
            }
        } else {
            CardText(stringResource(R.string.settings_ble_unsupported))
        }
    }
}

/** Sélecteur de taille de pneu : liste des presets dans un dialogue, renseigne la circonférence (mm). */
@Composable
private fun WheelSizePicker(valueMm: Int, onSelect: (Int) -> Unit) {
    val colors = ElanTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = matchWheelSize(valueMm)
    val title = stringResource(R.string.settings_wheel_title)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = title }
            .pressableScale(onClick = { open = true })
            .background(colors.backgroundElement, RoundedCornerShape(Radius.md))
            .border(1.5.dp, colors.border, RoundedCornerShape(Radius.md))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = current?.label ?: stringResource(R.string.settings_wheel_custom),
            color = colors.text,
            style = ElanType.label.copy(fontWeight = FontWeight.Bold),
        )
        Icon(painter = painterResource(MdiIcons.ChevronDown), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(colors.backgroundElement)
                    .border(1.dp, colors.border, RoundedCornerShape(Radius.lg)),
            ) {
                Text(
                    text = title.uppercase(),
                    style = ElanType.overline,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                )
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    WHEEL_SIZES.forEach { w ->
                        val active = w.mm == valueMm
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressableScale(onClick = { onSelect(w.mm); open = false })
                                .background(if (active) colors.velo.copy(alpha = 0.13f) else colors.backgroundElement)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = w.label,
                                color = if (active) colors.velo else colors.text,
                                style = if (active) ElanType.subtitle.copy(fontWeight = FontWeight.ExtraBold) else ElanType.subtitle,
                            )
                            Text(stringResource(R.string.settings_wheel_mm, w.mm), color = colors.textSecondary, style = ElanType.bodySm)
                        }
                    }
                }
            }
        }
    }
}
