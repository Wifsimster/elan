package ovh.battistella.elan.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ExercisePoint
import ovh.battistella.elan.domain.ProgressionAdvice
import ovh.battistella.elan.domain.adviceLabel
import ovh.battistella.elan.domain.difficultyLabel
import ovh.battistella.elan.domain.epley1RM
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.ui.components.BarChart
import ovh.battistella.elan.ui.components.EmptyState
import ovh.battistella.elan.ui.components.ExerciseIllustration
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.TagPill
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.screens.common.TintedIconBox
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanGradients
import ovh.battistella.elan.ui.theme.ElanType
import kotlin.math.roundToInt

/**
 * Fiche d'un exercice : guide (illustration, muscles, exécution), résumé
 * Record / Actuel / Depuis le début, 1RM, conseil de progression, courbe des
 * charges max et liste des séances (plus récente d'abord).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseScreen(
    contentPadding: PaddingValues,
    viewModel: ExerciseViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenSession: (Long) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val points = ui.points

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp)
            .screenContent(),
    ) {
        SubScreenHeader(title = ui.name, onBack = onBack)

        ui.guide?.let { guide ->
            ElanCard {
                ExerciseIllustration(imageKey = guide.imageKey, icon = guide.icon, height = 156.dp)
                if (guide.muscles.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        guide.muscles.forEach { TagPill(label = it, color = colors.muscu) }
                    }
                }
                if (guide.howTo.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.exercise_execution).uppercase(), style = ElanType.overline, color = colors.textMuted)
                        Text(guide.howTo, style = TextStyle(fontSize = 15.sp, lineHeight = 23.sp), color = colors.text)
                    }
                }
            }
        }

        if (points != null && points.isEmpty()) {
            EmptyState(
                icon = MdiIcons.ChartLine,
                tint = colors.muscu,
                title = stringResource(R.string.exercise_empty_title),
                subtitle = stringResource(R.string.exercise_empty_subtitle),
            )
        }

        if (!points.isNullOrEmpty()) {
            ElanCard {
                Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                    Metric(stringResource(R.string.exercise_record), "${fmtKg(ui.best)} kg", colors.muscu)
                    Metric(stringResource(R.string.exercise_current), "${fmtKg(ui.last)} kg")
                    val delta = ui.delta
                    Metric(
                        stringResource(R.string.exercise_since_start),
                        "${if (delta >= 0) "+" else ""}${fmtKg(delta)} kg",
                        if (delta > 0) colors.success else if (delta < 0) colors.warning else colors.textSecondary,
                    )
                }
            }

            if (ui.best1rm > 0) {
                ElanCard {
                    Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                        Metric(stringResource(R.string.exercise_1rm_current), "${ui.last1rm.roundToInt()} kg")
                        Metric(stringResource(R.string.exercise_1rm_record), "${ui.best1rm.roundToInt()} kg", colors.muscu)
                    }
                    Text(
                        stringResource(R.string.exercise_1rm_hint),
                        style = ElanType.caption,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AdviceCard(ui)

            ElanCard {
                Text(stringResource(R.string.exercise_max_per_session), style = ElanType.headline, color = colors.text)
                BarChart(data = ui.bars, gradient = ElanGradients.muscu, formatValue = { "${fmtKg(it)} kg" })
            }

            Text(stringResource(R.string.exercise_sessions), style = ElanType.headline, color = colors.text)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                points.asReversed().forEach { p ->
                    SessionRow(p = p, onClick = { onOpenSession(p.sessionId) })
                }
            }
        }
    }
}

/** Conseil de progression d'après le ressenti des dernières séances. */
@Composable
private fun AdviceCard(ui: ExerciseUi) {
    val colors = ElanTheme.colors
    val advice = ui.advice
    val adviceColor = when (advice) {
        ProgressionAdvice.AUGMENTE -> colors.success
        ProgressionAdvice.REDUIS -> colors.warning
        ProgressionAdvice.MAINTIENS -> colors.muscu
    }
    val adviceIcon = when (advice) {
        ProgressionAdvice.AUGMENTE -> MdiIcons.ArrowUpBold
        ProgressionAdvice.REDUIS -> MdiIcons.ArrowDownBold
        ProgressionAdvice.MAINTIENS -> MdiIcons.Equal
    }
    ElanCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TintedIconBox(
                icon = if (ui.hasRating) adviceIcon else MdiIcons.GestureTap,
                color = if (ui.hasRating) adviceColor else colors.textMuted,
                size = 42.dp,
                iconSize = 22.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.exercise_advice).uppercase(), style = ElanType.overline, color = colors.textMuted)
                if (ui.hasRating) {
                    Text(adviceLabel(advice), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.text)
                    ui.lastRating?.let {
                        Text(stringResource(R.string.exercise_last_session, difficultyLabel(it)), style = TextStyle(fontSize = 13.sp), color = colors.textSecondary)
                    }
                } else {
                    Text(stringResource(R.string.exercise_rate_hint), style = TextStyle(fontSize = 14.sp), color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(p: ExercisePoint, onClick: () -> Unit) {
    val colors = ElanTheme.colors
    val oneRm = epley1RM(p.maxWeightKg, p.topReps)
    ElanCard(modifier = Modifier.fillMaxWidth().pressableScale(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("${fmtKg(p.maxWeightKg)} kg × ${p.topReps}", style = ElanType.subtitle, color = colors.text)
                Text(formatDateTime(p.startedAt), style = TextStyle(fontSize = 13.sp), color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (oneRm > 0) {
                    Text(
                        stringResource(R.string.exercise_1rm_approx, oneRm.roundToInt()),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                        color = colors.muscu,
                    )
                }
                Text(
                    if (p.sets > 1) stringResource(R.string.exercise_sets_count_plural, p.sets) else stringResource(R.string.exercise_sets_count, p.sets),
                    style = TextStyle(fontSize = 13.sp),
                    color = colors.textSecondary,
                )
                Text(
                    stringResource(R.string.exercise_volume, p.volume.roundToInt()),
                    style = TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                    color = colors.textSecondary,
                )
            }
            Icon(painter = painterResource(MdiIcons.ChevronRight), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color? = null) {
    val colors = ElanTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = ElanType.metric.copy(fontSize = 20.sp), color = color ?: colors.text)
        Text(label, style = ElanType.caption, color = colors.textSecondary)
    }
}
