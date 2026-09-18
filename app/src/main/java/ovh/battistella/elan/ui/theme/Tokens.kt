package ovh.battistella.elan.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Jetons du design system PULSE (docs/port-spec/02-interface.md §5) — les
 * valeurs nommées dont toute l'interface est construite.
 *
 * Material 3 (via [ElanTheme]) garde la main sur le schéma de couleurs de
 * base, la typographie Material et le mouvement ; ces jetons posent la marque
 * par-dessus : une palette sémantique clair / sombre, des dégradés par sport,
 * les couleurs de zones cardiaques, un rythme d'espacement et un vocabulaire
 * de formes partagés, pour qu'un changement ici se propage à chaque écran.
 */

/** Couleurs sémantiques PULSE ; une instance claire et une sombre. */
@Immutable
data class PulseColors(
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val background: Color,
    /** Fond des cartes et éléments posés sur [background]. */
    val backgroundElement: Color,
    /** Surface encore plus haute (feuilles, menus). */
    val surfaceHigh: Color,
    val backgroundSelected: Color,
    val border: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val link: Color,
    val scrim: Color,
    // Couleurs par sport.
    val velo: Color,
    val muscu: Color,
    val course: Color,
    val marche: Color,
    /** Données de fréquence cardiaque uniquement. */
    val heart: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
) {
    companion object {
        val Light = PulseColors(
            text = Color(0xFF0B0E13),
            textSecondary = Color(0xFF5A6472),
            textMuted = Color(0xFF6B7280),
            background = Color(0xFFF3F5FA),
            backgroundElement = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFFFFFFFF),
            backgroundSelected = Color(0xFFE9ECF4),
            border = Color(0xFFE4E8F0),
            hairline = Color(0xFFEDF0F6),
            accent = Color(0xFF3B5BFF),
            accentSoft = Color(0xFFE5EAFF),
            link = Color(0xFF2F6BFF),
            scrim = Color(0x99000000), // rgba(0,0,0,0.6)
            velo = Color(0xFF0BA59B),
            muscu = Color(0xFF7C3AED),
            course = Color(0xFF0284C7),
            marche = Color(0xFF65A30D),
            heart = Color(0xFFF43F5E),
            danger = Color(0xFFDC2626),
            success = Color(0xFF10B981),
            warning = Color(0xFFE08600),
        )

        val Dark = PulseColors(
            text = Color(0xFFF4F7FB),
            textSecondary = Color(0xFF9AA3B0),
            textMuted = Color(0xFF868FA0),
            background = Color(0xFF0A0C10),
            backgroundElement = Color(0xFF14181F),
            surfaceHigh = Color(0xFF1B202A),
            backgroundSelected = Color(0xFF232A35),
            border = Color(0xFF222934),
            hairline = Color(0xFF1A1F28),
            accent = Color(0xFF5B7CFF),
            accentSoft = Color(0xFF1B2236),
            link = Color(0xFF6E8BFF),
            scrim = Color(0xA8000000), // rgba(0,0,0,0.66)
            velo = Color(0xFF22D3C5),
            muscu = Color(0xFFA78BFA),
            course = Color(0xFF38BDF8),
            marche = Color(0xFFA3E635),
            heart = Color(0xFFFF5C7A),
            danger = Color(0xFFFF4D4D),
            success = Color(0xFF34D399),
            warning = Color(0xFFFBBF24),
        )
    }
}

/**
 * Dégradés PULSE, identiques en clair et en sombre, tracés en diagonale
 * (0,0) → (1,1). Chaque entrée est la paire [début, fin] à passer à
 * `Brush.linearGradient`.
 */
object PulseGradients {
    val accent: List<Color> = listOf(Color(0xFF6478FF), Color(0xFF7A3BFF))
    val velo: List<Color> = listOf(Color(0xFF2DE0C0), Color(0xFF0BA9B5))
    val muscu: List<Color> = listOf(Color(0xFFB07BFF), Color(0xFF7A3BFF))
    val course: List<Color> = listOf(Color(0xFF5AC8FF), Color(0xFF3B7BFF))
    val marche: List<Color> = listOf(Color(0xFFC7F04F), Color(0xFF5FB828))
    val heart: List<Color> = listOf(Color(0xFFFF6B8B), Color(0xFFF43F5E))
    val danger: List<Color> = listOf(Color(0xFFFF5A5A), Color(0xFFD61F2E))
    val fire: List<Color> = listOf(Color(0xFFFFB020), Color(0xFFFF6B35))
    val success: List<Color> = listOf(Color(0xFF4ADE80), Color(0xFF10B981))
    /** rgba(10,12,16,0) → rgba(10,12,16,0.85) ; voile bas de carte / héros. */
    val scrim: List<Color> = listOf(Color(0x000A0C10), Color(0xD90A0C10))

