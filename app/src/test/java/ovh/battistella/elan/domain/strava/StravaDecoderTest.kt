package ovh.battistella.elan.domain.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class StravaDecoderTest {

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private val gpx = GpxTcxParserTest.GPX_SAMPLE.toByteArray(Charsets.UTF_8)

    @Test
    fun `XML brut → parseur GPX ou TCX`() {
        assertEquals(StravaFormat.GPX, StravaDecoder.decode(gpx).format)
        assertEquals(StravaFormat.TCX, StravaDecoder.decode(GpxTcxParserTest.TCX_SAMPLE.toByteArray()).format)
        assertFalse(StravaDecoder.isGzip(gpx))
    }

    @Test
    fun `aller-retour gzip`() {
        val zipped = gzip(gpx)
        assertTrue(StravaDecoder.isGzip(zipped))
        assertEquals(gpx.size.toLong(), StravaDecoder.declaredGunzipSize(zipped))
        val r = StravaDecoder.decode(zipped)
        assertEquals(StravaFormat.GPX, r.format)
        assertEquals(3, r.activities[0].points.size)
    }

    @Test
    fun `une bombe gzip annoncée est refusée avant décompression`() {
        val zipped = gzip(gpx)
        // ISIZE forgé à 4 Go - 1 : refus sur la taille annoncée, sans inflater.
        val n = zipped.size
        zipped[n - 4] = 0xff.toByte(); zipped[n - 3] = 0xff.toByte(); zipped[n - 2] = 0xff.toByte(); zipped[n - 1] = 0xff.toByte()
        assertEquals(0xffffffffL, StravaDecoder.declaredGunzipSize(zipped))
        val e = assertThrows(StravaFileException::class.java) { StravaDecoder.decode(zipped) }
        assertEquals("Fichier décompressé trop volumineux.", e.message)
    }

    @Test
    fun `une bombe gzip à l'ISIZE menteur est refusée pendant la décompression`() {
        // 61 Mo de zéros compressent en ~60 Ko ; ISIZE = 61 Mo mod 2^32, on le
        // falsifie à 1 pour passer le premier contrôle.
        val big = ByteArray(61 * 1024 * 1024)
        val zipped = gzip(big)
        val n = zipped.size
        zipped[n - 4] = 1; zipped[n - 3] = 0; zipped[n - 2] = 0; zipped[n - 1] = 0
        val e = assertThrows(StravaFileException::class.java) { StravaDecoder.decode(zipped) }
        assertEquals("Fichier décompressé trop volumineux.", e.message)
    }

    @Test
    fun `gzip corrompu → archive illisible`() {
        val zipped = gzip(gpx).copyOf(20)
        assertThrows(StravaFileException::class.java) { StravaDecoder.decode(zipped) }
    }

    @Test
    fun `signature FIT → décodeur binaire`() {
        val fit = ByteArray(16).also {
            it[0] = 14; it[8] = '.'.code.toByte(); it[9] = 'F'.code.toByte(); it[10] = 'I'.code.toByte(); it[11] = 'T'.code.toByte()
        }
        val r = StravaDecoder.decode(fit)
        assertEquals(StravaFormat.FIT, r.format)
        assertTrue(r.activities.single().points.isEmpty())
        assertEquals(StravaFormat.FIT, StravaDecoder.decode(gzip(fit)).format)
    }
}
