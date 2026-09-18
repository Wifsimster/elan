package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Radius

/**
 * Pastille de fréquence cardiaque. Connectée : bpm sur fond teinté heart avec
 * halo ; sinon « Connecter » et [onClick] mène aux réglages (la pastille
 * connectée n'est pas cliquable, comme dans l'app d'origine).
 */
@Composable
fun HrBadge(
    bpm: Int?,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ceinture en cours de connexion (affiche « ... »). */
    connecting: Boolean = false,
) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.pill)
    val label = when {
        connected -> bpm?.toString() ?: "··"
        connecting -> "..."
        else -> "Connecter"
    }
    val a11y = when {
        connected && bpm != null -> "Fréquence cardiaque $bpm battements par minute"
        connected -> "Ceinture cardiaque connectée"
        connecting -> "Connexion de la ceinture cardiaque…"
        else -> "Connecter une ceinture cardiaque"
    }
    val tint = if (connected) colors.heart else colors.textSecondary

    val surface = if (connected) {
        // Halo cardiaque quand la ceinture émet.
        Modifier
            .shadow(6.dp, shape, ambientColor = colors.heart, spotColor = colors.heart)
            .background(colors.heart.copy(alpha = 0.12f), shape)
            .border(1.dp, colors.heart.copy(alpha = 0.40f), shape)
    } else {
        Modifier
            .background(colors.backgroundElement, shape)
            .border(1.dp, colors.border, shape)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .then(if (connected) Modifier else Modifier.pressableScale(onClick = onClick))
            .then(surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(if (connected) MdiIcons.HeartPulse else MdiIcons.HeartOffOutline),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = tint,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
        )
        if (connected && bpm != null) {
            Text(
                text = "bpm",
                color = colors.textSecondary,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
