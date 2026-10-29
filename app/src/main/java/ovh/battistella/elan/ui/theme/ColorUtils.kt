package ovh.battistella.elan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ovh.battistella.elan.domain.bestInk

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

fun Color.toHex6(): String = "#%06X".format(toArgb() and 0xFFFFFF)

fun bestInk(background: Color, candidates: List<Color>): Color {
    val chosen = bestInk(background.toHex6(), candidates.map { it.toHex6() })
    return candidates.first { it.toHex6().equals(chosen, ignoreCase = true) }
}
