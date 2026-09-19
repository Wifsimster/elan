package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseGradients
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.bestInk

/** Encre posée sur une puce sélectionnée de teinte [tint] : la plus lisible entre `OnBright` et blanc. */
fun chipInk(tint: Color): Color = bestInk(tint, listOf(PulseGradients.OnBright, Color.White))

/**
 * Pastille de filtre / suggestion PULSE, sélectionnable, avec appui ressort
 * (échelle 0,94). Sélectionnée, la puce peint son fond avec la teinte :
 * l'encre est choisie pour rester lisible dessus (du blanc sur le lime de la
 * marche ou le teal du vélo tombe sous 2:1).
 */
@Composable
fun PulseChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Teinte à l'état sélectionné (défaut : accent). */
    color: Color? = null,
) {
    val colors = ElanTheme.colors
    val tint = color ?: colors.accent
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier = modifier
            .semantics { this.selected = selected }
            .pressableScale(scaleTo = 0.94f, onClick = onClick)
            .background(if (selected) tint else colors.backgroundElement, shape)
            .border(1.dp, if (selected) tint else colors.border, shape)
            .clip(shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = if (selected) chipInk(tint) else colors.text,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            maxLines = 1,
        )
    }
}
