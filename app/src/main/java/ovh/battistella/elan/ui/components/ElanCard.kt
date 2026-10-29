package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.Spacing

enum class CardVariant { Elevated, Inset, Plain }

@Composable
fun ElanCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Elevated,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.lg)
    val surface = when (variant) {
        CardVariant.Elevated, CardVariant.Plain -> Modifier.background(colors.backgroundElement, shape)
        CardVariant.Inset ->
            Modifier.background(colors.background, shape).border(1.dp, colors.border, shape)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
        modifier = modifier
            .then(surface)
            .clip(shape)
            .padding(horizontal = Spacing.three, vertical = 18.dp),
        content = content,
    )
}
