// Tests de la répartition du temps par zone cardiaque. FC max de 200 pour des
// bornes rondes : zones à 0 / 120 / 140 / 160 / 180 bpm.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HrZonesTest {
    private val maxHr = 200.0

    private data class S(override val ts: Long, override val hr: Double?) : HrReading

    /** Série d'échantillons régulièrement espacés (une mesure par seconde). */
    private fun series(vararg hrs: Double?, stepMs: Long = 1000) = hrs.mapIndexed { i, hr -> S(i * stepMs, hr) }

    @Test
    fun `zoneBounds donne cinq zones contiguës, la dernière sans plafond`() {
        assertEquals(
            listOf(
                ZoneBound(1, 0, 119),
                ZoneBound(2, 120, 139),
                ZoneBound(3, 140, 159),
                ZoneBound(4, 160, 179),
                ZoneBound(5, 180, null),
            ),
            zoneBounds(maxHr),
        )
    }

    @Test
    fun `renvoie null sans FC max exploitable`() {
        assertNull(zoneDistribution(series(130.0, 130.0), 0.0))
    }

    @Test
    fun `renvoie null quand il n'y a pas d'intervalle à mesurer`() {
        assertNull(zoneDistribution(emptyList(), maxHr))
        assertNull(zoneDistribution(series(130.0), maxHr))
    }

    @Test
    fun `attribue chaque intervalle à la zone de la mesure qui l'ouvre`() {
        // 3 s en zone 2 (130 bpm) puis 2 s en zone 4 (165 bpm) ; la dernière mesure n'ouvre aucun intervalle.
        val d = zoneDistribution(series(130.0, 130.0, 130.0, 165.0, 165.0, 165.0), maxHr)
        assertNotNull(d)
        d!!
        assertEquals(5.0, d.totalSec, 0.0)
        assertEquals(listOf(0.0, 3.0, 0.0, 2.0, 0.0), d.slices.map { it.seconds })
        assertEquals(0.6, d.slices[1].ratio, 1e-9)
        assertEquals(0.4, d.slices[3].ratio, 1e-9)
    }

    @Test
    fun `somme des parts égale à 1`() {
        val d = zoneDistribution(series(110.0, 130.0, 150.0, 170.0, 190.0, 190.0), maxHr)!!
        assertEquals(1.0, d.slices.sumOf { it.ratio }, 1e-9)
    }

    @Test
    fun `plafonne les trous de mesure à 30 s`() {
        // Ceinture muette pendant 5 min : seules 30 s sont comptées, pas 300.
        val d = zoneDistribution(listOf(S(0, 130.0), S(300_000, 130.0)), maxHr)!!
        assertEquals(30.0, d.totalSec, 0.0)
        assertEquals(30.0, d.slices[1].seconds, 0.0)
    }

    @Test
    fun `ignore un horodatage identique ou en arrière`() {
        val d = zoneDistribution(listOf(S(5000, 130.0), S(5000, 130.0), S(1000, 130.0), S(7000, 130.0)), maxHr)!!
        // Seul l'intervalle 1000 → 7000 est valide (6 s) : aucun temps négatif compté.
        assertEquals(6.0, d.totalSec, 0.0)
    }

    @Test
    fun `coupe l'intervalle sur un point sans FC au lieu de prolonger la dernière valeur`() {
        val d = zoneDistribution(series(130.0, null, 130.0, 130.0), maxHr)!!
        assertEquals(1.0, d.totalSec, 0.0)
        assertEquals(1.0, d.slices[1].seconds, 0.0)
    }

    @Test
    fun `ignore une FC nulle ou négative (contact perdu)`() {
        assertEquals(1.0, zoneDistribution(series(130.0, 0.0, 130.0, 130.0), maxHr)!!.totalSec, 0.0)
    }

    @Test
    fun `porte le libellé et les bornes de chaque zone`() {
        val d = zoneDistribution(series(130.0, 130.0), maxHr)!!
        assertEquals("Récupération", d.slices[0].label)
        assertEquals(5, d.slices[4].zone)
        assertEquals(180, d.slices[4].minBpm)
        assertNull(d.slices[4].maxBpm)
    }

    @Test
    fun `accepte des HrSample et des TrackPoint sans conversion`() {
        val samples = listOf(HrSample(0, 130.0), HrSample(1000, 130.0))
        assertEquals(1.0, zoneDistribution(samples, maxHr)!!.totalSec, 0.0)
        val points = listOf(TrackPoint(0, 1, 0, 0.0, 0.0, null, null, 165.0, null), TrackPoint(0, 1, 2000, 0.0, 0.0, null, null, null, null))
        assertNull(zoneDistribution(points, maxHr)) // le second point coupe l'intervalle
    }

    @Test
    fun `dominantZone renvoie la zone où l'on a passé le plus de temps`() {
        val d = zoneDistribution(series(130.0, 130.0, 130.0, 165.0, 165.0), maxHr)!!
        assertEquals(2, dominantZone(d).zone)
    }

    @Test
    fun `dominantZone en cas d'égalité retient la zone la plus basse`() {
        val d = zoneDistribution(series(130.0, 130.0, 165.0, 165.0, 165.0), maxHr)!!
        assertEquals(d.slices[1].seconds, d.slices[3].seconds, 0.0)
        assertEquals(2, dominantZone(d).zone)
    }
}
