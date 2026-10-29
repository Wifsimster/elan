package ovh.battistella.elan.domain

import kotlin.math.pow

private val HEX6 = Regex("^[0-9a-fA-F]{6}$")

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

fun relativeLuminance(hex: String): Double {
    val rgb = channels(hex) ?: return 0.0
    val lin = rgb.map { v ->
        val c = v / 255.0
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
}

fun contrastRatio(a: String, b: String): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = if (la >= lb) la else lb
    val lo = if (la >= lb) lb else la
    return (hi + 0.05) / (lo + 0.05)
}

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