    /** Dégradés assez clairs pour exiger une encre sombre ([OnBright]). */
    val BRIGHT_GRADIENTS: Set<List<Color>> = setOf(velo, success, fire, marche)

    /** Encre à poser sur un dégradé de [BRIGHT_GRADIENTS] ; blanc sur les autres. */
    val OnBright: Color = Color(0xFF0B0E13)

    /** L'encre lisible sur [gradient]. */
    fun inkOn(gradient: List<Color>): Color =
        if (gradient in BRIGHT_GRADIENTS) OnBright else Color.White
}

/** Couleurs des cinq zones de fréquence cardiaque, dans l'ordre zone 1 → 5. */
object HrZoneColors {
    val Light: List<Color> = listOf(
        Color(0xFFFBC7D3),
        Color(0xFFF59BB0),
        Color(0xFFEE7091),
        Color(0xFFE85C82),
        Color(0xFFF43F5E),
    )
    val Dark: List<Color> = listOf(
        Color(0xFF5C2A38),
        Color(0xFF8A3A50),
        Color(0xFFB84765),
        Color(0xFFDC5170),
        Color(0xFFFF5C7A),
    )
}

/** Rayons de coins. Pilules et champs en sm, Card et Button en lg, héros et feuilles en xl. */
object Radius {
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 22.dp
    val xl: Dp = 28.dp
    val pill: Dp = 999.dp
}

/** Échelle d'espacement sur grille de 4 pt. Marges écran = [three], inter-cartes = [three], gouttière 12. */
object Spacing {
    val half: Dp = 2.dp
    val one: Dp = 4.dp
    val two: Dp = 8.dp
    val three: Dp = 16.dp
    val four: Dp = 24.dp
    val five: Dp = 32.dp
    val six: Dp = 64.dp
}

/** Élévations (ombres Material) correspondant aux trois paliers PULSE. */
object Elevation {
    val sm: Dp = 3.dp
    val md: Dp = 8.dp
    val lg: Dp = 18.dp
}

/**
 * Ressorts PULSE transposés en [SpringSpec] Compose.
 *
 * React Native décrit un ressort par `{damping, stiffness, mass}` ; Compose
 * par `{dampingRatio, stiffness}` avec une masse implicite de 1. La
 * correspondance est `dampingRatio = damping / (2 * sqrt(stiffness * mass))`,
 * la raideur (`stiffness`) étant reprise telle quelle. Une masse ≠ 1 change
 * aussi la fréquence propre (ω = √(k/m)) ; l'écart (≤ 20 %) est jugé
 * imperceptible et volontairement ignoré pour garder les valeurs lisibles.
 *
 *   snappy : {20, 320, 0.7} → ratio 20 / (2·√224) ≈ 0.67
 *   bouncy : {13, 240, 0.8} → ratio 13 / (2·√192) ≈ 0.47
 *   gentle : {24, 170, 1.0} → ratio 24 / (2·√170) ≈ 0.92
 */
object Motion {
    val snappy: SpringSpec<Float> = spring(dampingRatio = 0.67f, stiffness = 320f)
    val bouncy: SpringSpec<Float> = spring(dampingRatio = 0.47f, stiffness = 240f)
    val gentle: SpringSpec<Float> = spring(dampingRatio = 0.92f, stiffness = 170f)

    /** Échelle appliquée aux surfaces pressées. */
    const val pressScale: Float = 0.96f
}

/** Largeur maximale du contenu sur tablette / paysage. */
val MaxContentWidth: Dp = 800.dp

internal val LocalPulseColors = staticCompositionLocalOf<PulseColors> {
    error("PulseColors non fournies — envelopper le contenu dans ElanTheme { }")
}
internal val LocalHrZoneColors = staticCompositionLocalOf<List<Color>> { HrZoneColors.Light }
