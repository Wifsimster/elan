package ovh.battistella.elan.ui.screens.settings.cards

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType

@Composable
fun CardText(text: String, modifier: Modifier = Modifier, muted: Boolean = false, size: Int = 13) {
    Text(
        text = text,
        style = when {
            size <= 12 -> ElanType.caption
            size >= 15 -> ElanType.body
            else -> ElanType.bodySm
        },
        color = if (muted) ElanTheme.colors.textMuted else ElanTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
fun CardError(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = ElanType.bodySm, color = ElanTheme.colors.danger, modifier = modifier)
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
) {
    val colors = ElanTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = ElanType.subtitle,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = color ?: colors.accent),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
fun StatusDot(color: Color, size: Int = 10) {
    Box(modifier = Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(ElanTheme.colors.hairline))
}

@Composable
fun SubTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ElanType.subtitle, color = ElanTheme.colors.text, modifier = modifier)
}

@Composable
fun ScannedDeviceRow(@DrawableRes icon: Int, color: Color, name: String, onClick: () -> Unit) {
    val colors = ElanTheme.colors
    Hairline()
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(haptic = HapticKind.Light, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(name, color = colors.text, style = ElanType.subtitle, modifier = Modifier.weight(1f))
        Icon(painter = painterResource(MdiIcons.ChevronRight), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun HairlineRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Hairline()
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        content = content,
    )
}
