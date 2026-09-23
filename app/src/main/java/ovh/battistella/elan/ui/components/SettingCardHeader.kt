package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType

/**
 * En-tête d'une carte de réglages : icône colorée 22 dp + titre de section.
 * Uniformise le motif répété dans l'écran Réglages pour garder une
 * présentation cohérente d'une carte à l'autre.
 */
@Composable
fun SettingCardHeader(
    @DrawableRes icon: Int,
    /** Couleur de l'icône (accent thématique de la section). */
    color: Color,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(title, style = ElanType.sectionTitle, color = ElanTheme.colors.text)
    }
}
