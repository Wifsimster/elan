package ovh.battistella.elan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Spacing

private const val WORDMARK_RATIO = 1673f / 736f

@Composable
fun ElanWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    color: Color = ElanTheme.colors.text,
    accent: Color = ElanTheme.colors.accent,
) {
    val label = stringResource(R.string.app_name)
    Box(
        modifier = modifier
            .size(width = height * WORDMARK_RATIO, height = height)
            .semantics { contentDescription = label },
    ) {
        Icon(painterResource(R.drawable.brand_wordmark), contentDescription = null, tint = color, modifier = Modifier.matchParentSize())
        Icon(painterResource(R.drawable.brand_wordmark_accent), contentDescription = null, tint = accent, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun ElanTick(
    modifier: Modifier = Modifier,
    color: Color = ElanTheme.colors.accent,
    height: Dp = 9.dp,
) {
    Canvas(modifier = modifier.size(width = height * (227f / 132f), height = height)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.392f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.023f)
            lineTo(w * 0.432f, h)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ElanTheme.colors.textSecondary,
    tick: Color = ElanTheme.colors.accent,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(20.dp)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        ElanTick(color = tick)
        Text(text.uppercase(), style = ElanType.overline, color = color, maxLines = 1)
    }
}
