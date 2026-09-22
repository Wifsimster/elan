package ovh.battistella.elan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Schéma Material 3 dérivé des jetons Sillage, pour que les composants Material
 * (boutons, switches, barre de navigation, snackbar) parlent la même langue
 * que les composants maison. Pas de couleur dynamique (Material You) : la
 * marque est fixe.
 */
private fun colorSchemeFrom(c: ElanColors, dark: Boolean): ColorScheme {
    val onAccent = Color.White
    return if (dark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.text,
            secondary = c.link,
            onSecondary = onAccent,
            secondaryContainer = c.backgroundSelected,
            onSecondaryContainer = c.text,
            tertiary = c.success,
            onTertiary = c.background,
            background = c.background,
            onBackground = c.text,
            surface = c.backgroundElement,
            onSurface = c.text,
            surfaceVariant = c.backgroundSelected,
            onSurfaceVariant = c.textSecondary,
            surfaceContainerLowest = c.background,
            surfaceContainerLow = c.backgroundElement,
            surfaceContainer = c.backgroundElement,
            surfaceContainerHigh = c.surfaceHigh,
            surfaceContainerHighest = c.backgroundSelected,
            outline = c.border,
            outlineVariant = c.hairline,
            error = c.danger,
            onError = Color.White,
            scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.text,
            secondary = c.link,
            onSecondary = onAccent,
            secondaryContainer = c.backgroundSelected,
            onSecondaryContainer = c.text,
            tertiary = c.success,
            onTertiary = Color.White,
            background = c.background,
            onBackground = c.text,
            surface = c.backgroundElement,
            onSurface = c.text,
            surfaceVariant = c.backgroundSelected,
            onSurfaceVariant = c.textSecondary,
            surfaceContainerLowest = c.backgroundElement,
            surfaceContainerLow = c.background,
            surfaceContainer = c.background,
            surfaceContainerHigh = c.backgroundSelected,
            surfaceContainerHighest = c.backgroundSelected,
            outline = c.border,
            outlineVariant = c.hairline,
            error = c.danger,
            onError = Color.White,
            scrim = c.scrim,
        )
    }
}

/**
 * Point d'entrée du thème d'Élan. Enveloppe [MaterialExpressiveTheme] — qui
 * fournit le [MotionScheme] à ressorts de Material 3 Expressive et les
 * défauts des composants — et fournit la couche de jetons Sillage
 * ([ElanColors], zones FC) par-dessus.
 */
@Composable
fun ElanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val elanColors = if (darkTheme) ElanColors.Dark else ElanColors.Light
    val hrZones = if (darkTheme) HrZoneColors.Dark else HrZoneColors.Light
    // « Animations réduites » (échelle d'animation système à 0) : plus de rebond
    // ni de halo pulsant (pressableScale, RouteCanvas) et ressorts Material
    // standard, sans dépassement.
    val reducedMotion = rememberSystemReducedMotion()

    CompositionLocalProvider(
        LocalElanColors provides elanColors,
        LocalHrZoneColors provides hrZones,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeFrom(elanColors, darkTheme),
            motionScheme = if (reducedMotion) MotionScheme.standard() else MotionScheme.expressive(),
            content = content,
        )
    }
}

/**
 * Accès aux jetons Sillage, à la manière dont [MaterialTheme] expose ses
 * sous-systèmes (`ElanTheme.colors.accent`). Une fonction et un objet peuvent
 * partager un nom en Kotlin : `ElanTheme { }` et `ElanTheme.colors` coexistent.
 */
object ElanTheme {
    val colors: ElanColors
        @Composable @ReadOnlyComposable get() = LocalElanColors.current
    val hrZones: List<Color>
        @Composable @ReadOnlyComposable get() = LocalHrZoneColors.current
}
