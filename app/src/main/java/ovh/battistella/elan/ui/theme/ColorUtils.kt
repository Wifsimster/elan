package ovh.battistella.elan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ovh.battistella.elan.domain.bestInk

/**
 * Résout une clé de teinte du domaine (`ActivityMeta.colorKey`,
 * `Effort.colorKey`) en couleur du thème courant. Les briques du domaine ne
 * connaissent pas le thème : c'est ici que « velo » devient un teal clair ou
 * sombre. Une clé inconnue retombe sur l'accent.
 */
fun ElanColors.forKey(key: String): Color = when (key) {
    "velo" -> velo
    "muscu" -> muscu
    "course" -> course
    "marche" -> marche
    "heart" -> heart
    "danger" -> danger
    "success" -> success
    "warning" -> warning
    "accent" -> accent
    "text" -> text
    "textSecondary" -> textSecondary
    "textMuted" -> textMuted
    else -> accent
}

/** `#rrggbb` de la couleur (canal alpha ignoré), pour les calculs de contraste. */
fun Color.toHex6(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** Variante typée de [bestInk] : l'encre la plus lisible sur [background]. */
fun bestInk(background: Color, candidates: List<Color>): Color {
    val chosen = bestInk(background.toHex6(), candidates.map { it.toHex6() })
    return candidates.first { it.toHex6().equals(chosen, ignoreCase = true) }
}
