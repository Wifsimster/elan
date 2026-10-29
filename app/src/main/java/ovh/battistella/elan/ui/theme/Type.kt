package ovh.battistella.elan.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R

object ElanFonts {
    val sans = FontFamily(
        Font(R.font.archivo_regular, FontWeight.Normal),
        Font(R.font.archivo_medium, FontWeight.Medium),
        Font(R.font.archivo_semibold, FontWeight.SemiBold),
        Font(R.font.archivo_bold, FontWeight.Bold),
        Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
    )
    val condensed = FontFamily(
        Font(R.font.archivo_condensed_bold, FontWeight.Bold),
        Font(R.font.archivo_condensed_extrabold, FontWeight.ExtraBold),
        Font(R.font.archivo_condensed_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    )
}

object ElanType {
    private const val TABULAR = "tnum"

    val display = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 52.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
        lineHeight = 54.sp,
        fontFeatureSettings = TABULAR,
    )
    val metricLg = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 84.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.5).sp,
        lineHeight = 84.sp,
        fontFeatureSettings = TABULAR,
    )
    val metric = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp,
        fontFeatureSettings = TABULAR,
    )
    val metricSm = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 26.sp,
        fontFeatureSettings = TABULAR,
    )
    val title = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp,
    )
    val headline = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        lineHeight = 24.sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    )
    val subtitle = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeight = 21.sp,
    )
    val body = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    )
    val bodySm = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )
    val label = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    )
    val caption = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    )
    val micro = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
    )
    val overline = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        lineHeight = 16.sp,
    )
    val button = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.1).sp,
        lineHeight = 20.sp,
    )
    val buttonLg = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    )
}
