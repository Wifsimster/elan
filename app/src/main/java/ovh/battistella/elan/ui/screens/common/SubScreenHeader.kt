package ovh.battistella.elan.ui.screens.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType


/**
 * En-tête d'un écran empilé (hors onglets) : flèche retour, titre, actions à
 * droite — l'équivalent de l'en-tête natif de pile de l'app d'origine, rendu
 * dans le contenu puisque le scaffold racine n'a pas de barre supérieure.
 */
@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = ElanTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        HeaderAction(icon = MdiIcons.ArrowLeft, label = stringResource(R.string.common_back), onClick = onBack, size = 26.dp)
        Text(
            text = title,
            style = PulseType.headline,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** Icône d'action d'en-tête, pressable (zone tactile élargie), avec libellé d'accessibilité. */
@Composable
fun HeaderAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ElanTheme.colors.text,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = label,
        tint = tint,
        modifier = modifier
            .pressableScale(enabled = enabled, scaleTo = 0.88f, onClick = onClick)
            .padding(6.dp)
            .size(size),
    )
}
