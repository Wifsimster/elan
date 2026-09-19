// Tests pour les calculs géographiques (haversine + décimation).
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private data class TsPoint(override val lat: Double, override val lon: Double, val ts: Long) : GeoPoint

    @Test
    fun `distance d'un point à lui-même = 0`() {
        val p = LatLon(48.8566, 2.3522)
        assertEquals(0.0, haversineMeters(p, p), 0.0)
    }

    @Test
    fun `symétrique entre A et B`() {
        val a = LatLon(48.8566, 2.3522) // Paris
        val b = LatLon(45.7640, 4.8357) // Lyon
        assertEquals(haversineMeters(b, a), haversineMeters(a, b), 1e-6)
    }

    @Test
    fun `Paris vers Lyon ≈ 392 km (± 5 km)`() {
        val d = haversineMeters(LatLon(48.8566, 2.3522), LatLon(45.764, 4.8357))
        assertTrue(d > 387_000)
        assertTrue(d < 397_000)
    }

    @Test
    fun `un degré de latitude à l'équateur ≈ 111 km`() {
        val d = haversineMeters(LatLon(0.0, 0.0), LatLon(1.0, 0.0))
        assertTrue(d > 110_000)
        assertTrue(d < 112_000)
    }

    @Test
    fun `résultat strictement positif pour deux points distincts`() {
        assertTrue(haversineMeters(LatLon(10.0, 10.0), LatLon(10.0001, 10.0001)) > 0)
    }

    @Test
    fun `decimateByDistance renvoie tel quel si ≤ 2 points`() {
        val pts = listOf(LatLon(0.0, 0.0))
        assertEquals(pts, decimateByDistance(pts, 100.0))
        val pts2 = listOf(LatLon(0.0, 0.0), LatLon(1.0, 0.0))
        assertEquals(pts2, decimateByDistance(pts2, 100.0))
    }

    @Test
    fun `préserve toujours le premier et le dernier point`() {
        val pts = listOf(LatLon(0.0, 0.0), LatLon(0.000_001, 0.0), LatLon(0.000_002, 0.0), LatLon(0.000_003, 0.0))
        val out = decimateByDistance(pts, 10_000.0)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
    }

    @Test
    fun `élimine les points trop proches selon le seuil`() {
        // Quatre points à 1 mètre d'écart : on garde le 1er et le dernier.
        val pts = listOf(LatLon(0.0, 0.0), LatLon(0.000_009, 0.0), LatLon(0.000_018, 0.0), LatLon(0.000_027, 0.0))
        assertEquals(2, decimateByDistance(pts, 100.0).size)
    }

    @Test
    fun `conserve les points suffisamment éloignés`() {
        // Trois points séparés de ~111 m : avec un seuil de 50 m, tous sont gardés.
        val pts = listOf(LatLon(0.0, 0.0), LatLon(0.001, 0.0), LatLon(0.002, 0.0))
        assertEquals(3, decimateByDistance(pts, 50.0).size)
    }

    @Test
    fun `préserve les métadonnées des points (générique)`() {
        val pts = listOf(TsPoint(0.0, 0.0, 1), TsPoint(1.0, 0.0, 2), TsPoint(2.0, 0.0, 3))
        val out = decimateByDistance(pts, 10.0)
        assertEquals(1L, out.first().ts)
        assertEquals(3L, out.last().ts)
    }
}
