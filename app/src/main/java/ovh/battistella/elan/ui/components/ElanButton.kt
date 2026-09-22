package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.ElanColors
import ovh.battistella.elan.ui.theme.ElanGradients
import ovh.battistella.elan.ui.theme.Radius

enum class ButtonVariant { Primary, Secondary, Danger, Ghost }
enum class ButtonSize { Md, Lg }

/**
 * Déduit le dégradé correspondant à une couleur d'activité du thème (même
 * table que `gradientFor` dans l'app d'origine) : velo / muscu / course /
 * marche / heart, warning → fire, sinon accent.
 */
fun gradientFor(color: Color?, colors: ElanColors): List<Color> = when (color) {
    colors.velo -> ElanGradients.velo
    colors.muscu -> ElanGradients.muscu
    colors.course -> ElanGradients.course
    colors.marche -> ElanGradients.marche
    colors.heart -> ElanGradients.heart
    colors.warning -> ElanGradients.fire
    else -> ElanGradients.accent
}

/**
 * Bouton Sillage : remplissage en dégradé + ombre teintée pour l'action
 * principale (et la variante danger), contour 1,5 dp pour le secondaire,
 * plat pour le ghost. Appui « ressort » et haptique inclus (light pour
 * primary, selection sinon). En [loading], l'étiquette laisse place à un
 * indicateur ; le bouton est alors inactif comme s'il était désactivé.
 */
@Composable
fun ElanButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    /** Teinte de l'action (défaut : accent). Choisit aussi le dégradé primary. */
    color: Color? = null,
    /** Dégradé explicite pour la variante primary ; sinon déduit de [color]. */
    gradient: List<Color>? = null,
    @DrawableRes icon: Int? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = ElanTheme.colors
    val accent = color ?: colors.accent
    val grad = gradient
        ?: if (variant == ButtonVariant.Danger) ElanGradients.danger else gradientFor(accent, colors)
    val filled = variant == ButtonVariant.Primary || variant == ButtonVariant.Danger

    val fg = when {
        filled -> ElanGradients.inkOn(grad)
        variant == ButtonVariant.Ghost -> colors.textSecondary
        else -> accent
    }
    val padV = if (size == ButtonSize.Lg) 17.dp else 14.dp
    val fontSize = if (size == ButtonSize.Lg) 17.sp else 16.sp
    val iconSize = if (size == ButtonSize.Lg) 22.dp else 20.dp
    val shape = RoundedCornerShape(Radius.lg)
    val active = enabled && !loading

    // Action pleine : dégradé + ombre teintée à la couleur de l'action.
    val surface = when {
        filled -> {
            val shadowColor = if (variant == ButtonVariant.Danger) colors.danger else accent
            Modifier
                .shadow(Elevation.md, shape, ambientColor = shadowColor, spotColor = shadowColor)
                .background(Brush.linearGradient(grad, start = Offset.Zero, end = Offset.Infinite), shape)
        }
        variant == ButtonVariant.Secondary -> Modifier.border(1.5.dp, accent, shape)
        else -> Modifier
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .semantics(mergeDescendants = true) { }
            // L'échelle d'appui enveloppe toute la surface (ombre comprise).
            .pressableScale(
                enabled = active,
                haptic = if (variant == ButtonVariant.Primary) HapticKind.Light else HapticKind.Selection,
                onClick = onClick,
            )
            .then(surface)
            .clip(shape)
            .padding(horizontal = 20.dp, vertical = padV),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = fg,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(iconSize),
            )
        } else {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = title,
                color = fg,
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp,
                ),
                maxLines = 1,
            )
        }
    }
}
