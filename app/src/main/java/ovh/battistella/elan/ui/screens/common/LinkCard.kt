package ovh.battistella.elan.ui.screens.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius

/**
 * Pastille d'icône carrée teintée (`color + '22'` dans l'app d'origine) :
 * motif partagé par les cartes de lien, la séance du jour et l'en-tête de
 * détail.
 */
@Composable
fun TintedIconBox(
    @DrawableRes icon: Int,
    color: Color,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = Radius.sm,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(radius)),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

/**
 * Carte-lien : pastille d'icône, titre + sous-titre et chevron, pressable en
 * entier (vers le catalogue, la progression…).
 */
@Composable
fun LinkCard(
    @DrawableRes icon: Int,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconBoxSize: Dp = 46.dp,
) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pressableScale(onClick = onClick)
            .background(colors.backgroundElement, shape)
            .clip(shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        TintedIconBox(icon = icon, color = color, size = iconBoxSize, iconSize = if (iconBoxSize >= 46.dp) 24.dp else 22.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = ElanType.subtitle, color = colors.text)
            Text(subtitle, style = ElanType.bodySm, color = colors.textSecondary)
        }
        Icon(
            painter = painterResource(MdiIcons.ChevronRight),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}
