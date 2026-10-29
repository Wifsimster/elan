package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeekTest {
    private fun local(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    @Test
    fun `startOfWeekMs renvoie le lundi minuit (local) de la semaine`() {
        val monday = zoned(startOfWeekMs(local(2025, 6, 11, 14, 30)))
        assertEquals(2025, monday.year)
        assertEquals(6, monday.monthValue)
        assertEquals(9, monday.dayOfMonth)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(0, monday.hour)
        assertEquals(0, monday.minute)
    }

    @Test
    fun `startOfWeekMs un lundi se renvoie lui-même (minuit)`() {
        val start = zoned(startOfWeekMs(local(2025, 6, 9, 8, 0)))
        assertEquals(9, start.dayOfMonth)
        assertEquals(0, start.hour)
    }

    @Test
    fun `startOfWeekMs un dimanche renvoie le lundi précédent`() {
        val start = zoned(startOfWeekMs(local(2025, 6, 15, 23, 0)))
        assertEquals(9, start.dayOfMonth)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
    }

    @Test
    fun `localDayKey formate en YYYY-MM-DD avec zéro initial`() {
        assertEquals("2025-01-05", localDayKey(LocalDate.of(2025, 1, 5)))
        assertEquals("2025-12-31", localDayKey(LocalDate.of(2025, 12, 31)))
        assertEquals("2025-01-05", localDayKey(local(2025, 1, 5, 23, 59)))
    }

    private val now = local(2025, 6, 11, 10, 0)

    @Test
    fun `dailyDurationBars produit days barres, aujourd'hui en dernier`() {
        val bars = dailyDurationBars(emptyList(), 7, now)
        assertEquals(7, bars.size)
        assertTrue(bars.all { it.value == 0 })
        assertEquals("M", bars[6].label)
    }

    @Test
    fun `dailyDurationBars apparie les durées par clé de jour locale`() {
        val daily = listOf(
            DailyDuration(localDayKey(LocalDate.of(2025, 6, 11)), 1800),
            DailyDuration(localDayKey(LocalDate.of(2025, 6, 9)), 3600),
        )
        val bars = dailyDurationBars(daily, 7, now)
        assertEquals(1800, bars[6].value)
        assertEquals(3600, bars[4].value)
        assertEquals("J", bars[0].label)
    }

    @Test
    fun `dailyDurationBars ignore les jours hors fenêtre`() {
        val daily = listOf(DailyDuration(localDayKey(LocalDate.of(2025, 6, 1)), 9999))
        assertTrue(dailyDurationBars(daily, 7, now).all { it.value == 0 })
    }
}
