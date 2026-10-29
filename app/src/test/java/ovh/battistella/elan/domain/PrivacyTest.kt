package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI

class PrivacyTest {
    private val degPerMetre = 180.0 / (PI * 6_371_000)

    private fun line(vararg metres: Int) = metres.map { LatLon(it * degPerMetre, 0.0) }

    @Test
    fun `options proposées - 0, 100, 200, 500`() {
        assertEquals(listOf(0, 100, 200, 500), PRIVACY_ZONE_OPTIONS)
    }

    @Test
    fun `rayon nul ou tracé trop court - inchangé`() {
        val pts = line(0, 50, 1000)
        assertSame(pts, trimPrivacyZone(pts, 0.0))
        val court = line(0, 10)
        assertSame(court, trimPrivacyZone(court, 500.0))
    }

    @Test
    fun `retire les points à moins du rayon du premier et du dernier point`() {
        val pts = line(0, 50, 150, 500, 1000, 1380, 1470, 1500)
        assertEquals(line(150, 500, 1000, 1380), trimPrivacyZone(pts, 100.0))
    }

    @Test
    fun `un point juste au-delà du rayon est conservé, juste en deçà retiré`() {
        val pts = line(0, 99, 101, 1000, 1099, 1101, 1200)
        assertEquals(line(101, 1000, 1099), trimPrivacyZone(pts, 100.0))
    }

    @Test
    fun `boucle entièrement dans la zone - tracé d'origine plutôt qu'un export vide`() {
        val pts = line(0, 20, 40, 20, 0)
        assertSame(pts, trimPrivacyZone(pts, 500.0))
    }

    @Test
    fun `il reste toujours au moins deux points`() {
        val pts = line(0, 50, 300, 550, 600)
        assertSame(pts, trimPrivacyZone(pts, 200.0))
        val ok = line(0, 50, 300, 400, 650, 700)
        assertEquals(line(300, 400), trimPrivacyZone(ok, 200.0))
    }
}
