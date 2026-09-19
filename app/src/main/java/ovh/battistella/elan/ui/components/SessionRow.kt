package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.domain.formatDistance
import ovh.battistella.elan.domain.formatDurationShort
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.forKey

/** « 3 séries », « 1 exercice » : pluriel français à partir de 2. */
internal fun pluralFr(count: Int, singular: String): String =
    "$count $singular${if (count > 1) "s" else ""}"

/**
 * Ligne d'historique d'une séance : pastille d'activité teintée 46 dp, libellé
 * + date, et à droite la métrique clé. En muscu la durée n'a pas de sens : on
 * met en avant la complétion (séries faites, puis exercices) ; les activités
 * GPS gardent temps en mouvement + distance ; sinon la FC moyenne.
 */
@Composable
fun SessionRow(
    session: Session,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val meta = session.type.meta
    val color = colors.forKey(meta.colorKey)
    val shape = RoundedCornerShape(Radius.lg)
    val secondaryStyle = TextStyle(fontSize = 13.sp)

    val primary: String
    val secondary: String?
    if (session.type == ActivityType.MUSCU) {
        primary = pluralFr(session.setCount ?: 0, "série")
        secondary = pluralFr(session.exerciseCount ?: 0, "exercice")
    } else {
        primary = formatDurationShort(session.movingTimeSec ?: session.durationSec)
        secondary = when {
            isGpsActivity(session.type) && session.distanceM != null -> formatDistance(session.distanceM)
            session.avgHr != null -> "${Math.round(session.avgHr)} bpm moy."
            else -> null
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pressableScale(onClick = onClick)
            .shadow(Elevation.sm, shape)
            .background(colors.backgroundElement, shape)
            .clip(shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .background(color.copy(alpha = 0.13f), RoundedCornerShape(Radius.sm)),
        ) {
            Icon(
                painter = painterResource(MdiIcons.byName(meta.icon) ?: MdiIcons.Run),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(meta.label, style = PulseType.subtitle, color = colors.text)
            Text(formatDateTime(session.startedAt), style = secondaryStyle, color = colors.textSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
            Text(
                text = primary,
                style = TextStyle(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                color = colors.text,
            )
            if (secondary != null) {
                Text(secondary, style = secondaryStyle, color = colors.textSecondary)
            }
        }
        Icon(
            painter = painterResource(MdiIcons.ChevronRight),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}
