package ovh.battistella.elan.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Les onze styles de texte Sillage (docs/port-spec/02-interface.md §5), en
 * police système (aucune police embarquée). Les styles de métriques utilisent
 * des chiffres tabulaires (`tnum`) pour que les valeurs qui défilent ne
 * sautent pas. L'overline est en MAJUSCULES : la transformation est laissée
 * aux appelants (`text.uppercase()`), Compose n'ayant pas de textTransform.
 */
object ElanType {
    private const val TABULAR = "tnum"

    val display = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 44.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.2).sp,
        lineHeight = 48.sp,
    )
    val metricLg = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 64.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR,
    )
    val metric = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR,
    )
    val title = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
        lineHeight = 32.sp,
    )
    val headline = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.3).sp,
        lineHeight = 26.sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 24.sp,
    )
    val subtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    /** MAJUSCULES à la charge de l'appelant. */
    val overline = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )
}
