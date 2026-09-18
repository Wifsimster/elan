// Tests des séries de graphes : abscisse en km cumulés, points sans donnée
// ignorés, sous-échantillonnage à 140 points + dernier.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDataTest {
    private fun tp(i: Int, speedKmh: Double? = null, altitude: Double? = null, hr: Double? = null) =
        TrackPoint(id = i.toLong(), sessionId = 1, ts = i * 1000L, lat = 48.0 + i * 0.001, lon = 2.0, altitude = altitude, speedKmh = speedKmh, hr = hr, cadence = null)

    @Test
    fun `série vide sans point`() {
        assertTrue(speedProfile(emptyList()).isEmpty())
        assertTrue(elevationProfile(emptyList()).isEmpty())
        assertTrue(hrProfile(emptyList()).isEmpty())
    }

    @Test
    fun `abscisse = distance cumulée en km, à partir de 0`() {
        val pts = listOf(tp(0, speedKmh = 10.0), tp(1, speedKmh = 12.0), tp(2, speedKmh = 14.0))
        val s = speedProfile(pts)
        assertEquals(3, s.size)
        assertEquals(0.0, s[0].x, 0.0)
        // 0,001° de latitude ≈ 111 m par pas.
        assertEquals(0.1113, s[1].x, 0.001)
        assertEquals(2 * s[1].x, s[2].x, 1e-9)
        assertEquals(listOf(10.0, 12.0, 14.0), s.map { it.y })
    }

    @Test
    fun `les points sans donnée sont ignorés sans décaler l'abscisse`() {
        val pts = listOf(tp(0, hr = 120.0), tp(1), tp(2, hr = 130.0))
        val s = hrProfile(pts)
        assertEquals(2, s.size)
        assertEquals(130.0, s[1].y, 0.0)
        assertEquals(2 * 0.1113, s[1].x, 0.001) // abscisse du 3e point, pas du 2e
    }

    @Test
    fun `la vitesse négative est ramenée à 0`() {
        assertEquals(listOf(0.0), speedProfile(listOf(tp(0, speedKmh = -3.0))).map { it.y })
    }

    @Test
    fun `elevationProfile lit l'altitude`() {
        assertEquals(listOf(100.0, 105.0), elevationProfile(listOf(tp(0, altitude = 100.0), tp(1, altitude = 105.0))).map { it.y })
    }

    @Test
    fun `sous-échantillonne à 140 points plus le dernier au-delà de 140`() {
        val pts = (0 until 300).map { tp(it, hr = it.toDouble()) }
        val s = hrProfile(pts)
        assertEquals(141, s.size)
        assertEquals(0.0, s[0].y, 0.0)
        assertEquals(299.0, s.last().y, 0.0)
        // pas = 300/140 : i=1 → index 2, i=139 → index 297.
        assertEquals(2.0, s[1].y, 0.0)
        assertEquals(297.0, s[139].y, 0.0)
    }

    @Test
    fun `140 points pile ne sont pas sous-échantillonnés`() {
        assertEquals(140, hrProfile((0 until 140).map { tp(it, hr = 100.0) }).size)
    }
}
