package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius

/**
 * Pastille de fréquence cardiaque. Connectée : bpm (chiffres condensés) sur
 * aplat tonal heart ; sinon « Connecter » et [onClick] mène aux réglages (la pastille
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
        else -> stringResource(R.string.hr_badge_connect)
    }
    val a11y = when {
        connected && bpm != null -> stringResource(R.string.hr_badge_a11y_bpm, bpm)
        connected -> stringResource(R.string.hr_badge_a11y_connected)
        connecting -> stringResource(R.string.hr_badge_a11y_connecting)
        else -> stringResource(R.string.hr_badge_a11y_connect)
    }
    val tint = if (connected) colors.heart else colors.textSecondary

    val surface = if (connected) {
        // Aplat cardiaque tonal quand la ceinture émet.
        Modifier.background(colors.heart.copy(alpha = 0.16f), shape)
    } else {
        Modifier.background(colors.backgroundSelected, shape)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .then(if (connected) Modifier else Modifier.pressableScale(onClick = onClick))
            .then(surface)
            .defaultMinSize(minHeight = 40.dp)
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
            style = if (connected) ElanType.metricSm.copy(fontSize = 18.sp, lineHeight = 20.sp) else ElanType.label.copy(fontWeight = FontWeight.Bold),
        )
        if (connected && bpm != null) {
            Text(
                text = stringResource(R.string.hr_badge_unit),
                color = colors.textSecondary,
                style = ElanType.caption,
            )
        }
    }
}
