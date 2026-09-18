package ovh.battistella.elan.ui.screens.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.BodyMeasurement
import ovh.battistella.elan.domain.formatDateShort
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.ui.components.ChartPoint
import ovh.battistella.elan.ui.components.EmptyState
import ovh.battistella.elan.ui.components.LineChart
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.screens.common.ConfirmDialog
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.roundToLong

/**
 * Journal de poids : nouvelle pesée, métriques (actuel / 30 jours / depuis le
 * début), courbe d'évolution et journal en bloc continu avec suppression
 * confirmée. Le journal est virtualisé (des centaines de lignes possibles).
 */
@Composable
fun WeightScreen(
    contentPadding: PaddingValues,
    viewModel: WeightViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val list = ui.items.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        item(key = "header") {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.screenContent().padding(bottom = 14.dp),
            ) {
                SubScreenHeader(title = stringResource(R.string.weight_title), onBack = onBack)
                Text(stringResource(R.string.weight_intro), style = PulseType.label, color = colors.textSecondary)

                PulseCard {
                    SettingCardHeader(icon = MdiIcons.ScaleBathroom, color = colors.accent, title = stringResource(R.string.weight_new))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        WeightInput(value = ui.input, onValueChange = viewModel::setInput, modifier = Modifier.weight(1f))
                        Text("kg", style = PulseType.subtitle, color = colors.textSecondary)
                    }
                    PulseButton(
                        title = stringResource(R.string.weight_save),
                        icon = MdiIcons.Check,
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ui.error) {
                        Text(stringResource(R.string.weight_invalid), style = TextStyle(fontSize = 13.sp), color = colors.danger)
                    }
                }

                if (ui.items != null && list.isEmpty()) {
                    EmptyState(
                        icon = MdiIcons.ScaleBathroom,
                        title = stringResource(R.string.weight_empty_title),
                        subtitle = stringResource(R.string.weight_empty_subtitle),
                    )
                }

                ui.latest?.let { latest ->
                    PulseCard {
                        Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                            Metric(label = stringResource(R.string.weight_current), value = "${fmtWeight(latest.weightKg)} kg", color = colors.accent)
                            Metric(
                                label = stringResource(R.string.weight_30d),
                                value = ui.deltaMonth?.let { "${fmtWeightDelta(it)} kg" } ?: "—",
                            )
                            Metric(
                                label = stringResource(R.string.weight_since_start),
                                value = ui.deltaSinceStart?.let { "${fmtWeightDelta(it)} kg" } ?: "—",
                            )
                        }
                    }
                }

                if (list.size >= 2) {
                    PulseCard {
                        Text(stringResource(R.string.weight_evolution), style = PulseType.headline, color = colors.text)
                        LineChart(
                            data = list.asReversed().map { ChartPoint(it.measuredAt.toDouble(), it.weightKg) },
                            color = colors.accent,
                            label = stringResource(R.string.weight_title),
                            formatY = { fmtWeight((it * 10).roundToLong() / 10.0) },
                            formatX = { formatDateShort(it.toLong()) },
                        )
                    }
                }

                if (list.isNotEmpty()) {
                    Text(stringResource(R.string.weight_entries), style = PulseType.headline, color = colors.text)
                }
            }
        }

        items(count = list.size, key = { list[it].id }) { index ->
            val m = list[index]
            val prev = list.getOrNull(index + 1)
            Box(Modifier.screenContent()) {
                WeightRow(
                    measurement = m,
                    delta = prev?.let { m.weightKg - it.weightKg },
                    first = index == 0,
                    last = index == list.lastIndex,
                    onDelete = { viewModel.requestDelete(m) },
                )
            }
        }
    }

    ui.pendingDelete?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.weight_delete_title),
            text = "${fmtWeight(m.weightKg)} kg — ${formatDateTime(m.measuredAt, withYear = true)}",
            confirmLabel = stringResource(R.string.common_delete),
            confirmColor = colors.danger,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
            dismissLabel = stringResource(R.string.common_cancel),
        )
    }
}

/** Champ décimal (6 caractères max), chiffres tabulaires. */
@Composable
private fun WeightInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    val a11y = stringResource(R.string.weight_input_a11y)
    val shape = RoundedCornerShape(Radius.sm)
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 6) onValueChange(it) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.text, fontFeatureSettings = "tnum"),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.semantics { contentDescription = a11y },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.backgroundSelected, shape)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) { inner() }
        },
    )
}

@Composable
private fun Metric(label: String, value: String, color: Color? = null) {
    val colors = ElanTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = PulseType.metric.copy(fontSize = 20.sp), color = color ?: colors.text)
        Text(label, style = PulseType.caption, color = colors.textSecondary)
    }
}

/** Ligne du journal : bloc continu, coins arrondis en tête et en queue, séparateurs hairline. */
@Composable
private fun WeightRow(measurement: BodyMeasurement, delta: Double?, first: Boolean, last: Boolean, onDelete: () -> Unit) {
    val colors = ElanTheme.colors
    val date = formatDateTime(measurement.measuredAt, withYear = true)
    val deleteLabel = stringResource(R.string.weight_delete_a11y, date)
    val shape = RoundedCornerShape(
        topStart = if (first) Radius.lg else 0.dp,
        topEnd = if (first) Radius.lg else 0.dp,
        bottomStart = if (last) Radius.lg else 0.dp,
        bottomEnd = if (last) Radius.lg else 0.dp,
    )
    Column(modifier = Modifier.fillMaxWidth().background(colors.backgroundElement, shape)) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("${fmtWeight(measurement.weightKg)} kg", style = PulseType.subtitle.copy(fontFeatureSettings = "tnum"), color = colors.text)
                Text(date, style = TextStyle(fontSize = 13.sp), color = colors.textSecondary)
            }
            if (delta != null) {
                Text(
                    "${fmtWeightDelta(delta)} kg",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = colors.textSecondary,
                )
            }
            Icon(
                painter = painterResource(MdiIcons.TrashCanOutline),
                contentDescription = deleteLabel,
                tint = colors.textMuted,
                modifier = Modifier
                    .pressableScale(onClick = onDelete)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
    }
}
