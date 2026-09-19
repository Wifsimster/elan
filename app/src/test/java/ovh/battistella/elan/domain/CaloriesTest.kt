// Tests pour l'estimation calories (méthode MET) et les zones cardio.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaloriesTest {
    private val velo = ActivityType.VELO
    private val course = ActivityType.COURSE
    private val marche = ActivityType.MARCHE
    private val muscu = ActivityType.MUSCU

    @Test
    fun `musculation - MUSCU_MET 4,5, 70 kg, 1 h → 315 kcal`() {
        assertEquals(4.5 * 70, estimateCalories(muscu, 70.0, 3600), 1e-5)
    }

    @Test
    fun `vélo à vitesse par défaut (18 km par h, interpolé entre 16 et 19), 70 kg, 1 h`() {
        // Interpolation linéaire : 4,0 + (18-16)/(19-16) * (6,8-4,0) = 5,866…
        assertEquals(5.8667 * 70, estimateCalories(velo, 70.0, 3600), 0.05)
    }

    @Test
    fun `vélo très lent (10 km par h ou moins) plafonné au MET de base (3,5)`() {
        assertEquals(3.5 * 70, estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 10.0), 1e-5)
    }

    @Test
    fun `vélo très rapide (≥ 33 km par h) plafonné au MET max (15,8)`() {
        assertEquals(15.8 * 70, estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 35.0), 1e-5)
    }

    @Test
    fun `mise à l'échelle linéaire avec la durée`() {
        val k1 = estimateCalories(muscu, 80.0, 1800)
        val k2 = estimateCalories(muscu, 80.0, 3600)
        assertEquals(k1 * 2, k2, 1e-5)
    }

    @Test
    fun `durée nulle → 0 kcal`() {
        assertEquals(0.0, estimateCalories(muscu, 70.0, 0), 0.0)
    }

    @Test
    fun `avgSpeedKmh null retombe sur la valeur par défaut (18 km par h)`() {
        assertEquals(estimateCalories(velo, 70.0, 3600), estimateCalories(velo, 70.0, 3600, avgSpeedKmh = null), 0.0)
    }

    @Test
    fun `points exacts de la table MET vélo`() {
        assertEquals(4.0 * 70, estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 16.0), 1e-5)
        assertEquals(8.0 * 70, estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.5), 1e-5)
    }

    @Test
    fun `bonus dénivelé positif sur le vélo (~0,77 kcal par m pour 70 kg)`() {
        val sans = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.5)
        val avec = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.5, elevationGainM = 200.0)
        // 70 * 0,011 * 200 = 154 kcal de bonus
        assertEquals(154.0, avec - sans, 1e-5)
    }

    @Test
    fun `le dénivelé est ignoré pour la musculation`() {
        val sans = estimateCalories(muscu, 70.0, 3600)
        val avec = estimateCalories(muscu, 70.0, 3600, elevationGainM = 500.0)
        assertEquals(sans, avec, 0.0)
    }

    @Test
    fun `FC moyenne fournie → mélange MET et cardio (60 % cardio)`() {
        val sansHr = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0)
        val avecHr = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0, avgHr = 145.0, maxHr = 190.0)
        // Valeurs différentes (cardio à 76 % FCmax tire vers le bas).
        assertTrue(kotlin.math.abs(avecHr - sansHr) > 0.5)
    }

    @Test
    fun `exemple bilan - 1 h à 22 km par h, 200 m D+, FC 145, 70 kg, FCmax 190 → ~500 kcal`() {
        val kcal = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0, elevationGainM = 200.0, avgHr = 145.0, maxHr = 190.0)
        assertTrue(kcal > 480)
        assertTrue(kcal < 530)
    }

    @Test
    fun `FC max absente → 190 par défaut, FC max nulle → pas de mélange cardio`() {
        val defaut = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0, avgHr = 145.0)
        val explicite = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0, avgHr = 145.0, maxHr = 190.0)
        assertEquals(explicite, defaut, 0.0)
        val zero = estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0, avgHr = 145.0, maxHr = 0.0)
        assertEquals(estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 22.0), zero, 0.0)
        assertNotEquals(explicite, zero, 1e-5)
    }

    private val max = 180.0

    @Test
    fun `zone 1 sous 60 % FCmax`() {
        assertEquals(1, heartRateZone(100.0, max)) // ~56 %
    }

    @Test
    fun `zone 2 entre 60 et 70 %`() {
        assertEquals(2, heartRateZone(120.0, max)) // ~67 %
    }

    @Test
    fun `zone 3 entre 70 et 80 %`() {
        assertEquals(3, heartRateZone(135.0, max)) // 75 %
    }

    @Test
    fun `zone 4 entre 80 et 90 %`() {
        assertEquals(4, heartRateZone(155.0, max)) // ~86 %
    }

    @Test
    fun `zone 5 à 90 % et plus`() {
        assertEquals(5, heartRateZone(170.0, max)) // ~94 %
        assertEquals(5, heartRateZone(180.0, max))
        assertEquals(5, heartRateZone(200.0, max)) // dépassement FCmax théorique
    }

    @Test
    fun `frontière 60 % pile → zone 2`() {
        assertEquals(2, heartRateZone(108.0, max)) // 0,6 exact
    }

    @Test
    fun `ZONE_LABELS définit un libellé pour chacune des 5 zones`() {
        assertEquals(listOf(1, 2, 3, 4, 5), ZONE_LABELS.keys.sorted())
        assertEquals("Récupération", ZONE_LABELS[1])
        assertEquals("Maximal", ZONE_LABELS[5])
    }

    @Test
    fun `course - la dépense croît avec l'allure`() {
        val lent = estimateCalories(course, 70.0, 3600, avgSpeedKmh = 8.0)
        val rapide = estimateCalories(course, 70.0, 3600, avgSpeedKmh = 14.0)
        assertTrue(lent > 0)
        assertTrue(rapide > lent)
    }

    @Test
    fun `marche - moins coûteuse que la course à la même durée`() {
        assertTrue(estimateCalories(marche, 70.0, 3600, avgSpeedKmh = 5.0) < estimateCalories(course, 70.0, 3600, avgSpeedKmh = 10.0))
    }

    @Test
    fun `course - plus coûteuse que le vélo à vitesse égale (le corps se porte)`() {
        assertTrue(estimateCalories(course, 70.0, 3600, avgSpeedKmh = 12.0) > estimateCalories(velo, 70.0, 3600, avgSpeedKmh = 12.0))
    }

    @Test
    fun `extrapolation plate hors des bornes de la table`() {
        val tresLent = estimateCalories(marche, 70.0, 3600, avgSpeedKmh = 0.5)
        val borneBasse = estimateCalories(marche, 70.0, 3600, avgSpeedKmh = 3.2)
        assertEquals(borneBasse, tresLent, 1e-5)
    }

    @Test
    fun `le dénivelé compte aussi à pied`() {
        val plat = estimateCalories(course, 70.0, 3600, avgSpeedKmh = 10.0, elevationGainM = 0.0)
        val montagne = estimateCalories(course, 70.0, 3600, avgSpeedKmh = 10.0, elevationGainM = 500.0)
        assertTrue(montagne > plat)
    }

    @Test
    fun `sans vitesse fournie, chaque activité retombe sur une allure ordinaire`() {
        for (type in listOf(velo, course, marche)) assertTrue(estimateCalories(type, 70.0, 3600) > 0)
    }
}
