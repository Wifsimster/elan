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

fun formatDurationShort(totalSec: Int): String = formatDurationShort(totalSec.toDouble())

fun formatDurationShort(totalSec: Double): String {
    val s = max(0L, floor(totalSec).toLong())
    val totalMin = Math.round(s / 60.0)
    val h = totalMin / 60
    val m = totalMin % 60
    if (h > 0) return "$h h ${m.toString().padStart(2, '0')}"
    return "$m min"
}

data class Measure(val value: String, val unit: String? = null)

private fun joinMeasure(m: Measure): String = if (m.unit != null) "${m.value} ${m.unit}" else m.value

private fun decimalFr(n: Double, digits: Int = 1): String =
    BigDecimal(n).setScale(digits, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

private fun roundStr(n: Double): String = Math.round(n).toString()

fun distanceParts(meters: Double?): Measure {
    if (meters == null) return Measure("—")
    if (meters >= 1000) return Measure(decimalFr(meters / 1000), "km")
    return Measure(roundStr(meters), "m")
}

fun speedParts(kmh: Double?): Measure {
    if (kmh == null) return Measure("—")
    return Measure(decimalFr(kmh), "km/h")
}

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

fun elevationParts(meters: Double?): Measure = Measure(roundStr(meters ?: 0.0), "m")

fun cadenceParts(rpm: Double?): Measure {
    if (rpm == null) return Measure("—")
    return Measure(roundStr(rpm), "tr/min")
}

fun formatDistance(meters: Double?): String = joinMeasure(distanceParts(meters))

fun formatSpeed(kmh: Double?): String = joinMeasure(speedParts(kmh))

fun formatPace(kmh: Double?): String = joinMeasure(paceParts(kmh))

fun formatHr(bpm: Double?): String = joinMeasure(hrParts(bpm))

fun formatCalories(kcal: Double?): String = joinMeasure(caloriesParts(kcal))

private val JOURS = listOf("dim.", "lun.", "mar.", "mer.", "jeu.", "ven.", "sam.")
private val MOIS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc.",
)

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

fun formatRelativeDays(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
    val day: LocalDate = zoned(ms).toLocalDate()
    val today: LocalDate = zoned(nowMs).toLocalDate()
    val diffDays = ChronoUnit.DAYS.between(day, today)
    if (diffDays <= 0) return "aujourd'hui"
    if (diffDays == 1L) return "hier"
    return "il y a $diffDays jours"
}
