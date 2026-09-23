package ovh.battistella.elan.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Jetons de **Sillage**, le design system d'Élan (voir `DESIGN.md`) — les
 * valeurs nommées dont toute l'interface est construite.
 *
 * Trois idées les organisent :
 * - **Encre et papier** : des neutres chauds (encre verdâtre en sombre, papier
 *   crème en clair), une profondeur par paliers de ton plutôt que par l'ombre.
 * - **Un seul Volt** : la couleur de marque ([ElanColors.brand]) marque
 *   l'action principale et « maintenant » ; elle porte toujours une encre
 *   sombre ([ElanColors.onBrand]).
 * - **Une teinte = un sens** : vélo, course, marche, muscu, et le rose réservé
 *   au cardio.
 *
 * Material 3 (via [ElanTheme]) garde la mécanique ; ces jetons posent la
 * marque par-dessus, pour qu'un changement ici se propage à chaque écran.
 */

/** Couleurs sémantiques Sillage ; une instance claire (papier) et une sombre (encre). */
@Immutable
data class ElanColors(
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
    /**
     * Volt de marque, **en aplat** : bouton principal, puce et onglet actifs,
     * « aujourd'hui ». Identique en clair et en sombre ; toujours sous [onBrand].
     */
    val brand: Color,
    /** Encre posée sur [brand] (et sur toute teinte claire). */
    val onBrand: Color,
    /**
     * La marque **en trait** sur le fond : barres, jauges, icônes, curseurs
     * (≥ 3:1). Volt en sombre, olive en clair — le Volt pur disparaît sur le
     * papier.
     */
    val accent: Color,
    /** Fond teinté de la marque (pastille, sélection douce). */
    val accentSoft: Color,
    /** La marque **en texte** (liens « Voir tout ») : ≥ 4,5:1 sur les surfaces. */
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
        /** Papier : neutres chauds, teintes approfondies pour tenir sur le blanc. */
        val Light = ElanColors(
            text = Color(0xFF151712),
            textSecondary = Color(0xFF595C52),
            textMuted = Color(0xFF6B6E64),
            background = Color(0xFFF3F2EC),
            backgroundElement = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFFFFFFFF),
            backgroundSelected = Color(0xFFE8E7DF),
            border = Color(0xFFDAD9CF),
            hairline = Color(0xFFEAE9E2),
            brand = Color(0xFFD4F545),
            onBrand = Color(0xFF12140F),
            accent = Color(0xFF5A7F00),
            accentSoft = Color(0xFFEFF7CC),
            link = Color(0xFF4A6600),
            scrim = Color(0x99000000), // rgba(0,0,0,0.6)
            velo = Color(0xFF007584),
            muscu = Color(0xFF7440DB),
            course = Color(0xFFB8420B),
            marche = Color(0xFF2F5BD0),
            heart = Color(0xFFCF2358),
            danger = Color(0xFFC4261C),
            success = Color(0xFF137A3B),
            warning = Color(0xFF9A5B00),
        )

        /** Encre : noir chaud légèrement vert, pour que le Volt y vibre. */
        val Dark = ElanColors(
            text = Color(0xFFF3F4EE),
            textSecondary = Color(0xFFA7AB9E),
            textMuted = Color(0xFF8B8F82),
            background = Color(0xFF0D0E0B),
            backgroundElement = Color(0xFF171914),
            surfaceHigh = Color(0xFF1F221B),
            backgroundSelected = Color(0xFF292D24),
            border = Color(0xFF30352A),
            hairline = Color(0xFF22261E),
            brand = Color(0xFFD4F545),
            onBrand = Color(0xFF12140F),
            accent = Color(0xFFD4F545),
            accentSoft = Color(0xFF252B12),
            link = Color(0xFFD4F545),
            scrim = Color(0xA8000000), // rgba(0,0,0,0.66)
            velo = Color(0xFF35D6E6),
            muscu = Color(0xFFB899FF),
            course = Color(0xFFFF8B4D),
            marche = Color(0xFF86A8FF),
            heart = Color(0xFFFF5E8A),
            danger = Color(0xFFFF5D52),
            success = Color(0xFF5BDF8E),
            warning = Color(0xFFFFC247),
        )
    }
}

