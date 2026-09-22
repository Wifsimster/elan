package ovh.battistella.elan.ui.screens.progression

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.ExerciseSummary
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.changeSummaryLine
import ovh.battistella.elan.domain.describeChange
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.domain.formatDateShort
import ovh.battistella.elan.ui.components.EmptyAction
import ovh.battistella.elan.ui.components.EmptyState
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.screens.common.TintedIconBox
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType

/**
 * Progression muscu : carte « Changements de la semaine » (progression auto),
 * puis un exercice par ligne vers sa fiche. Vide : invitation à démarrer.
 */
@Composable
fun ProgressionScreen(
    contentPadding: PaddingValues,
    viewModel: ProgressionViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
    onStartMuscu: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.screenContent()) {
                SubScreenHeader(title = stringResource(R.string.progression_title), onBack = onBack)
                if (ui.changes.isNotEmpty()) {
                    ElanCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painter = painterResource(MdiIcons.TrendingUp), contentDescription = null, tint = colors.muscu, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.progression_changes_title), style = ElanType.subtitle, color = colors.text)
                        }
                        Text(
                            stringResource(R.string.progression_changes_text, changeSummaryLine(ui.changes)),
                            style = ElanType.caption,
                            color = colors.textSecondary,
                        )
                        ui.changes.forEach { c ->
                            val up = c.direction == Direction.UP
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(if (up) MdiIcons.ArrowUpBold else MdiIcons.ArrowDownBold),
                                    contentDescription = null,
                                    tint = if (up) colors.success else colors.warning,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(describeChange(c), style = ElanType.bodySm, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.progression_intro), style = ElanType.label, color = colors.textSecondary)
                if (ui.items?.isEmpty() == true) {
                    EmptyState(
                        icon = MdiIcons.ChartLine,
                        tint = colors.muscu,
                        title = stringResource(R.string.progression_empty_title),
                        subtitle = stringResource(R.string.progression_empty_subtitle),
                        action = EmptyAction(stringResource(R.string.progression_empty_action), MdiIcons.Dumbbell, onStartMuscu),
                    )
                }
            }
        }
        val items = ui.items.orEmpty()
        items(count = items.size, key = { items[it].exercise }) { i ->
            val it = items[i]
            Column(modifier = Modifier.screenContent()) {
                ExerciseSummaryRow(item = it, onClick = { onOpenExercise(it.exercise) })
            }
        }
    }
}

/** Ligne d'index : pastille, nom, « N séances · dernière … », dernière charge, flèche de ressenti, chevron. */
@Composable
private fun ExerciseSummaryRow(item: ExerciseSummary, onClick: () -> Unit) {
    val colors = ElanTheme.colors
    val sessionsLabel = if (item.sessions > 1) {
        stringResource(R.string.progression_sessions_plural, item.sessions, formatDateShort(item.lastAt))
    } else {
        stringResource(R.string.progression_sessions, item.sessions, formatDateShort(item.lastAt))
    }
    ElanCard(modifier = Modifier.fillMaxWidth().pressableScale(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TintedIconBox(icon = MdiIcons.Dumbbell, color = colors.muscu, size = 46.dp, iconSize = 22.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(item.exercise, style = ElanType.subtitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sessionsLabel, style = ElanType.bodySm, color = colors.textSecondary)
            }
            Text(
                "${fmtKg(item.lastWeightKg)} kg",
                style = ElanType.metricSm,
                color = colors.muscu,
            )
            when (item.lastDifficulty) {
                Difficulty.FACILE -> Icon(
                    painter = painterResource(MdiIcons.ArrowUpBold),
                    contentDescription = stringResource(R.string.progression_easy_a11y),
                    tint = colors.success,
                    modifier = Modifier.size(18.dp),
                )
                Difficulty.DUR -> Icon(
                    painter = painterResource(MdiIcons.ArrowDownBold),
                    contentDescription = stringResource(R.string.progression_hard_a11y),
                    tint = colors.warning,
                    modifier = Modifier.size(18.dp),
                )
                else -> Unit
            }
            Icon(painter = painterResource(MdiIcons.ChevronRight), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(22.dp))
        }
    }
}
