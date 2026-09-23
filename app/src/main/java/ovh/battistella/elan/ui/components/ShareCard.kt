// Carte partageable d'une séance, façon Strava : tracé (ou héros d'activité)
// + stats + marque « Élan ». Rendue dans l'aperçu de partage puis capturée en
// PNG (GraphicsLayer), sans aucun appel réseau : le partage est délégué à l'OS.
package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.RecordKind
import ovh.battistella.elan.data.repository.RecordScope
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.domain.Effort
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.formatCalories
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.domain.formatDistance
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.domain.formatHr
import ovh.battistella.elan.domain.formatPace
import ovh.battistella.elan.domain.formatSpeed
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.usesPace
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanGradients
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.forKey
import kotlin.math.roundToInt

/** Largeur de la carte (dp) — le PNG partagé a ce ratio. */
val SHARE_CARD_WIDTH = 360.dp

/** Hauteur du visuel d'en-tête (tracé). */
val SHARE_HERO_HEIGHT = 200.dp

private data class StatItem(val label: String, val value: String, val color: Color? = null)

private fun recordNoun(kind: RecordKind): String = when (kind) {
    RecordKind.DISTANCE -> "distance"
    RecordKind.ELEVATION -> "dénivelé"
    RecordKind.DURATION -> "durée"
    RecordKind.SPEED -> "vitesse"
}

/**
 * Carte de partage : héros (tracé GPS ou dégradé d'activité + icône), marque,
 * activité et date, grille de stats sur 3 colonnes, badge record (trophée) ou
 * niveau d'effort.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareCard(
    session: Session,
    points: List<TrackPoint>,
    sets: List<MuscuSet>,
    records: List<SessionRecord>,
    effort: Effort,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val meta = session.type.meta
    val color = colors.forKey(meta.colorKey)
    val isGps = isGpsActivity(session.type)
    val pace = usesPace(session.type)
    val hasRoute = isGps && points.size >= 2

    val stats = buildList {
        if (isGps) {
            add(StatItem(stringResource(R.string.share_stat_distance), formatDistance(session.distanceM), color))
            // Temps en mouvement (hors arrêts) s'il est connu, sinon durée totale.
            add(StatItem(stringResource(R.string.share_stat_duration), formatDuration(session.movingTimeSec ?: session.durationSec)))
            add(
                if (pace) StatItem(stringResource(R.string.share_stat_pace), formatPace(session.avgSpeedKmh))
                else StatItem(stringResource(R.string.share_stat_speed), formatSpeed(session.avgSpeedKmh)),
            )
            add(StatItem(stringResource(R.string.share_stat_elevation), "${(session.elevationGainM ?: 0.0).roundToInt()} m"))
            if (session.avgHr != null) add(StatItem(stringResource(R.string.share_stat_hr), formatHr(session.avgHr), colors.heart))
            add(StatItem(stringResource(R.string.share_stat_calories), formatCalories(session.calories), colors.warning))
        } else {
            val volume = sets.sumOf { it.reps * it.weightKg }
            val exercises = sets.map { it.exercise }.toSet().size
            add(StatItem(stringResource(R.string.share_stat_duration), formatDuration(session.durationSec)))
            add(StatItem(stringResource(R.string.share_stat_exercises), exercises.toString()))
            add(StatItem(stringResource(R.string.share_stat_volume), "${volume.roundToInt()} kg", color))
            if (session.avgHr != null) add(StatItem(stringResource(R.string.share_stat_hr), formatHr(session.avgHr), colors.heart))
            add(StatItem(stringResource(R.string.share_stat_calories), formatCalories(session.calories), colors.warning))
        }
    }

    // Badge : un record si la séance en détient un (absolu d'abord), sinon l'effort.
    val topRecord = records.sortedBy { if (it.scope == RecordScope.ALL) 0 else 1 }.firstOrNull()
    val badgeIcon: Int
    val badgeLabel: String
    val badgeColor: Color
    if (topRecord != null) {
        badgeIcon = MdiIcons.Trophy
        badgeLabel = stringResource(
            if (topRecord.scope == RecordScope.ALL) R.string.share_record_all else R.string.share_record_year,
            recordNoun(topRecord.kind),
        )
        badgeColor = colors.warning
    } else {
        badgeIcon = MdiIcons.Speedometer
        badgeLabel = stringResource(R.string.share_effort, effort.label)
        badgeColor = colors.forKey(effort.colorKey)
    }

    Column(
        modifier = modifier
            .width(SHARE_CARD_WIDTH)
            .clip(RoundedCornerShape(Radius.xl))
            .background(colors.background),
    ) {
        if (hasRoute) {
            Box(modifier = Modifier.fillMaxWidth().height(SHARE_HERO_HEIGHT).background(colors.backgroundElement)) {
                RouteCanvas(points = points, color = color, height = SHARE_HERO_HEIGHT, fill = true)
            }
        } else {
            IconHero(gradient = ElanGradients.forKey(meta.colorKey), icon = MdiIcons.byName(meta.icon) ?: MdiIcons.Run)
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    ElanWordmark(height = 18.dp)
                    Text(meta.label, style = ElanType.headline, color = colors.text)
                    Text(formatDateTime(session.startedAt, withYear = true), style = ElanType.caption, color = colors.textSecondary)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.13f), RoundedCornerShape(Radius.sm)),
                ) {
                    Icon(painter = painterResource(MdiIcons.byName(meta.icon) ?: MdiIcons.Run), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 3) {
                stats.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.width((SHARE_CARD_WIDTH - 36.dp - 24.dp) / 3)) {
                        Text(s.value, style = ElanType.metricSm, color = s.color ?: colors.text, maxLines = 1)
                        Text(s.label, style = ElanType.caption, color = colors.textSecondary)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.13f), RoundedCornerShape(Radius.pill))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(painter = painterResource(badgeIcon), contentDescription = null, tint = badgeColor, modifier = Modifier.size(15.dp))
                Text(badgeLabel, color = badgeColor, style = ElanType.label.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** Héros sans tracé : dégradé d'activité (140 dp) + icône 56, encre sombre sur les dégradés clairs. */
@Composable
private fun IconHero(gradient: List<Color>, @DrawableRes icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Brush.linearGradient(gradient, start = Offset.Zero, end = Offset.Infinite)),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = ElanGradients.inkOn(gradient), modifier = Modifier.size(56.dp))
    }
}

/** Dégradé sillage par clé d'activité (`velo`, `muscu`, `course`, `marche`), marque sinon. */
fun ElanGradients.forKey(key: String): List<Color> = when (key) {
    "velo" -> velo
    "muscu" -> muscu
    "course" -> course
    "marche" -> marche
    "heart" -> heart
    "warning" -> fire
    "success" -> success
    "danger" -> danger
    else -> brand
}
