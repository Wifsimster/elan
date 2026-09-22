package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.Spacing

/**
 * Encart d'erreur intégré à une carte (fond teinté, icône, texte lisible) —
 * pour les échecs qui demandent une action de l'utilisateur (identifiants S3
 * refusés, serveur injoignable…), à la place d'une ligne rouge brute. Annoncé
 * immédiatement par les lecteurs d'écran (région live assertive = rôle alert).
 */
@Composable
fun ErrorNotice(message: String, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
            .background(colors.danger.copy(alpha = 0.10f), shape)
            .border(1.dp, colors.danger.copy(alpha = 0.33f), shape)
            .padding(Spacing.gutter),
    ) {
        Icon(
            painter = painterResource(MdiIcons.AlertCircleOutline),
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            color = colors.text,
            style = ElanType.bodySm,
            modifier = Modifier.weight(1f),
        )
    }
}
