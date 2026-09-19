// Aides temporelles « semaine » pour l'accueil : début de semaine (lundi) et
// agrégation des durées quotidiennes en barres alignées sur les N derniers
// jours. Heure locale = fuseau par défaut du système.
package ovh.battistella.elan.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Initiales des jours, lundi en tête (index 0 = lundi).
private val MONDAY_FIRST_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")

private fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** Minuit (heure locale) du lundi de la semaine contenant `now` (ms epoch). */
fun startOfWeekMs(now: Long): Long {
    val zone = ZoneId.systemDefault()
    val date = localDate(now)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong()) // lundi = 1
    return monday.atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * Clé locale `YYYY-MM-DD` d'une date — même formulation que
 * `date(..., 'localtime')` côté SQLite, pour apparier les lignes renvoyées par la base.
 */
fun localDayKey(d: LocalDate): String =
    "${d.year}-${d.monthValue.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"

fun localDayKey(ms: Long): String = localDayKey(localDate(ms))

/** Durée totale d'un jour (`day` = clé `YYYY-MM-DD`). */
data class DailyDuration(val day: String, val durationSec: Int)

/** Une barre du graphe d'activité : initiale du jour + durée (s). */
data class DayBar(val label: String, val value: Int)

/**
 * Construit les barres des `days` derniers jours (aujourd'hui à droite) à partir
 * des durées quotidiennes. Le libellé est l'initiale du jour (lundi en tête) ;
 * les jours sans donnée valent 0.
 */
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
