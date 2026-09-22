package ovh.battistella.elan.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.GoalProgress
import ovh.battistella.elan.domain.PeriodStats
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.changeSummaryLine
import ovh.battistella.elan.domain.describeGoal
import ovh.battistella.elan.domain.distanceParts
import ovh.battistella.elan.domain.formatDistance
import ovh.battistella.elan.domain.formatDurationShort
import ovh.battistella.elan.domain.formatGoalValue
import ovh.battistella.elan.domain.formatRelativeDays
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.ui.components.BarChart
import ovh.battistella.elan.ui.components.BarPoint
import ovh.battistella.elan.ui.components.ButtonSize
import ovh.battistella.elan.ui.components.EmptyState
import ovh.battistella.elan.ui.components.HrBadge
import ovh.battistella.elan.ui.components.ElanButton
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.SessionRow
import ovh.battistella.elan.ui.components.StatTile
import ovh.battistella.elan.ui.components.Trend
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.LinkCard
import ovh.battistella.elan.ui.screens.common.TintedIconBox
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.forKey
import kotlin.math.roundToInt

/**
 * Accueil : en-tête + pastille cardio, bannière de progression, séance du
 * jour, grille de démarrage, lien catalogue, résumé de la semaine, objectifs,
 * activité sur 7 jours et séances récentes. L'onboarding se superpose tant que
 * le profil n'a pas été confirmé.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
    onStartOuting: (ActivityType) -> Unit = {},
    onStartMuscu: (TemplateId?) -> Unit = {},
    onOpenCatalog: () -> Unit = {},
    onOpenProgression: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSession: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /**
     * Feuille de restauration (premier lancement) : emplacement injectable pour
     * que les tests fournissent leur propre `BackupViewModel`.
     */
    restoreSheet: @Composable (onCancel: () -> Unit, onRestored: (Int) -> Unit) -> Unit = { onCancel, onRestored ->
        RestoreSheet(onCancel = onCancel, onRestored = onRestored)
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val heart by viewModel.heart.collectAsStateWithLifecycle()
    val restoreOpen by viewModel.restoreOpen.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors

    // Recharge à chaque retour sur l'écran (séance fraîchement enregistrée).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    if (restoreOpen) {
        restoreSheet(viewModel::closeRestore, viewModel::restoreFinished)
    } else {
        ui.onboarding?.let { profile ->
            OnboardingSheet(
                initial = profile,
                onDone = { w, h, hr, goal -> viewModel.finishOnboarding(w, h, hr, goal) },
                onRestore = { viewModel.requestRestore() },
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .screenContent()
            .padding(top = 12.dp, bottom = 32.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_greeting), style = ElanType.label, color = colors.textSecondary)
                Text(stringResource(R.string.app_name), style = ElanType.title, color = colors.text)
            }
            HrBadge(bpm = heart.bpm, connected = heart.connected, onClick = onOpenSettings)
        }

        ui.planUpdate?.let { state ->
            PlanUpdateBanner(state = state, onOpen = onOpenProgression, onDismiss = { viewModel.dismissPlanUpdate() })
        }

        if (ui.loaded) {
            TodayCard(
                plan = ui.today,
                jsDay = ui.jsDay,
                lastSessionAt = ui.recent.firstOrNull()?.startedAt,
                now = ui.now,
                resumable = ui.resumable,
                onStartOuting = onStartOuting,
                onStartMuscu = onStartMuscu,
            )
        }

        StartGrid(resumable = ui.resumable, onStartOuting = onStartOuting, onStartMuscu = { onStartMuscu(null) })

        LinkCard(
            icon = MdiIcons.ViewGridOutline,
            color = colors.muscu,
            title = stringResource(R.string.home_catalog_title),
            subtitle = stringResource(R.string.home_catalog_subtitle),
            onClick = onOpenCatalog,
        )

        WeekCard(stats = ui.stats, lastStats = ui.lastStats)

        if (ui.goals.isNotEmpty()) GoalsProgressCard(items = ui.goals)

        ElanCard {
            Text(stringResource(R.string.home_activity_title), style = ElanType.headline, color = colors.text)
            BarChart(
                data = ui.bars.map { BarPoint(it.label, it.value.toDouble()) },
                formatValue = { formatDurationShort(it) },
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_recent), style = ElanType.headline, color = colors.text)
            Text(
                text = stringResource(R.string.home_see_all),
                color = colors.accent,
                style = TextStyle(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .pressableScale(haptic = null, onClick = onOpenHistory)
                    .padding(4.dp),
            )
        }

        if (ui.loaded && ui.recent.isEmpty()) {
            ElanCard {
                EmptyState(
                    icon = MdiIcons.RunFast,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.recent.forEach { s -> SessionRow(session = s, onClick = { onOpenSession(s.id) }) }
            }
        }
    }
}

