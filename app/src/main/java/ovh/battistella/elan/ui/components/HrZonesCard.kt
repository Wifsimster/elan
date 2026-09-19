package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.domain.ZoneDistribution
import ovh.battistella.elan.domain.ZoneSlice
import ovh.battistella.elan.domain.dominantZone
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.domain.formatDurationShort
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.roundToInt

/** Fourchette de bpm d'une zone, en texte court (« < 114 », « 133–151 », « 171+ »). */
internal fun rangeLabel(slice: ZoneSlice): String = when {
    slice.maxBpm == null -> "${slice.minBpm}+"
    slice.minBpm <= 0 -> "< ${slice.maxBpm + 1}"
    else -> "${slice.minBpm}–${slice.maxBpm}"
}

/** Légende de clôture : « Surtout en zone N (label) — X % du temps avec la ceinture. » */
internal fun dominantCaption(distribution: ZoneDistribution): String {
    val dominant = dominantZone(distribution)
    val pct = (dominant.ratio * 100).roundToInt()
    return "Surtout en zone ${dominant.zone} (${dominant.label.lowercase()}) — $pct % du temps avec la ceinture."
}

/**
 * Temps passé dans chaque zone cardiaque : barre empilée 14 dp + détail par
 * zone. Les zones étant ordonnées, la barre suit une rampe d'une seule teinte
 * (`HrZoneColors`) et non cinq couleurs catégorielles ; deux dp de fond
 * séparent les segments, et chaque zone est nommée en toutes lettres pour que
 * l'information ne repose jamais sur la couleur seule.
 */
@Composable
fun HrZonesCard(distribution: ZoneDistribution, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    val ramp = ElanTheme.hrZones
    val filled = distribution.slices.filter { it.ratio > 0 }
    val barDescription = filled.joinToString(", ") { "${it.label} ${(it.ratio * 100).roundToInt()} %" }

    PulseCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Zones cardiaques", style = PulseType.headline, color = colors.text)
            Text(formatDurationShort(distribution.totalSec), style = PulseType.caption, color = colors.textMuted)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .semantics { contentDescription = barDescription },
        ) {
            filled.forEach { slice ->
                Box(
                    modifier = Modifier
                        // Poids = part du temps ; un passage très court reste visible.
                        .weight(slice.ratio.toFloat())
                        .widthIn(min = 3.dp)
                        .height(14.dp)
                        .background(ramp[slice.zone - 1], RoundedCornerShape(Radius.pill)),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            distribution.slices.forEach { slice ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.size(10.dp).background(ramp[slice.zone - 1], CircleShape))
                    Text(
                        text = slice.label,
                        style = PulseType.body,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(rangeLabel(slice), style = PulseType.caption, color = colors.textMuted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDuration(slice.seconds),
                        style = PulseType.label.copy(fontFeatureSettings = "tnum"),
                        color = colors.text,
                    )
                    Text(
                        text = "${(slice.ratio * 100).roundToInt()} %",
                        style = PulseType.caption.copy(fontFeatureSettings = "tnum"),
                        color = colors.textSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }

        Text(dominantCaption(distribution), style = PulseType.caption, color = colors.textSecondary)
    }
}
