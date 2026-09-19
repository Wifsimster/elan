package ovh.battistella.elan.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.RecordKind
import ovh.battistella.elan.data.repository.RecordScope
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.domain.ACTIVITY_TYPES
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.ChartPoint
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.cadenceParts
import ovh.battistella.elan.domain.caloriesParts
import ovh.battistella.elan.domain.difficultyLabel
import ovh.battistella.elan.domain.distanceParts
import ovh.battistella.elan.domain.elevationParts
import ovh.battistella.elan.domain.elevationProfile
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.domain.hrParts
import ovh.battistella.elan.domain.hrProfile
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.paceParts
import ovh.battistella.elan.domain.sessionEffort
import ovh.battistella.elan.domain.speedParts
import ovh.battistella.elan.domain.speedProfile
import ovh.battistella.elan.domain.usesPace
import ovh.battistella.elan.domain.zoneDistribution
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.HrZonesCard
import ovh.battistella.elan.ui.components.LineChart
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.PulseChip
import ovh.battistella.elan.ui.components.RouteMap
import ovh.battistella.elan.ui.components.StatTile
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.haptics.rememberHaptics
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.HeaderAction
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.screens.common.TintedIconBox
import ovh.battistella.elan.ui.screens.common.ConfirmDialog
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.forKey
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import ovh.battistella.elan.ui.components.ChartPoint as UiChartPoint

/** Abscisse en km : entier dès 10 km, sinon une décimale (« 2,5 »). */
private fun fmtKm(v: Double): String =
    if (v >= 10) v.roundToInt().toString() else String.format(Locale.ROOT, "%.1f", v).replace('.', ',')

private fun List<ChartPoint>.toUi(): List<UiChartPoint> = map { UiChartPoint(it.x, it.y) }

/**
 * Détail d'une séance : en-tête, records, vignette du tracé, statistiques,
 * profils vitesse / altitude / FC, zones cardiaques, détail muscu, notes,
 * changement de type et actions (export, partage, suppression).
 */
