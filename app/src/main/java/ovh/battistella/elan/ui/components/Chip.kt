package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanGradients
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.Spacing
import ovh.battistella.elan.ui.theme.bestInk

/** Encre posée sur une puce sélectionnée de teinte [tint] : la plus lisible entre `OnBright` et blanc. */
fun chipInk(tint: Color): Color = bestInk(tint, listOf(ElanGradients.OnBright, Color.White))

/**
 * Puce de filtre / suggestion Sillage, en pilule, sélectionnable, avec appui
 * ressort (échelle 0,94). Au repos : aplat tonal sans bordure. Sélectionnée :
 * aplat de la teinte (le Volt de marque par défaut) sous l'encre la plus
 * lisible.
 */
@Composable
fun ElanChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Teinte à l'état sélectionné (défaut : marque). */
    color: Color? = null,
) {
    val colors = ElanTheme.colors
    val tint = fillFor(color, colors)
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { this.selected = selected }
            .pressableScale(scaleTo = 0.94f, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .background(if (selected) tint else colors.backgroundSelected, shape)
            .clip(shape)
            .padding(horizontal = Spacing.three, vertical = Spacing.two),
    ) {
        Text(
            text = label,
            color = if (selected) chipInk(tint) else colors.text,
            style = ElanType.label.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
