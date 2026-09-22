package ovh.battistella.elan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Schéma Material 3 dérivé des jetons Sillage, pour que les composants Material
 * (dialogues, switches, champs, snackbar) parlent la même langue que les
 * composants maison. Pas de couleur dynamique (Material You) : la marque est
 * fixe. `primary` est la marque « en trait » ([ElanColors.accent]) : Material
 * s'en sert aussi comme couleur de texte (`TextButton`, champ focalisé), que
 * le Volt pur ne tiendrait pas sur le papier. Les aplats Volt passent par les
 * composants maison ([ElanColors.brand]).
 */
private fun colorSchemeFrom(c: ElanColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = if (dark) c.onBrand else Color.White,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.text,
        inversePrimary = c.accent,
        secondary = c.link,
        onSecondary = if (dark) c.onBrand else Color.White,
        secondaryContainer = c.backgroundSelected,
        onSecondaryContainer = c.text,
        tertiary = c.success,
        onTertiary = if (dark) c.background else Color.White,
        background = c.background,
        onBackground = c.text,
        surface = c.backgroundElement,
        onSurface = c.text,
        surfaceVariant = c.backgroundSelected,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = Color.Transparent,
        surfaceBright = c.surfaceHigh,
        surfaceDim = c.background,
        surfaceContainerLowest = if (dark) c.background else c.backgroundElement,
        surfaceContainerLow = if (dark) c.backgroundElement else c.background,
        surfaceContainer = if (dark) c.backgroundElement else c.background,
        surfaceContainerHigh = if (dark) c.surfaceHigh else c.backgroundSelected,
        surfaceContainerHighest = c.backgroundSelected,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
        outline = c.border,
        outlineVariant = c.hairline,
        error = c.danger,
        onError = Color.White,
        scrim = c.scrim,
    )
}

/**
 * Typographie Material recomposée en Archivo : les composants Material non
 * stylés (dialogues, champs, snackbar, `Text` sans style) héritent de la
 * police de marque plutôt que de la police système.
 */
private val ElanMaterialTypography: Typography = Typography().run {
    fun TextStyle.sans() = copy(fontFamily = ElanFonts.sans)
    fun TextStyle.condensed() = copy(fontFamily = ElanFonts.condensed, fontWeight = FontWeight.ExtraBold)
    copy(
        displayLarge = displayLarge.condensed(),
        displayMedium = displayMedium.condensed(),
        displaySmall = displaySmall.condensed(),
        headlineLarge = headlineLarge.condensed(),
        headlineMedium = headlineMedium.condensed(),
        headlineSmall = headlineSmall.sans().copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.sans().copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.sans(),
        bodyMedium = bodyMedium.sans(),
        bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans().copy(fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.sans().copy(fontWeight = FontWeight.SemiBold),
    )
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
            typography = ElanMaterialTypography,
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
