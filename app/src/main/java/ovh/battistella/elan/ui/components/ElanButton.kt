package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.theme.ControlSize
import ovh.battistella.elan.ui.theme.ElanColors
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.Spacing
import ovh.battistella.elan.ui.theme.bestInk

enum class ButtonVariant { Primary, Secondary, Danger, Ghost }
enum class ButtonSize { Md, Lg }

/**
 * Aplat d'une action de teinte [color] : l'accent (ou rien) devient le Volt de
 * marque — l'accent « en trait » est trop sombre en clair pour un aplat —,
 * une teinte d'activité reste elle-même.
 */
fun fillFor(color: Color?, colors: ElanColors): Color =
    if (color == null || color == colors.accent || color == colors.link) colors.brand else color

/** Encre la plus lisible sur un aplat [fill] : l'encre de marque ou le blanc. */
fun inkOn(fill: Color, colors: ElanColors): Color = bestInk(fill, listOf(colors.onBrand, Color.White))

/**
 * Bouton Sillage, en pilule pleine largeur de doigt (52 / 60 dp) :
 * - [ButtonVariant.Primary] : aplat Volt (ou teinte d'activité), encre choisie
 *   pour le contraste — pas de dégradé ni d'ombre, la couleur suffit ;
 * - [ButtonVariant.Secondary] : aplat tonal (teinte à 14 %), texte teinté ;
 * - [ButtonVariant.Danger] : aplat `danger` ;
 * - [ButtonVariant.Ghost] : texte seul.
 * Appui « ressort » et haptique inclus (light pour primary, selection sinon).
 * En [loading], l'étiquette laisse place à un indicateur ; le bouton est alors
 * inactif comme s'il était désactivé.
 */
@Composable
fun ElanButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    /** Teinte de l'action (défaut : marque). */
    color: Color? = null,
    @DrawableRes icon: Int? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.pill)
    val active = enabled && !loading

    val fill: Color? = when (variant) {
        ButtonVariant.Primary -> fillFor(color, colors)
        ButtonVariant.Danger -> colors.danger
        ButtonVariant.Secondary -> (if (color == null || color == colors.accent) colors.accent else color).copy(alpha = 0.14f)
        ButtonVariant.Ghost -> null
    }
    val fg = when (variant) {
        ButtonVariant.Primary, ButtonVariant.Danger -> inkOn(fill!!, colors)
        ButtonVariant.Secondary -> if (color == null || color == colors.accent) colors.link else color
        ButtonVariant.Ghost -> colors.textSecondary
    }
    val lg = size == ButtonSize.Lg
    val iconSize = if (lg) 22.dp else 20.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .semantics(mergeDescendants = true) { }
            .pressableScale(
                enabled = active,
                haptic = if (variant == ButtonVariant.Primary) HapticKind.Light else HapticKind.Selection,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = if (lg) ControlSize.lg else ControlSize.md)
            .then(if (fill != null) Modifier.background(fill, shape) else Modifier)
            .clip(shape)
            .padding(horizontal = if (lg) Spacing.four else 20.dp, vertical = Spacing.gutter),
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
                style = if (lg) ElanType.buttonLg else ElanType.button,
                maxLines = 1,
            )
        }
    }
}
