package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ACTIVITY_TYPES
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.describeGoal
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.ElanChip
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.components.SettingStepper
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.settings.GoalForm
import ovh.battistella.elan.ui.screens.settings.goalActivityAllowed
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.forKey

/**
 * Carte Réglages : objectifs d'entraînement (liste + formulaire d'ajout).
 * 100 % local — les définitions vivent dans `settings` (incluses dans la
 * sauvegarde), la progression est calculée à la volée sur l'accueil.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsCard(
    goals: List<Goal>,
    form: GoalForm,
    onMetric: (GoalMetric) -> Unit,
    onActivity: (GoalActivity) -> Unit,
    onPeriod: (GoalPeriod) -> Unit,
    onTarget: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.Target, color = colors.accent, title = stringResource(R.string.settings_goals_title))
        CardText(stringResource(R.string.settings_goals_intro))

        goals.forEach { g ->
            val label = describeGoal(g)
            val removeLabel = stringResource(R.string.settings_goals_remove, label)
            HairlineRow {
                Icon(painter = painterResource(MdiIcons.FlagOutline), contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(label, color = colors.text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(MdiIcons.TrashCanOutline),
                    contentDescription = removeLabel,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .pressableScale(onClick = { onRemove(g.id) })
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
        }

        Hairline()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.settings_goals_new), style = ElanType.label, color = colors.textSecondary)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElanChip(stringResource(R.string.settings_goal_metric_sessions), form.metric == GoalMetric.SESSIONS, { onMetric(GoalMetric.SESSIONS) })
                ElanChip(stringResource(R.string.settings_goal_metric_distance), form.metric == GoalMetric.DISTANCE, { onMetric(GoalMetric.DISTANCE) })
                ElanChip(stringResource(R.string.settings_goal_metric_tonnage), form.metric == GoalMetric.TONNAGE, { onMetric(GoalMetric.TONNAGE) })
            }

            // Type d'activité : séances et distance ; le tonnage n'existe qu'en muscu.
            if (form.metric != GoalMetric.TONNAGE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElanChip(stringResource(R.string.settings_goal_activity_all), form.activity == GoalActivity.ALL, { onActivity(GoalActivity.ALL) })
                    ACTIVITY_TYPES.forEach { t ->
                        val a = GoalActivity.fromKey(t.key) ?: return@forEach
                        if (!goalActivityAllowed(form.metric, a)) return@forEach
                        ElanChip(t.meta.shortLabel, form.activity == a, { onActivity(a) }, color = colors.forKey(t.meta.colorKey))
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElanChip(stringResource(R.string.settings_goal_period_week), form.period == GoalPeriod.WEEK, { onPeriod(GoalPeriod.WEEK) }, color = colors.velo)
                ElanChip(stringResource(R.string.settings_goal_period_month), form.period == GoalPeriod.MONTH, { onPeriod(GoalPeriod.MONTH) }, color = colors.velo)
            }

            SettingStepper(
                label = stringResource(R.string.settings_goal_target),
                value = form.target,
                unit = form.spec.unit,
                step = form.spec.step,
                min = form.spec.min,
                max = form.spec.max,
                onChange = onTarget,
            )

            ElanButton(title = stringResource(R.string.settings_goals_add), icon = MdiIcons.Plus, onClick = onAdd, modifier = Modifier.fillMaxWidth())
        }
    }
}
