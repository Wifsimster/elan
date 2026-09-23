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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanFonts
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.max
import kotlin.math.roundToLong

/** Une barre : libellé sous l'axe et valeur (≥ 0). */
data class BarPoint(val label: String, val value: Double)

/** Résumé lu par les lecteurs d'écran : « libellé valeur » par barre (pur, pour les tests). */
internal fun barChartSummary(data: List<BarPoint>, formatValue: (Double) -> String): String =
    data.joinToString(", ") { "${it.label} ${formatValue(it.value)}" }

/**
 * Histogramme Sillage : une colonne par point, valeur au-dessus des barres non
 * nulles (ou espaceur de 14 dp pour garder l'alignement), rail de fond pleine
 * hauteur en `hairline` (chaque jour garde une présence visuelle — sans rail,
 * une semaine creuse paraît « cassée »), barre « sillage » (la teinte pleine
 * en haut, qui s'estompe vers la base) sur 56 % de la largeur de colonne,
 * hauteur mini 4 dp. La dernière barre — la plus récente, « aujourd'hui » —
 * reste pleine et son libellé passe à l'encre principale.
 */
@Composable
fun BarChart(
    data: List<BarPoint>,
    modifier: Modifier = Modifier,
    /** Teinte des barres (défaut : la marque en trait, `accent`). */
    color: Color? = null,
    height: Dp = 120.dp,
    /** Formate la valeur affichée au-dessus de chaque barre non nulle. */
    formatValue: ((Double) -> String)? = null,
) {
    val colors = ElanTheme.colors
    val maxValue = max(1.0, data.maxOfOrNull { it.value } ?: 0.0)
    val railColor = colors.hairline
    val tint = color ?: colors.accent
    val trail = Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.45f)))
    val lastIndex = data.lastIndex
    val summary = if (data.isEmpty()) {
        stringResource(R.string.bar_chart_a11y_empty)
    } else {
        stringResource(R.string.bar_chart_a11y, barChartSummary(data, formatValue ?: { it.roundToLong().toString() }))
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        data.forEachIndexed { index, point ->
            val latest = index == lastIndex
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
                        color = if (latest) colors.text else colors.textSecondary,
                        style = ElanType.micro.copy(fontFamily = ElanFonts.condensed, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(height),
                ) {
                    val radius = CornerRadius(Radius.xs.toPx())
                    drawRoundRect(color = railColor, cornerRadius = radius)
                    if (hasValue) {
                        val barHeight = max(4.dp.toPx(), ratio * size.height)
                        drawRoundRect(
                            brush = if (latest) SolidColor(tint) else trail,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(size.width, barHeight),
                            cornerRadius = radius,
                        )
                    }
                }
                Text(
                    text = point.label,
                    color = if (latest) colors.text else colors.textMuted,
                    style = if (latest) ElanType.micro.copy(fontWeight = FontWeight.ExtraBold) else ElanType.micro,
                    maxLines = 1,
                )
            }
        }
    }
}
