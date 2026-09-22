package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType

/**
 * Incrémenteur numérique d'un réglage (poids, taille, FC max, heure de rappel,
 * circonférence de pneu…), borné par [min]/[max]. Partagé par les cartes de
 * l'écran Réglages. Haptique « selection » à chaque pas.
 */
@Composable
fun SettingStepper(
    label: String,
    value: Int,
    unit: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
) {
    val colors = ElanTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StepperButton(
                icon = MdiIcons.MinusCircleOutline,
                description = stringResource(R.string.stepper_decrease_a11y, label),
                enabled = value > min,
                onClick = { onChange((value - step).coerceAtLeast(min)) },
            )
            Text(
                text = "$value $unit",
                style = ElanType.metric.copy(fontSize = 17.sp, letterSpacing = 0.sp),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 70.dp)
                    .semantics { contentDescription = "$label : $value $unit" },
            )
            StepperButton(
                icon = MdiIcons.PlusCircleOutline,
                description = stringResource(R.string.stepper_increase_a11y, label),
                enabled = value < max,
                onClick = { onChange((value + step).coerceAtMost(max)) },
            )
        }
    }
}

@Composable
private fun StepperButton(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = ElanTheme.colors.accent,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            // Zone tactile élargie (hitSlop 12 dans l'app d'origine).
            .pressableScale(enabled = enabled, scaleTo = 0.88f, onClick = onClick)
            .padding(6.dp)
            .size(28.dp),
    )
}
