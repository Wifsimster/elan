// Tests pour la projection géo → viewBox (cadrage + ratio préservé).
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProjectionTest {
    private val points = listOf(LatLon(48.85, 2.34), LatLon(48.86, 2.35), LatLon(48.87, 2.36))

    @Test
    fun `renvoie les dimensions transmises`() {
        val p = createProjection(points, width = 200.0, height = 100.0)
        assertEquals(200.0, p.width, 0.0)
        assertEquals(100.0, p.height, 0.0)
    }

    @Test
    fun `le coin nord-ouest (maxLat, minLon) tombe dans le viewBox`() {
        val proj = createProjection(points, width = 300.0, height = 200.0, pad = 20.0)
        val (x, y) = proj.project(LatLon(48.87, 2.34))
        assertTrue(x >= 0)
        assertTrue(y >= 0)
        assertTrue(x <= 300)
        assertTrue(y <= 200)
    }

    @Test
    fun `inverse l'axe Y - le point le plus au nord a un y inférieur au point sud`() {
        val proj = createProjection(points, width = 300.0, height = 200.0)
        val yNord = proj.project(LatLon(48.87, 2.35)).y
        val ySud = proj.project(LatLon(48.85, 2.35)).y
        assertTrue(yNord < ySud)
    }

    @Test
    fun `axe X croissant avec la longitude (est → droite)`() {
        val proj = createProjection(points, width = 300.0, height = 200.0)
        val xOuest = proj.project(LatLon(48.86, 2.34)).x
        val xEst = proj.project(LatLon(48.86, 2.36)).x
        assertTrue(xEst > xOuest)
    }

    @Test
    fun `tous les points projetés tiennent dans le viewBox (padding respecté)`() {
        val w = 400.0
        val h = 300.0
        val pad = 30.0
        val proj = createProjection(points, width = w, height = h, pad = pad)
        for (pt in points) {
            val (x, y) = proj.project(pt)
            assertTrue(x >= pad - 0.01)
            assertTrue(x <= w - pad + 0.01)
            assertTrue(y >= pad - 0.01)
            assertTrue(y <= h - pad + 0.01)
        }
    }

    @Test
    fun `tracé dégénéré (un seul point répété) ne lève pas et reste cadré`() {
        val same = listOf(LatLon(48.85, 2.34), LatLon(48.85, 2.34))
        val proj = createProjection(same, width = 100.0, height = 100.0)
        val (x, y) = proj.project(same[0])
        assertTrue(x.isFinite())
        assertTrue(y.isFinite())
    }

    @Test
    fun `projection identique pour deux points identiques (déterminisme)`() {
        val proj = createProjection(points, width = 200.0, height = 200.0)
        assertEquals(proj.project(LatLon(48.86, 2.35)), proj.project(LatLon(48.86, 2.35)))
    }

    @Test
    fun `metersPerUnit - 111 320 m par degré de latitude divisé par l'échelle`() {
        // 0,02° de latitude sur 200 − 2×40 = 120 unités → scale = 6000 u/° → 18,55 m/u.
        val proj = createProjection(points, width = 1000.0, height = 200.0)
        assertEquals(111_320 / 6000.0, proj.metersPerUnit, 1e-6)
    }
}
