package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius

/** Action proposée pour sortir de l'état vide (ex. « Importer depuis Strava »). */
data class EmptyAction(
    val label: String,
    @DrawableRes val icon: Int? = null,
    val onClick: () -> Unit,
)

/**
 * État vide Sillage : pastille d'icône teintée 64 dp (comme les autres surfaces
 * de repos), titre/sous-titre sur l'échelle typographique, et une action
 * secondaire facultative pour ne jamais laisser l'utilisateur dans une impasse.
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Teinte de la pastille d'icône (défaut : accent). */
    tint: Color? = null,
    action: EmptyAction? = null,
) {
    val colors = ElanTheme.colors
    val color = tint ?: colors.accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp, horizontal = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 2.dp)
                .size(64.dp)
                .background(color.copy(alpha = 0.13f), RoundedCornerShape(Radius.lg)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(title, style = ElanType.headline, color = colors.text, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = ElanType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            ElanButton(
                title = action.label,
                icon = action.icon,
                variant = ButtonVariant.Secondary,
                color = color,
                onClick = action.onClick,
            )
        }
    }
}
