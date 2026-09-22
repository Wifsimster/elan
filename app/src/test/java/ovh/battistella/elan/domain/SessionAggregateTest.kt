// Tests de la reconstruction des agrégats d'une sortie depuis ses points.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAggregateTest {
    private data class P(
        override val ts: Long = 0,
        override val lat: Double = 48.85,
        override val lon: Double = 2.35,
        override val altitude: Double? = null,
        override val speedKmh: Double? = null,
        override val hr: Double? = null,
        override val cadence: Double? = null,
    ) : AggPoint

    @Test
    fun `renvoie null pour moins de deux points`() {
        assertNull(aggregateFromPoints(emptyList()))
        assertNull(aggregateFromPoints(listOf(P(ts = 0))))
    }

    @Test
    fun `recalcule durée, distance et vitesse max plausibles`() {
        // ~15,7 m entre deux points à 0,0002° de longitude sur 1 s → mouvement. On roule 10 s.
        val points = (0..10).map { i ->
            P(ts = i * 1000L, lon = 2.35 + i * 0.0002, speedKmh = 30.0, hr = 140.0 + (i % 3), cadence = 85.0)
        }
        val agg = aggregateFromPoints(points)
        assertNotNull(agg)
        agg!!
        assertEquals(10, agg.durationSec)
        assertEquals(10_000L, agg.endedAt)
        assertTrue(agg.distanceM!! > 0)
        assertTrue(agg.avgSpeedKmh!! > 0)
        assertEquals(142.0, agg.maxHr!!, 0.0)
        assertEquals(85.0, agg.avgCadence!!, 0.0)
    }

    @Test
    fun `plafonne la vitesse max (fix Doppler glitché ignoré)`() {
        val agg = aggregateFromPoints(
            listOf(
                P(ts = 0, speedKmh = 25.0),
                P(ts = 1000, lon = 2.3502, speedKmh = 500.0), // aberrant
                P(ts = 2000, lon = 2.3504, speedKmh = 28.0),
            ),
        )!!
        assertEquals(28.0, agg.maxSpeedKmh!!, 0.0)
    }

    @Test
    fun `ne crédite pas la distance à l'arrêt (dérive GPS)`() {
        // Points quasi immobiles avec vitesse Doppler nulle : pas de distance.
        val agg = aggregateFromPoints(
            listOf(
                P(ts = 0, speedKmh = 0.0),
                P(ts = 1000, lat = 48.850001, speedKmh = 0.0),
                P(ts = 2000, lat = 48.850002, speedKmh = 0.0),
            ),
        )
        assertNull(agg?.distanceM)
    }

    @Test
    fun `dénivelé - crédité au-delà de 1 m de bruit, null sans altitude`() {
        val avec = aggregateFromPoints(
            listOf(P(ts = 0, altitude = 100.0), P(ts = 1000, altitude = 100.5), P(ts = 2000, altitude = 103.0), P(ts = 3000, altitude = 101.0)),
        )!!
        // 100 → 100,5 (bruit ignoré), 100,5 → 103 (+2,5), 103 → 101 (descente) : 2,5 arrondi à 3 (Math.round demi-supérieur).
        assertEquals(3.0, avec.elevationGainM!!, 0.0)
        val sans = aggregateFromPoints(listOf(P(ts = 0), P(ts = 1000)))!!
        assertNull(sans.elevationGainM)
    }

    @Test
    fun `dénivelé - l'altitude du premier point sert de référence`() {
        val agg = aggregateFromPoints(
            listOf(P(ts = 0, altitude = 100.0), P(ts = 1000, altitude = 110.0), P(ts = 2000, altitude = 110.0)),
        )!!
        assertEquals(10.0, agg.elevationGainM!!, 0.0)
    }

    @Test
    fun `accepte directement des TrackPoint`() {
        val tp = { ts: Long -> TrackPoint(0, 1, ts, 48.85, 2.35, null, 20.0, 150.0, null) }
        val agg = aggregateFromPoints(listOf(tp(0), tp(5000)))!!
        assertEquals(5, agg.durationSec)
        assertEquals(150.0, agg.avgHr!!, 0.0)
        assertNull(agg.avgCadence)
    }
}