/** Séance prévue aujourd'hui (planning), ou repos. */
@Composable
internal fun TodayCard(
    plan: PlannedSession,
    jsDay: Int,
    lastSessionAt: Long?,
    now: Long,
    resumable: Boolean,
    onStartOuting: (ActivityType) -> Unit,
    onStartMuscu: (TemplateId?) -> Unit,
) {
    val colors = ElanTheme.colors
    val dayName = WEEKDAYS_FR[jsDay.coerceIn(0, 6)]
    val lastLabel = lastSessionAt?.let { stringResource(R.string.home_last_session, formatRelativeDays(it, now)) }
    val secondaryStyle = TextStyle(fontSize = 13.sp)
    val mutedStyle = TextStyle(fontSize = 12.sp)

    if (plan is PlannedSession.Repos) {
        ElanCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TintedIconBox(icon = MdiIcons.Sleep, color = colors.textSecondary, size = 46.dp, iconSize = 24.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_today, dayName), style = ElanType.label, color = colors.textSecondary)
                    Text(stringResource(R.string.home_rest), style = ElanType.subtitle, color = colors.text)
                    Text(stringResource(R.string.home_rest_hint), style = secondaryStyle, color = colors.textSecondary)
                    if (lastLabel != null) {
                        Text(lastLabel, style = mutedStyle, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        return
    }

    val type = ActivityType.fromKey(plan.kind) ?: ActivityType.VELO
    val meta = type.meta
    val color = colors.forKey(meta.colorKey)
    val muscu = plan as? PlannedSession.Muscu
    val isResumeMuscu = muscu != null && resumable
    val label = when (plan) {
        is PlannedSession.Muscu -> plan.label
        is PlannedSession.Outing -> plan.label
        else -> meta.label
    }
    val subtitle = if (muscu != null) {
        templateById(muscu.templateId).exercises.joinToString(" · ") { it.name }
    } else {
        stringResource(R.string.home_active_recovery)
    }

    ElanCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TintedIconBox(icon = MdiIcons.byName(meta.icon) ?: MdiIcons.Run, color = color, size = 46.dp, iconSize = 24.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_today, dayName), style = ElanType.label, color = colors.textSecondary)
                Text(label, style = ElanType.subtitle, color = colors.text)
                Text(subtitle, style = secondaryStyle, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lastLabel != null) {
                    Text(lastLabel, style = mutedStyle, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        ElanButton(
            title = stringResource(if (isResumeMuscu) R.string.home_resume else R.string.home_start_session),
            icon = MdiIcons.Play,
            color = color,
            onClick = { if (muscu != null) onStartMuscu(muscu.templateId) else onStartOuting(type) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** « Démarrer une séance » : grille 2×2 Vélo / Muscu (ou Reprendre) / Course / Marche. */
@Composable
private fun StartGrid(
    resumable: Boolean,
    onStartOuting: (ActivityType) -> Unit,
    onStartMuscu: () -> Unit,
) {
    val colors = ElanTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.home_start_overline).uppercase(),
            style = ElanType.overline,
            color = colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElanButton(
                title = ActivityType.VELO.meta.shortLabel,
                icon = MdiIcons.Bike,
                size = ButtonSize.Lg,
                color = colors.velo,
                onClick = { onStartOuting(ActivityType.VELO) },
                modifier = Modifier.weight(1f),
            )
            ElanButton(
                title = if (resumable) stringResource(R.string.home_resume) else ActivityType.MUSCU.meta.shortLabel,
                icon = if (resumable) MdiIcons.Play else MdiIcons.Dumbbell,
                size = ButtonSize.Lg,
                color = colors.muscu,
                onClick = onStartMuscu,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElanButton(
                title = ActivityType.COURSE.meta.shortLabel,
                icon = MdiIcons.Run,
                size = ButtonSize.Lg,
                color = colors.course,
                onClick = { onStartOuting(ActivityType.COURSE) },
                modifier = Modifier.weight(1f),
            )
            ElanButton(
                title = ActivityType.MARCHE.meta.shortLabel,
                icon = MdiIcons.Walk,
                size = ButtonSize.Lg,
                color = colors.marche,
                onClick = { onStartOuting(ActivityType.MARCHE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Résumé de la semaine : 4 tuiles compactes avec tendance vs semaine dernière. */
@Composable
private fun WeekCard(stats: PeriodStats?, lastStats: PeriodStats?) {
    val colors = ElanTheme.colors
    val stable = stringResource(R.string.common_trend_stable)
    val both = stats != null && lastStats != null
    fun trend(current: Double, previous: Double, fmt: (Double) -> String): Trend? =
        if (both) buildTrend(current, previous, stable, fmt) else null
    val distance = distanceParts(stats?.totalDistanceM ?: 0.0)

    ElanCard {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_week_title), style = ElanType.headline, color = colors.text)
            if (both) Text(stringResource(R.string.home_vs_last_week), style = ElanType.caption, color = colors.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile(
                label = stringResource(R.string.home_stat_sessions),
                value = (stats?.sessionCount ?: 0).toString(),
                icon = MdiIcons.CalendarCheck,
                compact = true,
                trend = trend(stats?.sessionCount?.toDouble() ?: 0.0, lastStats?.sessionCount?.toDouble() ?: 0.0) {
                    it.roundToInt().toString()
                },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_stat_duration),
                value = formatDurationShort(stats?.totalDurationSec ?: 0),
                icon = MdiIcons.ClockOutline,
                compact = true,
                trend = trend(
                    stats?.totalDurationSec?.toDouble() ?: 0.0,
                    lastStats?.totalDurationSec?.toDouble() ?: 0.0,
                ) { formatDurationShort(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile(
                label = stringResource(R.string.home_stat_distance),
                value = distance.value,
                unit = distance.unit,
                icon = MdiIcons.MapMarkerDistance,
                compact = true,
                trend = trend(stats?.totalDistanceM ?: 0.0, lastStats?.totalDistanceM ?: 0.0) { formatDistance(it) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_stat_calories),
                value = (stats?.totalCalories ?: 0.0).roundToInt().toString(),
                unit = "kcal",
                icon = MdiIcons.Fire,
                color = colors.warning,
                compact = true,
                trend = trend(stats?.totalCalories ?: 0.0, lastStats?.totalCalories ?: 0.0) { it.roundToInt().toString() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Avancement des objectifs sur la période courante (masquée sans objectif). */
@Composable
internal fun GoalsProgressCard(items: List<GoalProgress>) {
    val colors = ElanTheme.colors
    ElanCard {
        Text(stringResource(R.string.home_goals_title), style = ElanType.headline, color = colors.text)
        items.forEach { p ->
            val pct = (p.ratio * 100).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = describeGoal(p.goal),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (p.done) {
                        Icon(
                            painter = painterResource(MdiIcons.CheckCircle),
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = "$pct %",
                            style = TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                            color = colors.textSecondary,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.backgroundSelected)
                        .semantics { contentDescription = "$pct %" },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p.ratio.toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(if (p.done) colors.success else colors.accent, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    text = stringResource(R.string.home_goal_done, formatGoalValue(p.goal, p.value)),
                    style = TextStyle(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            }
        }
    }
}

/**
 * Bannière : programme muscu relevé cette semaine (progression auto). Toucher
 * mène à la revue sur la page Progression ; la croix la masque.
 */
@Composable
internal fun PlanUpdateBanner(state: AutoProgressionState, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = ElanTheme.colors
    val dismissLabel = stringResource(R.string.plan_update_dismiss)
    ElanCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .pressableScale(haptic = HapticKind.Light, onClick = onOpen),
            ) {
                TintedIconBox(icon = MdiIcons.TrendingUp, color = colors.muscu, size = 42.dp, iconSize = 22.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.plan_update_title), style = ElanType.subtitle, color = colors.text)
                    Text(changeSummaryLine(state.changes), style = TextStyle(fontSize = 13.sp), color = colors.textSecondary)
                }
            }
            Icon(
                painter = painterResource(MdiIcons.Close),
                contentDescription = dismissLabel,
                tint = colors.textMuted,
                modifier = Modifier
                    .pressableScale(onClick = onDismiss)
                    .padding(8.dp)
                    .size(20.dp),
            )
        }
    }
}
