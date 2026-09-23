package ovh.battistella.elan.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R

/**
 * Familles Sillage, embarquées (`res/font/`, Archivo — licence OFL, voir
 * `docs/LICENSES-ASSETS.md`) : aucune police téléchargée.
 * - [sans] : Archivo largeur normale, pour tout le texte courant.
 * - [condensed] : Archivo Condensed, pour les chiffres et les titres —
 *   étroit, dense, tabulaire ; l'italique porte le mouvement (logotype,
 *   titres d'écran).
 */
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

/**
 * L'échelle typographique Sillage. Deux voix :
 * - **les chiffres** (`metric*`, `display`) en Archivo Condensed ExtraBold,
 *   tabulaires (`tnum`) pour que les valeurs qui défilent ne sautent pas ;
 * - **les mots** en Archivo : titres d'écran en condensé italique (l'élan du
 *   logotype), le reste en largeur normale.
 * L'overline est en MAJUSCULES : la transformation est laissée aux appelants
 * (`text.uppercase()`), Compose n'ayant pas de textTransform.
 */
object ElanType {
    private const val TABULAR = "tnum"

    /** Très grand chiffre héros (bilan de séance, poids). */
    val display = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 52.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
        lineHeight = 54.sp,
        fontFeatureSettings = TABULAR,
    )
    /** Le chronomètre de séance. */
    val metricLg = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 84.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.5).sp,
        lineHeight = 84.sp,
        fontFeatureSettings = TABULAR,
    )
    /** Valeur de StatTile. */
    val metric = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp,
        fontFeatureSettings = TABULAR,
    )
    /** Petite valeur chiffrée (ligne de séance, tuile compacte, compteur). */
    val metricSm = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 26.sp,
        fontFeatureSettings = TABULAR,
    )
    /** Titre d'écran (« Historique », « Réglages ») : condensé italique, comme le logotype. */
    val title = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp,
    )
    /** Titre de section / de carte. */
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
    /** Titre de ligne (séance, exercice). */
    val subtitle = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeight = 21.sp,
    )
    /** Texte courant. */
    val body = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    )
    /** Texte courant resserré : sous-titres de ligne, aides, dates. */
    val bodySm = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )
    /** Libellé de métrique, de champ. */
    val label = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    )
    /** Légende, unité. */
    val caption = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    )
    /** Très petit libellé (axes de graphe, badges). */
    val micro = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
    )
    /** Étiquette de section (« DURÉE », « NOTES ») — MAJUSCULES à la charge de l'appelant. */
    val overline = TextStyle(
        fontFamily = ElanFonts.condensed,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        lineHeight = 16.sp,
    )
    /** Libellé de bouton, de puce, de lien. */
    val button = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.1).sp,
        lineHeight = 20.sp,
    )
    /** Libellé de bouton large et de tuile de démarrage. */
    val buttonLg = TextStyle(
        fontFamily = ElanFonts.sans,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 22.sp,
    )
}
