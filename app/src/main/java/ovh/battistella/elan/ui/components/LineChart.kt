package ovh.battistella.elan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import kotlin.math.roundToLong

data class ChartPoint(val x: Double, val y: Double)

private val PAD_L = 40.dp
private val PAD_R = 10.dp
private val PAD_T = 12.dp
private val PAD_B = 22.dp

internal fun lineChartSummary(
    data: List<ChartPoint>,
    avg: Double?,
    label: String?,
    formatY: (Double) -> String,
): String {
    val ys = data.map { it.y }
    val prefix = if (label != null) "$label : " else ""
    val base = "${prefix}minimum ${formatY(ys.min())}, maximum ${formatY(ys.max())}"
    return if (avg != null) "$base, moyenne ${formatY(avg)}" else base
}

private fun defaultFormat(v: Double): String = v.roundToLong().toString()

@Composable
fun LineChart(
    data: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    avg: Double? = null,
    formatY: (Double) -> String = ::defaultFormat,
    formatX: (Double) -> String = ::defaultFormat,
    label: String? = null,
) {
    if (data.size < 2) return

    val colors = ElanTheme.colors
    val hairline = colors.hairline
    val muted = colors.textMuted
    val measurer = rememberTextMeasurer()
    val summary = lineChartSummary(data, avg, label, formatY)

    val minX = data.minOf { it.x }
    val maxX = data.maxOf { it.x }
    var minY = data.minOf { it.y }
    var maxY = data.maxOf { it.y }
    if (minY == maxY) {
        minY -= 1
        maxY += 1
    }
    val headroom = (maxY - minY) * 0.08
    val dataMin = minY
    minY -= headroom
    if (dataMin >= 0.0) minY = maxOf(minY, 0.0)
    maxY += headroom

    val yTicks = listOf(maxY, (maxY + minY) / 2, minY)
    val xTickCount = 4
    val xTicks = List(xTickCount + 1) { i -> minX + (maxX - minX) * i / xTickCount }

    val tickStyle = ElanType.micro.copy(color = muted, fontFeatureSettings = "tnum")
    val avgStyle = ElanType.micro.copy(color = muted, fontWeight = FontWeight.Bold)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = summary },
    ) {
        val padL = PAD_L.toPx()
        val padR = PAD_R.toPx()
        val padT = PAD_T.toPx()
        val padB = PAD_B.toPx()
        val innerW = maxOf(1f, size.width - padL - padR)
        val innerH = size.height - padT - padB
        val baseY = padT + innerH
        val xRange = (maxX - minX).takeIf { it != 0.0 } ?: 1.0
        val yRange = (maxY - minY).takeIf { it != 0.0 } ?: 1.0

        fun sx(x: Double): Float = padL + ((x - minX) / xRange * innerW).toFloat()
        fun sy(y: Double): Float = padT + ((1 - (y - minY) / yRange) * innerH).toFloat()

        val line = Path().apply {
            data.forEachIndexed { i, p ->
                if (i == 0) moveTo(sx(p.x), sy(p.y)) else lineTo(sx(p.x), sy(p.y))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(sx(maxX), baseY)
            lineTo(sx(minX), baseY)
            close()
        }

        yTicks.forEach { t ->
            val y = sy(t)
            drawLine(hairline, Offset(padL, y), Offset(size.width - padR, y), strokeWidth = 1.dp.toPx())
        }

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                0f to color.copy(alpha = 0.35f),
                1f to color.copy(alpha = 0f),
                startY = padT,
                endY = baseY,
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        if (avg != null) {
            val y = sy(avg)
            drawLine(
                color = muted,
                start = Offset(padL, y),
                end = Offset(size.width - padR, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
            val layout = measurer.measure("moy.", avgStyle)
            drawText(layout, topLeft = Offset(size.width - padR - 2.dp.toPx() - layout.size.width, y - 14.dp.toPx()))
        }

        yTicks.forEach { t ->
            val layout = measurer.measure(formatY(t), tickStyle)
            drawText(layout, topLeft = Offset(padL - 6.dp.toPx() - layout.size.width, sy(t) - layout.size.height / 2f))
        }

        val xLabelY = size.height - 14.dp.toPx()
        xTicks.forEachIndexed { i, t ->
            val layout = measurer.measure(formatX(t), tickStyle)
            val x = when (i) {
                0 -> padL
                xTicks.lastIndex -> size.width - padR - layout.size.width
                else -> sx(t) - layout.size.width / 2f
            }
            drawText(layout, topLeft = Offset(x, xLabelY))
        }
    }
}