/**
 * Dégradés « sillage » : une teinte qui s'éclaircit en s'éloignant, comme la
 * trace qu'on laisse. Identiques en clair et en sombre, ils ne servent qu'aux
 * surfaces de marque toujours sombres (carte de partage, illustrations) —
 * jamais aux boutons, qui sont en aplat. Chaque entrée est la paire
 * [début, fin] à passer à `Brush.linearGradient`.
 */
object ElanGradients {
    val brand: List<Color> = listOf(Color(0xFFE6FF7A), Color(0xFFB9E01F))
    val velo: List<Color> = listOf(Color(0xFF7BEAF2), Color(0xFF14B5C9))
    val muscu: List<Color> = listOf(Color(0xFFD2BDFF), Color(0xFF9468F5))
    val course: List<Color> = listOf(Color(0xFFFFB384), Color(0xFFF26A22))
    val marche: List<Color> = listOf(Color(0xFFB5C9FF), Color(0xFF5F86F2))
    val heart: List<Color> = listOf(Color(0xFFFF93B1), Color(0xFFF03D72))
    val danger: List<Color> = listOf(Color(0xFFFF8A80), Color(0xFFE5392E))
    val fire: List<Color> = listOf(Color(0xFFFFD27A), Color(0xFFFF9A2E))
    val success: List<Color> = listOf(Color(0xFF8CF0B2), Color(0xFF2FC46E))
    /** Encre (#0D0E0B) transparente → 85 % ; voile bas de carte / héros. */
    val scrim: List<Color> = listOf(Color(0x000D0E0B), Color(0xD90D0E0B))

    /** Encre sombre à poser sur les teintes claires (toutes les teintes sombres du thème `Dark`). */
    val OnBright: Color = Color(0xFF12140F)

    /** L'encre lisible sur [gradient] : la plus contrastée entre [OnBright] et blanc sur sa teinte finale. */
    fun inkOn(gradient: List<Color>): Color = bestInk(gradient.last(), listOf(OnBright, Color.White))
}

/** Couleurs des cinq zones de fréquence cardiaque, dans l'ordre zone 1 → 5. */
object HrZoneColors {
    val Light: List<Color> = listOf(
        Color(0xFFF7C9D6),
        Color(0xFFF0A0B8),
        Color(0xFFE67497),
        Color(0xFFDB4A77),
        Color(0xFFCF2358),
    )
    val Dark: List<Color> = listOf(
        Color(0xFF4D2432),
        Color(0xFF7A3048),
        Color(0xFFAC3F62),
        Color(0xFFD94E78),
        Color(0xFFFF5E8A),
    )
}

/**
 * Rayons de coins. [xs] puces de donnée, [sm] pastilles d'icône et champs,
 * [md] sous-blocs, [lg] cartes, [xl] héros et feuilles, [pill] boutons, puces
 * et badges.
 */
object Radius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val pill: Dp = 999.dp
}

/** Échelle d'espacement sur grille de 4 pt. Marges écran = [three], inter-cartes = [three], gouttière = [gutter]. */
object Spacing {
    val half: Dp = 2.dp
    val one: Dp = 4.dp
    val two: Dp = 8.dp
    val gutter: Dp = 12.dp
    val three: Dp = 16.dp
    val four: Dp = 24.dp
    val five: Dp = 32.dp
    val six: Dp = 64.dp
}

/**
 * Élévations. Sillage signale la profondeur par le ton : l'ombre est réservée
 * à ce qui flotte vraiment au-dessus du contenu (barre de contrôle, feuilles).
 */
object Elevation {
    val none: Dp = 0.dp
    val sm: Dp = 2.dp
    val md: Dp = 8.dp
    val lg: Dp = 18.dp
}

/** Hauteurs de contrôle : cible tactile ≥ 48 dp partout. */
object ControlSize {
    val md: Dp = 52.dp
    val lg: Dp = 60.dp
    /** Tuile de démarrage d'activité (accueil). */
    val tile: Dp = 104.dp
}

/**
 * Ressorts Sillage transposés en [SpringSpec] Compose.
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

internal val LocalElanColors = staticCompositionLocalOf<ElanColors> {
    error("ElanColors non fournies — envelopper le contenu dans ElanTheme { }")
}
internal val LocalHrZoneColors = staticCompositionLocalOf<List<Color>> { HrZoneColors.Light }
