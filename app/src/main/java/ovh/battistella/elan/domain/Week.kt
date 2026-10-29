package ovh.battistella.elan.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val MONDAY_FIRST_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")

private fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun startOfWeekMs(now: Long): Long {
    val zone = ZoneId.systemDefault()
    val date = localDate(now)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    return monday.atStartOfDay(zone).toInstant().toEpochMilli()
}

fun localDayKey(d: LocalDate): String =
    "${d.year}-${d.monthValue.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"

fun localDayKey(ms: Long): String = localDayKey(localDate(ms))

data class DailyDuration(val day: String, val durationSec: Int)

data class DayBar(val label: String, val value: Int)

fun dailyDurationBars(daily: List<DailyDuration>, days: Int, now: Long): List<DayBar> {
    val byDay = daily.associate { it.day to it.durationSec }
    val today = localDate(now)
    val out = ArrayList<DayBar>(days)
    for (i in days - 1 downTo 0) {
        val d = today.minusDays(i.toLong())
        out.add(DayBar(label = MONDAY_FIRST_LABELS[d.dayOfWeek.value - 1], value = byDay[localDayKey(d)] ?: 0))
    }
    return out
}
