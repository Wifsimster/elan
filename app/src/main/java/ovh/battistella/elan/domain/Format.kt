// Helpers de formatage (FR). Les dates sont rendues dans le fuseau par défaut
// du système, comme `Date` côté JavaScript.
package ovh.battistella.elan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max

private fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

/** 3725 -> "1:02:05" ; 125 -> "2:05". */
fun formatDuration(totalSec: Int): String = formatDuration(totalSec.toDouble())

fun formatDuration(totalSec: Double): String {
    val s = max(0L, floor(totalSec).toLong())
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val mm = m.toString().padStart(2, '0')
    val ss = sec.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
}

/** Durée compacte pour les listes : "1 h 02", "45 min". */
fun formatDurationShort(totalSec: Int): String = formatDurationShort(totalSec.toDouble())

fun formatDurationShort(totalSec: Double): String {
    val s = max(0L, floor(totalSec).toLong())
    // On arrondit à la minute la plus proche d'abord, puis on dérive heures et
    // minutes : arrondir les minutes seules pouvait produire « 1 h 60 ».
    val totalMin = Math.round(s / 60.0)
    val h = totalMin / 60
    val m = totalMin % 60
    if (h > 0) return "$h h ${m.toString().padStart(2, '0')}"
    return "$m min"
}

/**
 * Mesure scindée en chiffre et unité, pour l'afficher en grand nombre + petite
 * unité. `unit` est absente quand la valeur est inconnue (« — »).
 */
data class Measure(val value: String, val unit: String? = null)

/** Recompose une `Measure` en chaîne « valeur unité » (« 12,4 km »). */
private fun joinMeasure(m: Measure): String = if (m.unit != null) "${m.value} ${m.unit}" else m.value

/**
 * Décimale française : 12.4 -> "12,4". Arrondi demi-supérieur sur la valeur
 * binaire exacte, comme `Number#toFixed` (18.27 → « 18,3 »).
 */
private fun decimalFr(n: Double, digits: Int = 1): String =
    BigDecimal(n).setScale(digits, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

private fun roundStr(n: Double): String = Math.round(n).toString()

/** Mètres -> { "12,4", "km" } ou { "850", "m" }. */
fun distanceParts(meters: Double?): Measure {
    if (meters == null) return Measure("—")
    if (meters >= 1000) return Measure(decimalFr(meters / 1000), "km")
    return Measure(roundStr(meters), "m")
}

fun speedParts(kmh: Double?): Measure {
    if (kmh == null) return Measure("—")
    return Measure(decimalFr(kmh), "km/h")
}

/**
 * Allure en minutes par kilomètre, dérivée d'une vitesse en km/h (« 5:12 » + « /km »).
 * En dessous de 1 km/h (arrêt, capteur muet) l'allure part à l'infini — tiret.
 */
fun paceParts(kmh: Double?): Measure {
    if (kmh == null || kmh < 1) return Measure("—")
    val secPerKm = Math.round(3600 / kmh)
    if (secPerKm >= 3600) return Measure("—")
    val min = secPerKm / 60
    val sec = secPerKm % 60
    return Measure("$min:${sec.toString().padStart(2, '0')}", "/km")
}

fun hrParts(bpm: Double?): Measure {
    if (bpm == null) return Measure("—")
    return Measure(roundStr(bpm), "bpm")
}

fun caloriesParts(kcal: Double?): Measure {
    if (kcal == null) return Measure("—")
    return Measure(roundStr(kcal), "kcal")
}

/** Mètres -> { "57", "m" } (dénivelé, jamais converti en km ; null → 0). */
fun elevationParts(meters: Double?): Measure = Measure(roundStr(meters ?: 0.0), "m")

/** Tours/min -> { "84", "tr/min" } (cadence). */
fun cadenceParts(rpm: Double?): Measure {
    if (rpm == null) return Measure("—")
    return Measure(roundStr(rpm), "tr/min")
}

/** Mètres -> "12,4 km" ou "850 m". */
fun formatDistance(meters: Double?): String = joinMeasure(distanceParts(meters))

fun formatSpeed(kmh: Double?): String = joinMeasure(speedParts(kmh))

/** Allure prête à afficher : « 5:12 /km ». */
fun formatPace(kmh: Double?): String = joinMeasure(paceParts(kmh))

fun formatHr(bpm: Double?): String = joinMeasure(hrParts(bpm))

fun formatCalories(kcal: Double?): String = joinMeasure(caloriesParts(kcal))

// Index 0 = dimanche, comme `Date#getDay()`.
private val JOURS = listOf("dim.", "lun.", "mar.", "mer.", "jeu.", "ven.", "sam.")
private val MOIS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc.",
)

/** "lun. 2 juin, 18:30" ; avec `withYear` : "lun. 2 juin 2025, 18:30". */
fun formatDateTime(ms: Long, withYear: Boolean = false): String {
    val d = zoned(ms)
    val heure = "${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
    val annee = if (withYear) " ${d.year}" else ""
    return "${JOURS[d.dayOfWeek.value % 7]} ${d.dayOfMonth} ${MOIS[d.monthValue - 1]}$annee, $heure"
}

fun formatDateShort(ms: Long): String {
    val d = zoned(ms)
    return "${d.dayOfMonth} ${MOIS[d.monthValue - 1]}"
}

/** « aujourd'hui », « hier », « il y a 4 jours » — relatif au début de journée. */
fun formatRelativeDays(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
    val day: LocalDate = zoned(ms).toLocalDate()
    val today: LocalDate = zoned(nowMs).toLocalDate()
    val diffDays = ChronoUnit.DAYS.between(day, today)
    if (diffDays <= 0) return "aujourd'hui"
    if (diffDays == 1L) return "hier"
    return "il y a $diffDays jours"
}
