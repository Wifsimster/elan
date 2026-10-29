package ovh.battistella.elan.ui.screens.outing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.cadenceParts
import ovh.battistella.elan.domain.distanceParts
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.domain.hrParts
import ovh.battistella.elan.domain.paceParts
import ovh.battistella.elan.domain.speedParts
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.GpsStatusPill
import ovh.battistella.elan.ui.components.MapPlaceholder
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.RouteMap
import ovh.battistella.elan.ui.components.StatTile
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.ConfirmDialog
import ovh.battistella.elan.ui.screens.common.HeaderAction
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.forKey
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun OutingScreen(
    contentPadding: PaddingValues,
    viewModel: OutingViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
    onSaved: (Long) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val color = colors.forKey(ui.meta.colorKey)
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OutingEvent.Saved -> onSaved(event.sessionId)
                OutingEvent.Exit -> onExit()
            }
        }
    }

    BackHandler { viewModel.requestDiscard() }

    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onLocationPermission(
            fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }
    val begin = {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            viewModel.onLocationPermission(fine = true, coarse = true)
        } else {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 120.dp)
                .screenContent(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HeaderAction(
                    icon = MdiIcons.Close,
                    label = stringResource(R.string.outing_quit),
                    onClick = { viewModel.requestDiscard() },
                    size = 26.dp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(MdiIcons.byName(ui.meta.icon) ?: MdiIcons.Bike),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(ui.meta.label, style = ElanType.headline, color = colors.text)
                }
                Spacer(Modifier.width(38.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.outing_duration).uppercase(), style = ElanType.overline, color = color)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.2f))) {
                    Text(formatDuration(ui.outing.elapsedSec), style = ElanType.metricLg, color = colors.text)
                }
                if (ui.phase != OutingPhase.Idle) {
                    GpsStatusPill(
                        status = ui.outing.gpsStatus,
                        accuracyM = ui.outing.accuracyM,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            LiveStatsCard(ui = ui, color = color)

            if (ui.phase != OutingPhase.Idle) {
                if (ui.outing.livePath.size >= 2) {
                    RouteMap(points = ui.outing.livePath, color = color, height = 220.dp, live = true)
                } else {
                    MapPlaceholder(status = ui.outing.gpsStatus)
                }
            }
        }

        ControlBar(
            controls = ui.controls,
            color = color,
            bottomInset = contentPadding.calculateBottomPadding(),
            onStart = begin,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onFinish = viewModel::requestFinish,
            onRetry = viewModel::retrySave,
            onDiscardAfterFailure = viewModel::discardAfterFailure,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    when (ui.dialog) {
        OutingDialog.Finish -> ConfirmDialog(
            title = stringResource(R.string.outing_finish_title),
            text = stringResource(R.string.outing_finish_text),
            confirmLabel = stringResource(R.string.outing_finish),
            confirmColor = colors.link,
            onConfirm = viewModel::confirmFinish,
            onDismiss = viewModel::dismissDialog,
        )
        OutingDialog.Discard -> ConfirmDialog(
            title = stringResource(R.string.outing_discard_title),
            text = stringResource(R.string.outing_discard_text),
            confirmLabel = stringResource(R.string.outing_abandon),
            confirmColor = colors.danger,
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::dismissDialog,
        )
        OutingDialog.None -> Unit
    }

    ui.alert?.let { alert ->
        val (title, text) = when (alert) {
            OutingAlert.Coarse -> R.string.outing_coarse_title to R.string.outing_coarse_text
            OutingAlert.Denied -> R.string.outing_denied_title to R.string.outing_denied_text
            OutingAlert.StartFailed -> R.string.outing_start_failed_title to R.string.outing_start_failed_text
            OutingAlert.SaveFailed -> R.string.outing_save_failed_title to R.string.outing_save_failed_text
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissAlert,
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(text)) },
            confirmButton = { TextButton(onClick = viewModel::dismissAlert) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveStatsCard(ui: OutingScreenUi, color: androidx.compose.ui.graphics.Color) {
    val colors = ElanTheme.colors
    val o = ui.outing
    val pace = ui.pace
    val hero = if (pace) paceParts(o.speedKmh) else speedParts(o.speedKmh)
    val best = if (pace) paceParts(o.maxSpeedKmh) else speedParts(o.maxSpeedKmh)
    val distance = distanceParts(o.distanceM)
    val cardio = hrParts(o.bpm?.toDouble())

    ElanCard {
        StatTile(
            label = stringResource(if (pace) R.string.outing_pace else R.string.outing_speed),
            value = hero.value,
            unit = hero.unit,
            icon = if (pace) MdiIcons.TimerOutline else MdiIcons.Speedometer,
            color = color,
            hero = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatTile(
                label = stringResource(R.string.outing_distance),
                value = distance.value,
                unit = distance.unit,
                icon = MdiIcons.MapMarkerDistance,
                color = color,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.outing_cardio),
                value = cardio.value,
                unit = cardio.unit,
                icon = MdiIcons.HeartPulse,
                color = colors.heart,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            maxItemsInEachRow = 2,
        ) {
            StatTile(
                label = stringResource(if (pace) R.string.outing_best_pace else R.string.outing_max_speed),
                value = best.value,
                unit = best.unit,
                icon = if (pace) MdiIcons.TimerOutline else MdiIcons.SpeedometerMedium,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            if (o.wheelSpeedKmh != null && !pace) {
                val wheel = speedParts(o.wheelSpeedKmh)
                StatTile(
                    label = stringResource(R.string.outing_wheel_speed),
                    value = wheel.value,
                    unit = wheel.unit,
                    icon = MdiIcons.BikeFast,
                    color = color,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (ui.hasCadenceSensor && !pace) {
                val cadence = cadenceParts(o.cadenceRpm?.toDouble())
                StatTile(
                    label = stringResource(R.string.outing_cadence),
                    value = cadence.value,
                    unit = cadence.unit,
                    icon = MdiIcons.RotateRight,
                    color = color,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            StatTile(
                label = stringResource(R.string.outing_elevation),
                value = o.elevationGainM.roundToInt().toString(),
                unit = "m",
                icon = MdiIcons.ElevationRise,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.outing_calories),
                value = o.caloriesLive.roundToInt().toString(),
                unit = "kcal",
                icon = MdiIcons.Fire,
                color = colors.warning,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ControlBar(
    controls: OutingControls,
    color: androidx.compose.ui.graphics.Color,
    bottomInset: androidx.compose.ui.unit.Dp,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onDiscardAfterFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Elevation.lg)
            .background(colors.backgroundElement)
            .padding(bottom = bottomInset),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .screenContent()
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            when (controls) {
                is OutingControls.Start -> ElanButton(
                    title = stringResource(R.string.outing_start),
                    icon = MdiIcons.Play,
                    color = color,
                    loading = controls.requesting,
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
                OutingControls.SaveFailed -> {
                    ElanButton(
                        title = stringResource(R.string.outing_abandon),
                        icon = MdiIcons.TrashCanOutline,
                        variant = ButtonVariant.Secondary,
                        color = colors.danger,
                        onClick = onDiscardAfterFailure,
                        modifier = Modifier.weight(1f),
                    )
                    ElanButton(
                        title = stringResource(R.string.outing_retry),
                        icon = MdiIcons.Refresh,
                        color = color,
                        onClick = onRetry,
                        modifier = Modifier.weight(1.4f),
                    )
                }
                is OutingControls.Running -> {
                    ElanButton(
                        title = stringResource(if (controls.paused) R.string.outing_resume else R.string.outing_pause),
                        icon = if (controls.paused) MdiIcons.Play else MdiIcons.Pause,
                        variant = ButtonVariant.Secondary,
                        color = color,
                        enabled = !controls.saving,
                        onClick = if (controls.paused) onResume else onPause,
                        modifier = Modifier.weight(1f),
                    )
                    ElanButton(
                        title = stringResource(R.string.outing_finish),
                        icon = MdiIcons.FlagCheckered,
                        color = color,
                        loading = controls.saving,
                        onClick = onFinish,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        }
    }
}

