// Tests pour les helpers de formatage (durée, distance, vitesse, FC, date — FR).
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormatTest {
    private fun local(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `formatDuration formate m ss sous une heure`() {
        assertEquals("2:05", formatDuration(125))
    }

    @Test
    fun `formatDuration formate h mm ss au-delà d'une heure`() {
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test
    fun `formatDuration renvoie 0 00 pour 0 ou valeur négative`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-5))
    }

    @Test
    fun `formatDuration tronque les fractions de secondes`() {
        assertEquals("0:59", formatDuration(59.9))
    }

    @Test
    fun `formatDuration padde les minutes et secondes à 2 chiffres`() {
        assertEquals("1:00:01", formatDuration(3601))
    }

    @Test
    fun `formatDurationShort moins d'une heure → M min`() {
        assertEquals("45 min", formatDurationShort(45 * 60))
    }

    @Test
    fun `formatDurationShort au-delà d'une heure → H h MM`() {
        assertEquals("1 h 02", formatDurationShort(3720))
    }

    @Test
    fun `formatDurationShort 0 secondes → 0 min`() {
        assertEquals("0 min", formatDurationShort(0))
    }

    @Test
    fun `formatDurationShort reporte la retenue des minutes au lieu de produire H h 60`() {
        // 1 h 59 min 30 s arrondit à 2 h pile (et non « 1 h 60 »).
        assertEquals("2 h 00", formatDurationShort(7170))
        assertEquals("2 h 00", formatDurationShort(7189))
        // Juste sous le seuil de report : reste « 1 h 59 ».
        assertEquals("1 h 59", formatDurationShort(7169))
    }

    @Test
    fun `formatDistance null → tiret`() {
        assertEquals("—", formatDistance(null))
    }

    @Test
    fun `formatDistance sous 1 km → N m`() {
        assertEquals("850 m", formatDistance(850.0))
        assertEquals("0 m", formatDistance(0.0))
    }

    @Test
    fun `formatDistance ≥ 1 km → N,N km avec virgule décimale (FR)`() {
        assertEquals("12,4 km", formatDistance(12_400.0))
        assertEquals("1,0 km", formatDistance(1_000.0))
    }

    @Test
    fun `formatDistance arrondit les mètres`() {
        assertEquals("123 m", formatDistance(123.4))
    }

    @Test
    fun `formatSpeed null → tiret`() {
        assertEquals("—", formatSpeed(null))
    }

    @Test
    fun `formatSpeed 1 décimale avec virgule`() {
        assertEquals("23,5 km/h", formatSpeed(23.5))
        assertEquals("0,0 km/h", formatSpeed(0.0))
        assertEquals("18,3 km/h", formatSpeed(18.27))
    }

    @Test
    fun `decimalFr arrondit comme toFixed (demi-supérieur sur la valeur exacte)`() {
        // 1,05 est stocké légèrement au-dessus : « 1,1 » ; 0,15 légèrement en dessous : « 0,1 » (comme en JS).
        assertEquals("1,1 km/h", formatSpeed(1.05))
        assertEquals("0,1 km/h", formatSpeed(0.15))
        assertEquals("2,5 km/h", formatSpeed(2.45))
    }

    @Test
    fun `formatHr null → tiret`() {
        assertEquals("—", formatHr(null))
    }

    @Test
    fun `formatHr arrondit en bpm`() {
        assertEquals("72 bpm", formatHr(72.4))
        assertEquals("73 bpm", formatHr(72.6))
    }

    @Test
    fun `formatCalories null → tiret`() {
        assertEquals("—", formatCalories(null))
    }

    @Test
    fun `formatCalories arrondit en kcal`() {
        assertEquals("413 kcal", formatCalories(412.6))
    }

    @Test
    fun `distanceParts sépare le chiffre de l'unité`() {
        assertEquals(Measure("12,4", "km"), distanceParts(12_400.0))
        assertEquals(Measure("850", "m"), distanceParts(850.0))
    }

    @Test
    fun `valeur inconnue → pas d'unité (tiret seul)`() {
        assertEquals(Measure("—"), distanceParts(null))
        assertEquals(Measure("—"), speedParts(null))
        assertEquals(Measure("—"), hrParts(null))
        assertEquals(Measure("—"), caloriesParts(null))
        assertEquals(Measure("—"), cadenceParts(null))
    }

    @Test
    fun `speedParts utilise la virgule décimale (FR)`() {
        assertEquals(Measure("18,3", "km/h"), speedParts(18.27))
    }

    @Test
    fun `hrParts et caloriesParts arrondissent`() {
        assertEquals(Measure("73", "bpm"), hrParts(72.6))
        assertEquals(Measure("413", "kcal"), caloriesParts(412.6))
    }

    @Test
    fun `elevationParts reste en mètres et tolère null (→ 0)`() {
        assertEquals(Measure("57", "m"), elevationParts(57.0))
        assertEquals(Measure("0", "m"), elevationParts(null))
    }

    @Test
    fun `cadenceParts arrondit les tr par min`() {
        assertEquals(Measure("84", "tr/min"), cadenceParts(84.4))
    }

    @Test
    fun `les formateurs-chaîne restent cohérents avec leurs parts`() {
        assertEquals("12,4 km", formatDistance(12_400.0))
        assertEquals("18,3 km/h", formatSpeed(18.27))
        assertEquals("73 bpm", formatHr(72.6))
        assertEquals("413 kcal", formatCalories(412.6))
    }

    // 2 juin 2025 (lundi) à 18:30 — fixe par construction d'une date locale.
    private val ms = local(2025, 6, 2, 18, 30)

    @Test
    fun `formatDateTime concatène jour, date, mois, heure`() {
        assertEquals("lun. 2 juin, 18:30", formatDateTime(ms))
    }

    @Test
    fun `formatDateTime ajoute l'année sur demande`() {
        assertEquals("lun. 2 juin 2025, 18:30", formatDateTime(ms, withYear = true))
    }

    @Test
    fun `formatDateShort renvoie jour mois abrégé`() {
        assertEquals("2 juin", formatDateShort(ms))
    }

    @Test
    fun `formatDateTime padde l'heure à 2 chiffres`() {
        assertEquals("mer. 1 janv., 09:05", formatDateTime(local(2025, 1, 1, 9, 5)))
    }

    @Test
    fun `formatDateTime - dimanche et décembre (bornes des tables)`() {
        assertEquals("dim. 28 déc., 23:59", formatDateTime(local(2025, 12, 28, 23, 59)))
    }

    @Test
    fun `formatRelativeDays - aujourd'hui, hier, il y a N jours`() {
        val now = local(2025, 6, 11, 14, 30)
        assertEquals("aujourd'hui", formatRelativeDays(local(2025, 6, 11, 0, 1), now))
        assertEquals("aujourd'hui", formatRelativeDays(local(2025, 6, 12, 9, 0), now)) // futur → aujourd'hui
        assertEquals("hier", formatRelativeDays(local(2025, 6, 10, 23, 59), now))
        assertEquals("il y a 4 jours", formatRelativeDays(local(2025, 6, 7, 12, 0), now))
    }

    @Test
    fun `paceParts convertit une vitesse en minutes par kilomètre`() {
        assertEquals(Measure("5:00", "/km"), paceParts(12.0))
        assertEquals(Measure("6:00", "/km"), paceParts(10.0))
        assertEquals(Measure("12:00", "/km"), paceParts(5.0))
    }

    @Test
    fun `paceParts padde les secondes à deux chiffres`() {
        // 11,5 km/h → 313 s/km → 5 min 13 s
        assertEquals("5:13", paceParts(11.5).value)
    }

    @Test
    fun `paceParts rend un tiret à l'arrêt ou sans mesure`() {
        assertEquals(Measure("—"), paceParts(null))
        assertEquals(Measure("—"), paceParts(0.0))
        // Sous 1 km/h l'allure part à l'infini : un tiret plutôt qu'un nombre absurde.
        assertEquals(Measure("—"), paceParts(0.4))
    }

    @Test
    fun `formatPace joint valeur et unité`() {
        assertEquals("5:00 /km", formatPace(12.0))
        assertEquals("—", formatPace(null))
    }

    @Test
    fun `plus on va vite, plus l'allure est basse`() {
        val lent = paceParts(8.0).value
        val rapide = paceParts(16.0).value
        assertTrue(lent > rapide) // '7:30' > '3:45' en ordre lexical à minutes égales
    }
}
