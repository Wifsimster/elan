package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.ElanChip
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.settings.WEEK_PLAN_OPTIONS
import ovh.battistella.elan.ui.screens.settings.isOptionActive
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.forKey

/** Libellés des jours, lundi en tête (index 0 = lundi, comme le planning). */
val WEEK_DAY_LABELS: List<String> = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Carte Réglages : planning hebdomadaire personnalisable (séance prévue par jour). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekPlanCard(
    plan: List<PlannedSession>,
    onDayChange: (dayIndex: Int, planned: PlannedSession) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.CalendarWeek, color = colors.accent, title = stringResource(R.string.settings_plan_title))
        CardText(stringResource(R.string.settings_plan_intro))

        WEEK_DAY_LABELS.forEachIndexed { i, day ->
            if (i > 0) Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = if (i == 0) 0.dp else 2.dp)) {
                Text(day, style = ElanType.label, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEK_PLAN_OPTIONS.forEach { opt ->
                        val entry = plan.getOrNull(i) ?: PlannedSession.Repos
                        val color = when (val p = opt.planned) {
                            PlannedSession.Repos -> colors.textSecondary
                            is PlannedSession.Outing -> colors.forKey(ActivityType.fromKey(p.kind)!!.meta.colorKey)
                            is PlannedSession.Muscu -> colors.muscu
                        }
                        ElanChip(
                            label = opt.label,
                            selected = isOptionActive(opt, entry),
                            color = color,
                            onClick = { onDayChange(i, opt.planned) },
                        )
                    }
                }
            }
        }

        ElanButton(
            title = stringResource(R.string.settings_plan_reset),
            icon = MdiIcons.Restore,
            variant = ButtonVariant.Secondary,
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