@Composable
fun SessionDetailScreen(
    contentPadding: PaddingValues,
    viewModel: SessionDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenMap: (Long) -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val haptics = rememberHaptics()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionEvent.Deleted -> onBack()
                SessionEvent.RetypeSucceeded -> haptics(HapticKind.Success)
                SessionEvent.RetypeFailed -> haptics(HapticKind.Error)
            }
        }
    }

    val session = ui.session
    if (ui.loading) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(colors.background)) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }
    if (session == null) {
        Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(contentPadding).screenContent()) {
            SubScreenHeader(title = "", onBack = onBack)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(stringResource(R.string.session_not_found), color = colors.textSecondary)
            }
        }
        return
    }

    val meta = session.type.meta
    val color = colors.forKey(meta.colorKey)
    val isGps = isGpsActivity(session.type)
    val pace = usesPace(session.type)
    val points = ui.points
    val zones = zoneDistribution(points, ui.maxHr)
    val speed = if (isGps) speedProfile(points) else emptyList()
    val elevation = if (isGps) elevationProfile(points) else emptyList()
    val hr = if (isGps) hrProfile(points) else emptyList()
    val year = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).year

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .screenContent()
            .padding(top = 8.dp, bottom = 40.dp),
    ) {
        SubScreenHeader(title = meta.label, onBack = onBack) {
            HeaderAction(icon = MdiIcons.ShareVariant, label = stringResource(R.string.session_share), onClick = viewModel::share)
            HeaderAction(
                icon = MdiIcons.TrashCanOutline,
                label = stringResource(R.string.session_delete),
                tint = colors.danger,
                onClick = viewModel::requestDelete,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TintedIconBox(
                icon = MdiIcons.byName(meta.icon) ?: MdiIcons.Run,
                color = color,
                size = 54.dp,
                iconSize = 28.dp,
                radius = Radius.md,
            )
            Column {
                Text(meta.label, style = PulseType.headline, color = colors.text)
                Text(formatDateTime(session.startedAt, withYear = true), style = TextStyle(fontSize = 14.sp), color = colors.textSecondary)
            }
        }

        RecordsBanner(records = ui.records, year = year)

        if (isGps && points.size >= 2) {
            val expand = stringResource(R.string.session_expand_map)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = expand }
                    .pressableScale(onClick = { onOpenMap(session.id) }),
            ) {
                RouteMap(points = points, color = color)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .background(colors.backgroundElement.copy(alpha = 0.9f), RoundedCornerShape(Radius.sm)),
                ) {
                    Icon(painter = painterResource(MdiIcons.ArrowExpand), contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                }
            }
        }

        StatsCard(session = session, sets = ui.sets, maxHr = ui.maxHr, color = color, isGps = isGps, pace = pace)

        if (speed.size >= 2) {
            ChartCard(title = stringResource(R.string.session_chart_speed), unit = "km/h") {
                LineChart(
                    data = speed.toUi(),
                    color = color,
                    avg = session.avgSpeedKmh,
                    formatX = ::fmtKm,
                    label = stringResource(R.string.session_chart_speed_a11y),
                )
            }
        }
        if (elevation.size >= 2) {
            ChartCard(title = stringResource(R.string.session_chart_elevation), unit = "m") {
                LineChart(
                    data = elevation.toUi(),
                    color = colors.textSecondary,
                    formatX = ::fmtKm,
                    label = stringResource(R.string.session_chart_elevation_a11y),
                )
            }
        }
        if (hr.size >= 2) {
            ChartCard(title = stringResource(R.string.session_chart_hr), unit = "bpm") {
                LineChart(
                    data = hr.toUi(),
                    color = colors.heart,
                    avg = session.avgHr,
                    formatX = ::fmtKm,
                    label = stringResource(R.string.session_chart_hr_a11y),
                )
            }
        }

        if (zones != null) HrZonesCard(distribution = zones)

        if (session.type == ActivityType.MUSCU) {
            MuscuBreakdown(sets = ui.sets, color = color, onOpenExercise = onOpenExercise)
        }

        session.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            PulseCard {
                Text(stringResource(R.string.session_notes).uppercase(), style = PulseType.overline, color = colors.textSecondary)
                Text(notes, style = TextStyle(fontSize = 15.sp), color = colors.text)
            }
        }

        if (isGps) {
            SessionTypeCard(current = session.type, busy = ui.busy, onSelect = viewModel::requestRetype)
        }

        if (isGps && points.size >= 2) {
            PulseButton(
                title = stringResource(R.string.session_export_gpx),
                icon = MdiIcons.CloudUploadOutline,
                variant = ButtonVariant.Secondary,
                color = color,
                loading = ui.exporting,
                onClick = viewModel::exportGpx,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PulseButton(
            title = stringResource(R.string.session_share),
            icon = MdiIcons.ShareVariant,
            variant = ButtonVariant.Secondary,
            color = color,
            onClick = viewModel::share,
            modifier = Modifier.fillMaxWidth(),
        )
        PulseButton(
            title = stringResource(R.string.session_delete),
            icon = MdiIcons.TrashCanOutline,
            variant = ButtonVariant.Danger,
            onClick = viewModel::requestDelete,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (val dialog = ui.dialog) {
        SessionDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.session_delete_title),
            text = stringResource(R.string.session_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            confirmColor = colors.danger,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        is SessionDialog.Retype -> {
            val label = dialog.to.meta.label.lowercase()
            val cadence = if (session.type == ActivityType.VELO) stringResource(R.string.session_retype_cadence) else ""
            ConfirmDialog(
                title = stringResource(R.string.session_retype_title, label),
                text = stringResource(R.string.session_retype_text, label, cadence),
                confirmLabel = stringResource(R.string.session_retype_confirm),
                confirmColor = colors.accent,
                onConfirm = viewModel::confirmRetype,
                onDismiss = viewModel::dismissDialog,
                dismissLabel = stringResource(R.string.common_cancel),
            )
        }
        SessionDialog.None -> Unit
    }

    if (ui.retypeError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRetypeError,
            title = { Text(stringResource(R.string.session_retype_failed_title)) },
            text = { Text(stringResource(R.string.session_retype_failed_text)) },
            confirmButton = { TextButton(onClick = viewModel::dismissRetypeError) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (ui.sharePreview) {
        SharePreviewDialog(
            session = session,
            points = points,
            sets = ui.sets,
            records = ui.records,
            effort = sessionEffort(session, ui.maxHr),
            sharing = ui.sharing,
            onShare = viewModel::shareImage,
            onDismiss = viewModel::dismissSharePreview,
        )
    }
}

/**
 * Carte de statistiques. Vélo : temps en mouvement en tête, temps total
 * seulement s'il en diffère d'au moins 60 s ; muscu : séries + exercices ;
 * sinon la durée. Puis les métriques GPS, la FC, les calories et l'effort.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsCard(session: Session, sets: List<MuscuSet>, maxHr: Double, color: Color, isGps: Boolean, pace: Boolean) {
    val colors = ElanTheme.colors
    val effort = sessionEffort(session, maxHr)
    val movingSec = if (isGps) session.movingTimeSec else null
    val showTotalTime = movingSec != null && session.durationSec - movingSec >= 60

    PulseCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            maxItemsInEachRow = 2,
        ) {
            when {
                movingSec != null -> CompactTile(stringResource(R.string.session_moving), formatDuration(movingSec), icon = MdiIcons.ClockOutline)
                session.type == ActivityType.MUSCU -> {
                    CompactTile(stringResource(R.string.session_sets), sets.size.toString(), icon = MdiIcons.FormatListNumbered, color = color)
                    CompactTile(
                        stringResource(R.string.session_exercises),
                        sets.map { it.exercise }.toSet().size.toString(),
                        icon = MdiIcons.Dumbbell,
                        color = color,
                    )
                }
                else -> CompactTile(stringResource(R.string.session_duration), formatDuration(session.durationSec), icon = MdiIcons.ClockOutline)
            }
            if (showTotalTime) {
                CompactTile(stringResource(R.string.session_total_time), formatDuration(session.durationSec), icon = MdiIcons.TimerOutline)
            }
            if (isGps) {
                val distance = distanceParts(session.distanceM)
                val avg = if (pace) paceParts(session.avgSpeedKmh) else speedParts(session.avgSpeedKmh)
                val max = if (pace) paceParts(session.maxSpeedKmh) else speedParts(session.maxSpeedKmh)
                val elevation = elevationParts(session.elevationGainM)
                CompactTile(stringResource(R.string.outing_distance), distance.value, distance.unit, MdiIcons.MapMarkerDistance, color)
                CompactTile(
                    stringResource(if (pace) R.string.session_avg_pace else R.string.session_avg_speed),
                    avg.value,
                    avg.unit,
                    if (pace) MdiIcons.TimerOutline else MdiIcons.Speedometer,
                )
                CompactTile(
                    stringResource(if (pace) R.string.outing_best_pace else R.string.outing_max_speed),
                    max.value,
                    max.unit,
                    if (pace) MdiIcons.TimerOutline else MdiIcons.SpeedometerMedium,
                )
                CompactTile(stringResource(R.string.outing_elevation), elevation.value, elevation.unit, MdiIcons.ElevationRise)
                session.avgCadence?.let { c ->
                    val parts = cadenceParts(c)
                    CompactTile(stringResource(R.string.session_avg_cadence), parts.value, parts.unit, MdiIcons.RotateRight, color)
                }
                session.maxCadence?.let { c ->
                    val parts = cadenceParts(c)
                    CompactTile(stringResource(R.string.session_max_cadence), parts.value, parts.unit, MdiIcons.RotateRight, color)
                }
            }
            val avgHr = hrParts(session.avgHr)
            val maxHrParts = hrParts(session.maxHr)
            val calories = caloriesParts(session.calories)
            CompactTile(stringResource(R.string.session_avg_hr), avgHr.value, avgHr.unit, MdiIcons.HeartPulse, colors.heart)
            CompactTile(stringResource(R.string.session_max_hr), maxHrParts.value, maxHrParts.unit, MdiIcons.Heart, colors.heart)
            CompactTile(stringResource(R.string.outing_calories), calories.value, calories.unit, MdiIcons.Fire, colors.warning)
            CompactTile(stringResource(R.string.session_effort), effort.label, null, MdiIcons.Speedometer, colors.forKey(effort.colorKey))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.FlowRowScope.CompactTile(
    label: String,
    value: String,
    unit: String? = null,
    icon: Int? = null,
    color: Color? = null,
) {
    StatTile(label = label, value = value, unit = unit, icon = icon, color = color, compact = true, modifier = Modifier.weight(1f))
}

/** Carte de section pour un graphe : titre + unité + contenu. */
@Composable
private fun ChartCard(title: String, unit: String, content: @Composable () -> Unit) {
    val colors = ElanTheme.colors
    PulseCard {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(title, style = PulseType.headline, color = colors.text)
            Text(unit, style = PulseType.caption, color = colors.textMuted)
        }
        content()
    }
}

@Composable
private fun recordNoun(kind: RecordKind): String = stringResource(
    when (kind) {
        RecordKind.DISTANCE -> R.string.records_distance
        RecordKind.ELEVATION -> R.string.records_elevation
        RecordKind.DURATION -> R.string.records_duration
        RecordKind.SPEED -> R.string.records_speed
    },
)

private fun recordIcon(kind: RecordKind): Int = when (kind) {
    RecordKind.DISTANCE -> MdiIcons.MapMarkerDistance
    RecordKind.ELEVATION -> MdiIcons.ElevationRise
    RecordKind.DURATION -> MdiIcons.ClockOutline
    RecordKind.SPEED -> MdiIcons.Speedometer
}

/** Bannière de records, façon « PR » Strava : absolus d'abord, trois au plus. */
@Composable
internal fun RecordsBanner(records: List<SessionRecord>, year: Int) {
    if (records.isEmpty()) return
    val colors = ElanTheme.colors
    val top = topRecords(records)
    val hasAllTime = top.any { it.scope == RecordScope.ALL }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.08f), RoundedCornerShape(Radius.lg))
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TintedIconBox(icon = MdiIcons.Trophy, color = colors.warning, size = 40.dp, iconSize = 22.dp)
            Text(
                text = if (hasAllTime) stringResource(R.string.records_personal) else stringResource(R.string.records_year, year),
                style = PulseType.headline,
                color = colors.text,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            top.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(recordIcon(r.kind)), contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                    val scope = if (r.scope == RecordScope.ALL) stringResource(R.string.records_all_time) else stringResource(R.string.records_of_year, year)
                    Text(
                        text = "${stringResource(R.string.records_best, recordNoun(r.kind))} $scope",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                    )
                }
            }
        }
    }
}

