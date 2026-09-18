// Tests des helpers d'échantillons capteurs : agrégation FC/cadence,
// down-sampling des paliers et appariement temporel par dichotomie.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamplesTest {
    private data class V(override val ts: Long, val v: Int) : Timestamped

    @Test
    fun `meanMax renvoie null sur une série vide (pas de faux 0)`() {
        assertEquals(MeanMax(null, null), meanMax(emptyList()))
    }

    @Test
    fun `meanMax arrondit la moyenne et garde le max réel`() {
        assertEquals(MeanMax(101.0, 102.0), meanMax(listOf(100.0, 101.0, 102.0)))
        // moyenne 100.5 -> arrondi 101 (arrondi mathématique)
        assertEquals(MeanMax(101.0, 101.0), meanMax(listOf(100.0, 101.0)))
    }

    @Test
    fun `meanMax gère une valeur unique`() {
        assertEquals(MeanMax(142.0, 142.0), meanMax(listOf(142.0)))
    }

    @Test
    fun `summarizeHr null sans échantillon`() {
        assertEquals(HrSummary(null, null), summarizeHr(emptyList()))
    }

    @Test
    fun `summarizeHr moyenne et max FC`() {
        val s = listOf(HrSample(0, 120.0), HrSample(1000, 130.0), HrSample(2000, 140.0))
        assertEquals(HrSummary(130.0, 140.0), summarizeHr(s))
    }

    @Test
    fun `summarizeCadence null sans échantillon`() {
        assertEquals(CadenceSummary(null, null), summarizeCadence(emptyList()))
    }

    @Test
    fun `summarizeCadence exclut la roue libre (0) de la moyenne mais pas du max`() {
        // moyenne sur {90,100} = 95 ; max sur toutes = 100
        assertEquals(CadenceSummary(95.0, 100.0), summarizeCadence(listOf(0.0, 90.0, 0.0, 100.0, 0.0)))
    }

    @Test
    fun `summarizeCadence que des 0 - moyenne null, max null`() {
        assertEquals(CadenceSummary(null, null), summarizeCadence(listOf(0.0, 0.0, 0.0)))
    }

    @Test
    fun `summarizeCadence arrondit la moyenne en mouvement`() {
        assertEquals(CadenceSummary(81.0, 81.0), summarizeCadence(listOf(80.0, 81.0)))
    }

    @Test
    fun `pushDownsampled ignore une valeur identique reçue trop tôt`() {
        val buf = ArrayList<HrSample>()
        pushDownsampled(buf, HrSample(0, 120.0), { it.hr })
        pushDownsampled(buf, HrSample(500, 120.0), { it.hr }) // même valeur, < 1 s
        assertEquals(1, buf.size)
    }

    @Test
    fun `pushDownsampled garde une valeur identique reçue après minGapMs`() {
        val buf = ArrayList<HrSample>()
        pushDownsampled(buf, HrSample(0, 120.0), { it.hr })
        pushDownsampled(buf, HrSample(1000, 120.0), { it.hr }) // même valeur, >= 1 s
        assertEquals(2, buf.size)
    }

    @Test
    fun `pushDownsampled garde toujours une valeur différente, même rapprochée`() {
        val buf = ArrayList<HrSample>()
        pushDownsampled(buf, HrSample(0, 120.0), { it.hr })
        pushDownsampled(buf, HrSample(100, 121.0), { it.hr })
        assertEquals(2, buf.size)
    }

    @Test
    fun `pushDownsampled respecte un minGapMs personnalisé`() {
        val buf = ArrayList<V>()
        pushDownsampled(buf, V(0, 5), { it.v }, 5000)
        pushDownsampled(buf, V(3000, 5), { it.v }, 5000) // < 5 s, ignoré
        pushDownsampled(buf, V(6000, 5), { it.v }, 5000) // >= 5 s, gardé
        assertEquals(listOf(0L, 6000L), buf.map { it.ts })
    }

    private val samples = listOf(HrSample(0, 100.0), HrSample(1000, 110.0), HrSample(2000, 120.0), HrSample(5000, 150.0))

    @Test
    fun `nearestSample renvoie null sans échantillon`() {
        assertNull(nearestSample(emptyList<HrSample>(), 1234, { it.hr }))
    }

    @Test
    fun `nearestSample trouve l'échantillon exact`() {
        assertEquals(110.0, nearestSample(samples, 1000, { it.hr }))
    }

    @Test
    fun `nearestSample choisit le plus proche entre deux voisins`() {
        assertEquals(110.0, nearestSample(samples, 1400, { it.hr })) // plus près de 1000
        assertEquals(120.0, nearestSample(samples, 1600, { it.hr })) // plus près de 2000
    }

    @Test
    fun `nearestSample borne avant le premier et après le dernier`() {
        assertEquals(100.0, nearestSample(samples, -500, { it.hr }))
        assertEquals(150.0, nearestSample(samples, 4900, { it.hr }))
    }

    @Test
    fun `nearestSample renvoie null au-delà de la tolérance`() {
        // 3000 est à 1 s de 2000 et 2 s de 5000 -> plus proche 2000 (diff 1000 <= 10 s)
        assertEquals(120.0, nearestSample(samples, 3000, { it.hr }))
        // tolérance réduite à 500 ms : 3000 est à 1 s du plus proche -> null
        assertNull(nearestSample(samples, 3000, { it.hr }, 500))
    }
}
