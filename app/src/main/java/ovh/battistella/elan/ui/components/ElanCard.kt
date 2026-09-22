package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.Radius

/**
 * - [Elevated] (défaut) : surface flottante avec ombre douce, sans bordure.
 * - [Inset] : creux discret (champs, sous-blocs), bordure fine sans ombre.
 * - [Plain] : surface simple sans ombre ni bordure.
 */
enum class CardVariant { Elevated, Inset, Plain }

/**
 * Surface de contenu Sillage : coins `Radius.lg`, padding 16, contenu en
 * colonne espacée de 12. La profondeur vient de l'ombre, pas de la bordure.
 */
@Composable
fun ElanCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Elevated,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.lg)
    val surface = when (variant) {
        CardVariant.Elevated ->
            Modifier.shadow(Elevation.sm, shape).background(colors.backgroundElement, shape)
        CardVariant.Inset ->
            Modifier.background(colors.background, shape).border(1.dp, colors.border, shape)
        CardVariant.Plain -> Modifier.background(colors.backgroundElement, shape)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .then(surface)
            .clip(shape)
            .padding(16.dp),
        content = content,
    )
}
