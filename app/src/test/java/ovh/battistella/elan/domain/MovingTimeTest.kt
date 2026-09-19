// Tests du temps en mouvement : un arrêt (vitesse nulle / position figée) n'est
// pas comptabilisé, le chrono oublié à l'arrêt est coupé, les trous plafonnés.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingTimeTest {
    private data class Pt(
        override val ts: Long,
        override val lat: Double,
        override val lon: Double,
        override val speedKmh: Double?,
    ) : TimedPoint

    /** Construit un point ; `s` = secondes depuis le départ. */
    private fun pt(s: Int, speedKmh: Double? = null, lat: Double = 48.0, lon: Double = 2.0) =
        Pt(s * 1000L, lat, lon, speedKmh)

    @Test
    fun `renvoie 0 en deçà de deux points`() {
        assertEquals(0, movingTimeSec(emptyList()))
        assertEquals(0, movingTimeSec(listOf(pt(0, 20.0))))
    }

    @Test
    fun `cumule la durée des segments en mouvement (vitesse Doppler)`() {
        assertEquals(3, movingTimeSec(listOf(pt(0, 20.0), pt(1, 20.0), pt(2, 20.0), pt(3, 20.0))))
    }

    @Test
    fun `exclut les segments à l'arrêt (vitesse nulle)`() {
        // Seuls les segments 2→ et 3→ portent une vitesse > seuil.
        assertEquals(2, movingTimeSec(listOf(pt(0, 0.0), pt(1, 0.0), pt(2, 20.0), pt(3, 20.0))))
    }

    @Test
    fun `coupe le temps oublié à l'arrêt (chrono laissé tourner)`() {
        // 10 s de roulage puis ~1 h immobile (vitesse nulle).
        val moving = listOf(pt(0, 22.0), pt(2, 22.0), pt(4, 22.0), pt(6, 22.0), pt(8, 22.0), pt(10, 22.0))
        val idle = (12..3600 step 2).map { pt(it, 0.0) }
        val total = movingTimeSec(moving + idle)
        assertTrue(total > 0)
        assertTrue(total <= 12)
    }

    @Test
    fun `plafonne un intervalle anormalement long (perte de signal)`() {
        assertEquals(30, movingTimeSec(listOf(pt(0, 20.0), pt(100, 20.0)))) // plafonné à MAX_SEGMENT_SEC
    }

    @Test
    fun `retombe sur la vitesse implicite quand la vitesse Doppler manque`() {
        // ~50 m vers le nord en 10 s ≈ 5 m/s → en mouvement.
        assertEquals(10, movingTimeSec(listOf(pt(0, null), pt(10, null, 48.000449, 2.0))))
        // ~2 m en 10 s → dérive à l'arrêt, non comptée.
        assertEquals(0, movingTimeSec(listOf(pt(0, null), pt(10, null, 48.000018, 2.0))))
    }
}
