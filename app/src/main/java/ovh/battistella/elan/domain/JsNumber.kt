// Reproduction de `Number.prototype.toString()` de JavaScript (ECMA-262,
// Number::toString, radix 10). Nécessaire partout où une chaîne dérivée d'un
// nombre doit rester identique à celle de l'app d'origine : hachage des
// `externalId` d'import (sinon les doublons ne sont plus détectés) et
// coordonnées des exports GPX.
//
// Règles : plus courte représentation décimale qui redonne le même double ;
// entiers sans « .0 » ; notation positionnelle tant que l'exposant décimal n
// (position du point) vérifie -6 < n <= 21, sinon « d.ddde±x ».
package ovh.battistella.elan.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

fun Double.toJsString(): String {
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0) "Infinity" else "-Infinity"
    if (this == 0.0) return "0" // couvre aussi -0.0 : JS affiche « 0 »

    val negative = this < 0
    val abs = if (negative) -this else this
    val decimal = shortestRoundTrip(abs).stripTrailingZeros()
    val digits = decimal.unscaledValue().toString()
    val k = digits.length
    // Valeur = digits × 10^(n − k) : n est la position du point décimal.
    val n = k - decimal.scale()

    val body = when {
        n in k..21 -> digits + "0".repeat(n - k)
        n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
        n in -5..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val e = n - 1
            val exponent = if (e >= 0) "e+$e" else "e$e"
            if (k == 1) digits + exponent else digits[0] + "." + digits.substring(1) + exponent
        }
    }
    return if (negative) "-$body" else body
}

/**
 * Plus court décimal (en chiffres significatifs) qui se réanalyse en `value`.
 * À longueur égale, JS retient le candidat le plus proche de la valeur exacte
 * et, à égalité, le plus grand : on teste donc à chaque précision l'arrondi
 * par défaut et par excès de la valeur binaire exacte.
 */
private fun shortestRoundTrip(value: Double): BigDecimal {
    val exact = BigDecimal(value)
    for (precision in 1..17) {
        val lo = exact.round(MathContext(precision, RoundingMode.FLOOR))
        val hi = exact.round(MathContext(precision, RoundingMode.CEILING))
        val loOk = lo.toDouble() == value
        val hiOk = hi.toDouble() == value
        when {
            loOk && hiOk -> return if (hi.subtract(exact) <= exact.subtract(lo)) hi else lo
            loOk -> return lo
            hiOk -> return hi
        }
    }
    return exact // 17 chiffres suffisent toujours pour un double : inatteignable
}
