package ovh.battistella.elan.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ElanColors(
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val background: Color,
    val backgroundElement: Color,
    val surfaceHigh: Color,
    val backgroundSelected: Color,
    val border: Color,
    val hairline: Color,
    val brand: Color,
    val onBrand: Color,
    val accent: Color,
    val accentSoft: Color,
    val link: Color,
    val scrim: Color,
    val velo: Color,
    val muscu: Color,
    val course: Color,
    val marche: Color,
    val heart: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
) {
    companion object {
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
            scrim = Color(0x99000000),
            velo = Color(0xFF007584),
            muscu = Color(0xFF7440DB),
            course = Color(0xFFB8420B),
            marche = Color(0xFF2F5BD0),
            heart = Color(0xFFCF2358),
            danger = Color(0xFFC4261C),
            success = Color(0xFF137A3B),
            warning = Color(0xFF9A5B00),
        )

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
            scrim = Color(0xA8000000),
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
    val scrim: List<Color> = listOf(Color(0x000D0E0B), Color(0xD90D0E0B))

    val OnBright: Color = Color(0xFF12140F)

    fun inkOn(gradient: List<Color>): Color = bestInk(gradient.last(), listOf(OnBright, Color.White))
}

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

object Radius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val pill: Dp = 999.dp
}

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

object Elevation {
    val none: Dp = 0.dp
    val sm: Dp = 2.dp
    val md: Dp = 8.dp
    val lg: Dp = 18.dp
}

object ControlSize {
    val md: Dp = 52.dp
    val lg: Dp = 60.dp
    val tile: Dp = 104.dp
}

object Motion {
    val snappy: SpringSpec<Float> = spring(dampingRatio = 0.67f, stiffness = 320f)
    val bouncy: SpringSpec<Float> = spring(dampingRatio = 0.47f, stiffness = 240f)
    val gentle: SpringSpec<Float> = spring(dampingRatio = 0.92f, stiffness = 170f)

    const val pressScale: Float = 0.96f
}

val MaxContentWidth: Dp = 800.dp

internal val LocalElanColors = staticCompositionLocalOf<ElanColors> {
    error("ElanColors non fournies — envelopper le contenu dans ElanTheme { }")
}
internal val LocalHrZoneColors = staticCompositionLocalOf<List<Color>> { HrZoneColors.Light }
