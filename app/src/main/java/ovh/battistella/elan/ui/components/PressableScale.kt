package ovh.battistella.elan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.haptics.rememberHaptics
import ovh.battistella.elan.ui.theme.LocalReducedMotion
import ovh.battistella.elan.ui.theme.Motion

/**
 * Surface tactile « ressort », brique de base de toutes les interactions
 * PULSE : se comprime sous le doigt (`Motion.snappy` vers [scaleTo]) et
 * rebondit au relâchement (`Motion.bouncy` vers 1), avec un retour haptique
 * [haptic] au clic (`null` pour aucun). Rôle bouton pour l'accessibilité.
 *
 * Respecte « Réduire les animations » ([LocalReducedMotion]) : l'haptique est
 * conservée, l'échelle supprimée.
 */
@Composable
fun Modifier.pressableScale(
    enabled: Boolean = true,
    scaleTo: Float = Motion.pressScale,
    haptic: HapticKind? = HapticKind.Selection,
    onClick: () -> Unit,
): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed, reducedMotion, scaleTo) {
        if (reducedMotion) {
            scale.snapTo(1f)
        } else if (pressed) {
            scale.animateTo(scaleTo, Motion.snappy)
        } else {
            scale.animateTo(1f, Motion.bouncy)
        }
    }

    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
        ) {
            if (haptic != null) haptics(haptic)
            onClick()
        }
}
