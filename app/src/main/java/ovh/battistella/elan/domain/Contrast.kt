// Choix d'une encre lisible sur un aplat de couleur.
//
// Les puces sélectionnées peignent leur fond avec la teinte de l'activité : du
// blanc y est illisible dès que la teinte est claire (le lime de la marche, le
// teal du vélo tombent sous 2:1). Plutôt que de maintenir une liste de « teintes
// claires » à la main, on mesure. Pur et sans dépendance au thème, donc testé.
package ovh.battistella.elan.domain

import kotlin.math.pow

private val HEX6 = Regex("^[0-9a-fA-F]{6}$")

/** Canaux 0-255 d'un hex `#rrggbb` (ou `#rgb`). `null` si la chaîne est invalide. */
private fun channels(hex: String): IntArray? {
    val clean = hex.trim().removePrefix("#")
    val full = if (clean.length == 3) clean.map { "$it$it" }.joinToString("") else clean
    if (!HEX6.matches(full)) return null
    return intArrayOf(
        full.substring(0, 2).toInt(16),
        full.substring(2, 4).toInt(16),
        full.substring(4, 6).toInt(16),
    )
}

/** Luminance relative WCAG 2.x d'une couleur opaque, entre 0 (noir) et 1 (blanc). */
fun relativeLuminance(hex: String): Double {
    val rgb = channels(hex) ?: return 0.0
    val lin = rgb.map { v ->
        val c = v / 255.0
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
}

/** Rapport de contraste WCAG entre deux couleurs opaques, de 1:1 à 21:1. */
fun contrastRatio(a: String, b: String): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = if (la >= lb) la else lb
    val lo = if (la >= lb) lb else la
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * Encre la plus lisible sur `background` parmi `candidates` (à égalité, la
 * première l'emporte). On choisit toujours la meilleure des deux plutôt que de
 * comparer à un seuil : sur une teinte moyenne, aucune ne passerait le seuil et
 * il faudrait quand même en prendre une.
 */
fun bestInk(background: String, candidates: List<String>): String {
    var best = candidates[0]
    var bestRatio = -1.0
    for (ink in candidates) {
        val ratio = contrastRatio(background, ink)
        if (ratio > bestRatio) {
            best = ink
            bestRatio = ratio
        }
    }
    return best
}