/** Une carte par exercice : nom (→ fiche), ressenti, volume, lignes « n — reps × kg ». */
@Composable
internal fun MuscuBreakdown(sets: List<MuscuSet>, color: Color, onOpenExercise: (String) -> Unit) {
    if (sets.isEmpty()) return
    val colors = ElanTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groupSets(sets).forEach { g ->
            val difficulty = g.difficulty
            val diffColor = when (difficulty) {
                Difficulty.FACILE -> colors.success
                Difficulty.MOYEN -> colors.warning
                Difficulty.DUR, null -> colors.danger
            }
            PulseCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableScale(haptic = null, onClick = { onOpenExercise(g.name) }),
                ) {
                    Text(
                        text = g.name,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 21.sp),
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (difficulty != null) {
                        Text(
                            text = difficultyLabel(difficulty),
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = diffColor,
                            modifier = Modifier
                                .background(diffColor.copy(alpha = 0.13f), RoundedCornerShape(Radius.pill))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.session_volume, g.volume.roundToInt()),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = colors.textSecondary,
                    )
                    Icon(painter = painterResource(MdiIcons.ChevronRight), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                }
                g.rows.forEach { r ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(r.setIndex.toString(), style = TextStyle(fontWeight = FontWeight.Bold), color = colors.textSecondary, modifier = Modifier.width(22.dp))
                        Text(
                            stringResource(R.string.session_reps, r.reps),
                            style = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            color = colors.text,
                        )
                        Text("×", color = colors.textSecondary)
                        Text(
                            "${fmtKg(r.weightKg)} kg",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carte « Type d'activité » : corrige après coup une sortie enregistrée sous
 * le mauvais type. Limitée aux activités GPS ; la confirmation (portée par
 * l'écran) dit ce qui va bouger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionTypeCard(current: ActivityType, busy: Boolean, onSelect: (ActivityType) -> Unit) {
    val colors = ElanTheme.colors
    PulseCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.session_type_title).uppercase(), style = PulseType.overline, color = colors.textSecondary)
            if (busy) CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ACTIVITY_TYPES.filter { isGpsActivity(it) }.forEach { t ->
                PulseChip(
                    label = t.meta.shortLabel,
                    selected = t == current,
                    color = colors.forKey(t.meta.colorKey),
                    onClick = { onSelect(t) },
                )
            }
        }
        Text(stringResource(R.string.session_type_hint), style = PulseType.caption, color = colors.textSecondary)
    }
}
