package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius

/** Ton d'une tendance : [Positive] = vert, [Negative] = atténué, [Neutral] = stable. */
enum class Tone { Positive, Negative, Neutral }

/** Évolution affichée sous la valeur (ex. comparaison à la période précédente). */
data class Trend(val label: String, val tone: Tone)

/**
 * Tuile de métrique « bento » : pastille d'icône teintée + grand chiffre
 * tabulaire. Conçue pour s'aligner en grille fluide dans une [PulseCard] :
 * largeur mini 96 dp (pleine largeur en [hero]), et se laisse pondérer par
 * `Modifier.weight(1f)` dans une Row.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    @DrawableRes icon: Int? = null,
    color: Color? = null,
    /** Affichage compact (police plus petite). */
    compact: Boolean = false,
    /** Métrique mise en avant (chiffre plus grand) — valeur clé à lire en effort. */
    hero: Boolean = false,
    /** Petite ligne d'évolution sous la valeur. */
    trend: Trend? = null,
) {
    val colors = ElanTheme.colors
    val tint = color ?: colors.text
    val iconTint = color ?: colors.textSecondary
    val valueSize = when {
        hero -> 44.sp
        compact -> 24.sp
        else -> 32.sp
    }

    // Libellé, valeur, unité et tendance forment un seul nœud pour TalkBack
    // (« Distance : 12,4 km, +8 % vs semaine passée »).
    val description = listOfNotNull(
        stringResource(R.string.stat_tile_a11y, label, listOfNotNull(value, unit).joinToString(" ")),
        trend?.label,
    ).joinToString(", ")
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .then(if (hero) Modifier.fillMaxWidth() else Modifier.widthIn(min = 96.dp)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconTint.copy(alpha = 0.14f), RoundedCornerShape(Radius.sm)),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = label,
                style = PulseType.label,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = value,
                style = PulseType.metric.copy(fontSize = valueSize),
                color = tint,
                maxLines = 1,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    color = colors.textSecondary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (trend != null) {
            val trendColor = when (trend.tone) {
                Tone.Positive -> colors.success
                Tone.Negative -> colors.textSecondary
                Tone.Neutral -> colors.textMuted
            }
            val trendIcon = when (trend.tone) {
                Tone.Positive -> MdiIcons.ArrowUp
                Tone.Negative -> MdiIcons.ArrowDown
                Tone.Neutral -> MdiIcons.Minus
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(trendIcon),
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(12.dp),
                )
                Text(trend.label, style = PulseType.caption, color = trendColor)
            }
        }
    }
}
