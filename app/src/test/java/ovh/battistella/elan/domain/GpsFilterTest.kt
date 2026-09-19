// Tests du filtre GPS : antiméridien (repris de la suite Jest), puis chaque
// étage du pipeline — porte de précision, téléportation, Kalman, ancre de
// distance, hystérésis de dénivelé et purge de la fenêtre d'altitude.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GpsFilterTest {
    private fun fix(
        ts: Long = 0,
        lat: Double = 0.0,
        lon: Double = 0.0,
        altitude: Double? = null,
        accuracy: Double? = 5.0,
        altitudeAccuracy: Double? = null,
        speed: Double? = 5.0,
    ) = GpsFix(ts, lat, lon, altitude, accuracy, altitudeAccuracy, speed)

    /** 1 m de latitude en degrés, pour le rayon terrestre de `haversineMeters` (6 371 km). */
    private val metreLat = 180.0 / (Math.PI * 6_371_000)

    @Test
    fun `ne fait pas sauter l'estimation à l'autre bout du globe en croisant ±180°`() {
        val c = GpsConsolidator()
        c.process(fix(ts = 0, lat = 0.0, lon = 179.9999))
        val r = c.process(fix(ts = 1000, lat = 0.0, lon = -179.9999))
        assertNotNull(r.point)
        // L'estimation doit rester au voisinage de l'antiméridien (|lon| ~180),
        // pas retomber vers 0 (ce que produisait une innovation non enroulée).
        assertTrue(abs(r.point!!.lon) > 179)
    }

    @Test
    fun `porte de précision - rejette au-delà de 50 m, accepte 50 m pile et une précision inconnue`() {
        val c = GpsConsolidator()
        assertNull(c.process(fix(ts = 0, accuracy = 50.1)).point)
        assertNotNull(c.process(fix(ts = 1000, accuracy = 50.0)).point)
        // Précision absente → 15 m supposés, fix accepté.
        assertNotNull(c.process(fix(ts = 2000, accuracy = null)).point)
    }

    @Test
    fun `téléportation - rejette une vitesse implicite au-delà de 30 m par s`() {
        val c = GpsConsolidator()
        c.process(fix(ts = 0))
        // 40 m en 1 s : impossible à vélo.
        assertNull(c.process(fix(ts = 1000, lat = 40 * metreLat)).point)
        // Le même saut étalé sur 2 s (20 m/s) redevient plausible.
        assertNotNull(c.process(fix(ts = 2000, lat = 40 * metreLat)).point)
    }

    @Test
    fun `téléportation - rejette un horodatage égal ou antérieur au dernier point accepté`() {
        val c = GpsConsolidator()
        c.process(fix(ts = 1000))
        assertNull(c.process(fix(ts = 1000)).point)
        assertNull(c.process(fix(ts = 500)).point)
        assertNotNull(c.process(fix(ts = 1001)).point)
    }

    @Test
    fun `Kalman - le premier fix est repris tel quel`() {
        val r = GpsConsolidator().process(fix(ts = 0, lat = 48.85, lon = 2.35))
        assertEquals(48.85, r.point!!.lat, 0.0)
        assertEquals(2.35, r.point!!.lon, 0.0)
    }

    @Test
    fun `Kalman - un saut dans le rayon de précision n'est suivi que partiellement`() {
        val c = GpsConsolidator()
        c.process(fix(ts = 0, accuracy = 10.0, speed = 0.0))
        // 8 m plus au nord, à l'arrêt : le gain est < 1, l'estimation reste entre les deux.
        val r = c.process(fix(ts = 1000, lat = 8 * metreLat, accuracy = 10.0, speed = 0.0))
        val lat = r.point!!.lat
        assertTrue(lat > 0)
        assertTrue(lat < 8 * metreLat)
    }

    @Test
    fun `Kalman - converge vers une position stable répétée`() {
        val c = GpsConsolidator()
        c.process(fix(ts = 0, accuracy = 10.0, speed = 0.0))
        var lat = 0.0
        for (i in 1..30) {
            lat = c.process(fix(ts = i * 1000L, lat = 8 * metreLat, accuracy = 10.0, speed = 0.0)).point!!.lat
        }
        // À moins de 20 cm de la nouvelle position après 30 s.
        assertTrue(abs(lat - 8 * metreLat) < 0.2 * metreLat)
    }

    @Test
    fun `Kalman - un fix imprécis pèse moins qu'un fix précis`() {
        fun jumpWith(accuracy: Double): Double {
            val c = GpsConsolidator()
            c.process(fix(ts = 0, accuracy = 5.0, speed = 0.0))
            return c.process(fix(ts = 1000, lat = 10 * metreLat, accuracy = accuracy, speed = 0.0)).point!!.lat
        }
        assertTrue(jumpWith(40.0) < jumpWith(5.0))
    }

    @Test
    fun `ancre de distance - rien n'est crédité sous 5 m, puis le delta complet`() {
        val c = GpsConsolidator()
        // Précision 1 m : le Kalman suit quasiment la mesure, les distances sont lisibles.
        val acc = 1.0
        assertEquals(0.0, c.process(fix(ts = 0, accuracy = acc)).deltaDistanceM, 0.0)
        // 3 m : sous le seuil, l'ancre ne bouge pas.
        assertEquals(0.0, c.process(fix(ts = 1000, lat = 3 * metreLat, accuracy = acc)).deltaDistanceM, 0.0)
        // 6 m depuis l'ancre (pas 3 m depuis le point précédent) : delta ≈ 6 m.
        val d = c.process(fix(ts = 2000, lat = 6 * metreLat, accuracy = acc)).deltaDistanceM
        assertTrue("delta=$d", d >= 5.0)
        assertTrue("delta=$d", d < 7.0)
    }

    @Test
    fun `ancre de distance - à l'arrêt (Doppler sous 0,7 m par s) la dérive n'est pas créditée`() {
        val c = GpsConsolidator()
        c.process(fix(ts = 0, accuracy = 1.0, speed = 0.0))
        val r = c.process(fix(ts = 1000, lat = 20 * metreLat, accuracy = 1.0, speed = 0.5))
        assertEquals(0.0, r.deltaDistanceM, 0.0)
        // Vitesse inconnue : on suppose en mouvement.
        val r2 = c.process(fix(ts = 2000, lat = 20 * metreLat, accuracy = 1.0, speed = null))
        assertTrue(r2.deltaDistanceM > 5.0)
    }

    @Test
    fun `vitesse - convertie en km par h, absente si négative ou inconnue`() {
        val c = GpsConsolidator()
        assertEquals(18.0, c.process(fix(ts = 0, speed = 5.0)).point!!.speedKmh!!, 1e-9)
        assertNull(c.process(fix(ts = 1000, speed = -1.0)).point!!.speedKmh)
        assertNull(c.process(fix(ts = 2000, speed = null)).point!!.speedKmh)
    }

    @Test
    fun `altitude - ignorée quand la précision verticale dépasse 12 m`() {
        val c = GpsConsolidator()
        assertNull(c.process(fix(ts = 0, altitude = 100.0, altitudeAccuracy = 12.1)).point!!.altitude)
        assertEquals(100.0, c.process(fix(ts = 1000, altitude = 100.0, altitudeAccuracy = 12.0)).point!!.altitude!!, 0.0)
    }

    @Test
    fun `altitude - médiane glissante sur 7 échantillons, robuste à un pic isolé`() {
        val c = GpsConsolidator()
        var ts = 0L
        repeat(6) { c.process(fix(ts = ts++ * 1000, altitude = 100.0, speed = 0.0)) }
        val spike = c.process(fix(ts = ts++ * 1000, altitude = 400.0, speed = 0.0))
        assertEquals(100.0, spike.point!!.altitude!!, 0.0)
        assertEquals(0.0, spike.deltaElevationGainM, 0.0)
    }

    /** Alimente `n` fixes consécutifs à l'altitude `alt` et cumule le dénivelé crédité. */
    private class AltFeeder {
        val c = GpsConsolidator()
        var ts = 0L
        fun feed(alt: Double, n: Int = 7): Double {
            var gain = 0.0
            repeat(n) {
                ts += 1000
                gain += c.process(GpsFix(ts, 0.0, 0.0, alt, 5.0, null, 0.0)).deltaElevationGainM
            }
            return gain
        }
    }

    @Test
    fun `hystérésis - une montée n'est créditée qu'au-delà de 5 m, puis en totalité`() {
        val f = AltFeeder()
        assertEquals(0.0, f.feed(100.0), 0.0) // ancre à 100
        assertEquals(0.0, f.feed(104.0), 0.0) // +4 : dans l'hystérésis
        assertEquals(0.0, f.feed(105.0), 0.0) // +5 pile : pas strictement au-delà
        // +6 depuis l'ancre : 6 m crédités (et non 1 m au-delà du seuil).
        assertEquals(6.0, f.feed(106.0), 1e-9)
    }

    @Test
    fun `hystérésis - symétrique en descente, l'oscillation du bruit ne fabrique pas de dénivelé`() {
        val f = AltFeeder()
        f.feed(100.0)
        f.feed(106.0) // ancre à 106
        assertEquals(0.0, f.feed(102.0), 0.0) // −4 : l'ancre reste à 106
        assertEquals(0.0, f.feed(106.0), 0.0) // retour à 106 : rien à créditer
        assertEquals(0.0, f.feed(101.0), 0.0) // −5 pile : l'ancre reste à 106
        assertEquals(0.0, f.feed(106.0), 0.0)
        assertEquals(0.0, f.feed(100.0), 0.0) // −6 : l'ancre descend à 100
        assertEquals(0.0, f.feed(104.0), 0.0) // +4 depuis 100 : dans l'hystérésis
        assertEquals(6.0, f.feed(106.0), 1e-9) // +6 depuis 100
    }

    @Test
    fun `fenêtre d'altitude - purgée après un trou de plus de 30 s`() {
        val c = GpsConsolidator()
        var ts = 0L
        repeat(7) { ts += 1000; c.process(fix(ts = ts, altitude = 100.0, speed = 0.0)) }
        // 31 s plus tard, 200 m : la fenêtre repart de zéro, l'altitude lissée est 200.
        val r = c.process(fix(ts = ts + 31_000, altitude = 200.0, speed = 0.0))
        assertEquals(200.0, r.point!!.altitude!!, 0.0)
        assertEquals(100.0, r.deltaElevationGainM, 1e-9)
    }

    @Test
    fun `fenêtre d'altitude - conservée pour un trou de 30 s exactement`() {
        val c = GpsConsolidator()
        var ts = 0L
        repeat(7) { ts += 1000; c.process(fix(ts = ts, altitude = 100.0, speed = 0.0)) }
        val r = c.process(fix(ts = ts + 30_000, altitude = 200.0, speed = 0.0))
        // Médiane de [100 ×6, 200] = 100 : le pic est encore lissé.
        assertEquals(100.0, r.point!!.altitude!!, 0.0)
        assertEquals(0.0, r.deltaElevationGainM, 0.0)
    }
}
