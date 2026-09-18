package ovh.battistella.elan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseGradients
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.max

/** Une barre : libellé sous l'axe et valeur (≥ 0). */
data class BarPoint(val label: String, val value: Double)

/**
 * Histogramme PULSE : une colonne par point, valeur au-dessus des barres non
 * nulles (ou espaceur de 14 dp pour garder l'alignement), rail de fond pleine
 * hauteur en `hairline` (chaque jour garde une présence visuelle — sans rail,
 * une semaine creuse paraît « cassée »), barre en dégradé vertical sur 64 % de
 * la largeur de colonne, hauteur mini 4 dp, libellé 11/600.
 */
@Composable
fun BarChart(
    data: List<BarPoint>,
    modifier: Modifier = Modifier,
    gradient: List<Color> = PulseGradients.accent,
    height: Dp = 120.dp,
    /** Formate la valeur affichée au-dessus de chaque barre non nulle. */
    formatValue: ((Double) -> String)? = null,
) {
    val colors = ElanTheme.colors
    val maxValue = max(1.0, data.maxOfOrNull { it.value } ?: 0.0)
    val railColor = colors.hairline
    val brush = Brush.verticalGradient(gradient)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth(),
    ) {
        data.forEach { point ->
            val hasValue = point.value > 0
            val ratio = (point.value / maxValue).toFloat()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (hasValue && formatValue != null) {
                    Text(
                        text = formatValue(point.value),
                        color = colors.textSecondary,
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.64f)
                        .height(height),
                ) {
                    val radius = CornerRadius(Radius.sm.toPx())
                    drawRoundRect(color = railColor, cornerRadius = radius)
                    if (hasValue) {
                        val barHeight = max(4.dp.toPx(), ratio * size.height)
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(size.width, barHeight),
                            cornerRadius = radius,
                        )
                    }
                }
                Text(
                    text = point.label,
                    color = colors.textSecondary,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
            }
        }
    }
}
