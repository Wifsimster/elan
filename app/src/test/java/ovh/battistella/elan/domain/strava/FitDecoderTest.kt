package ovh.battistella.elan.domain.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FitDecoderTest {

    private class Fit {
        val data = ByteArrayOutputStream()
        fun u8(v: Int) = apply { data.write(v and 0xff) }
        fun u16(v: Int) = apply { u8(v); u8(v shr 8) }
        fun u32(v: Long) = apply { u16((v and 0xffff).toInt()); u16(((v shr 16) and 0xffff).toInt()) }
        fun s32(v: Int) = u32(v.toLong() and 0xffffffffL)

        fun definition(local: Int, global: Int, fields: List<Triple<Int, Int, Int>>, dev: List<Int> = emptyList()) = apply {
            u8(0x40 or (if (dev.isNotEmpty()) 0x20 else 0) or local)
            u8(0)
            u8(0)
            u16(global)
            u8(fields.size)
            for ((num, size, type) in fields) { u8(num); u8(size); u8(type) }
            if (dev.isNotEmpty()) {
                u8(dev.size)
                for ((i, size) in dev.withIndex()) { u8(i); u8(size); u8(0) }
            }
        }

        fun bytes(): ByteArray {
            val body = data.toByteArray()
            val out = ByteArrayOutputStream()
            out.write(14)
            out.write(0x10)
            out.write(0x08); out.write(0x08)
            val size = body.size.toLong()
            out.write((size and 0xff).toInt()); out.write(((size shr 8) and 0xff).toInt())
            out.write(((size shr 16) and 0xff).toInt()); out.write(((size shr 24) and 0xff).toInt())
            out.write('.'.code); out.write('F'.code); out.write('I'.code); out.write('T'.code)
            out.write(0); out.write(0)
            out.write(body)
            out.write(0); out.write(0)
            return out.toByteArray()
        }
    }

    private val T0 = 1_100_000_000L
    private fun ms(fitSec: Long) = (fitSec + FitDecoder.FIT_EPOCH_OFFSET_S) * 1000
    private fun semi(deg: Double): Int = Math.round(deg / FitDecoder.SEMICIRCLE_TO_DEG).toInt()

    private val recordFields = listOf(
        Triple(253, 4, 0x86),
        Triple(0, 4, 0x85),
        Triple(1, 4, 0x85),
        Triple(2, 2, 0x84),
        Triple(3, 1, 0x02),
        Triple(4, 1, 0x02),
    )

    private fun sample(): ByteArray = Fit()
        .definition(3, 12, listOf(Triple(0, 1, 0x00)), dev = listOf(2))
        .u8(0x03).u8(1).u8(0xAA).u8(0xBB)
        .definition(0, 20, recordFields)
        .u8(0x00).u32(T0).s32(semi(48.8566)).s32(semi(2.3522)).u16((35 + 500) * 5).u8(120).u8(80)
        .u8(0x00).u32(T0 + 10).s32(semi(48.857)).s32(semi(2.3525)).u16(0xffff).u8(0xff).u8(82)
        .definition(1, 20, listOf(Triple(0, 4, 0x85), Triple(1, 4, 0x85), Triple(3, 1, 0x02)))
        .u8(0x80 or (1 shl 5) or ((T0 + 15) and 0x1f).toInt()).s32(semi(48.858)).s32(semi(2.353)).u8(130)
        .definition(2, 18, listOf(Triple(5, 1, 0x00), Triple(2, 4, 0x86), Triple(9, 4, 0x86), Triple(11, 2, 0x84)))
        .u8(0x02).u8(2).u32(T0 - 5).u32(1_234_500).u16(321)
        .bytes()

    @Test
    fun `décode en-tête, définitions, points et session`() {
        val bytes = sample()
        assertTrue(FitDecoder.isFit(bytes))
        val a = FitDecoder.parse(bytes).single()

        assertEquals(ParsedSport.CYCLING, a.sport)
        assertEquals(ms(T0 - 5), a.startedAt)
        assertEquals(12_345.0, a.distanceM)
        assertEquals(321.0, a.calories)
        assertEquals(3, a.points.size)

        val p1 = a.points[0]
        assertEquals(ms(T0), p1.ts)
        assertEquals(48.8566, p1.lat!!, 1e-6)
        assertEquals(2.3522, p1.lon!!, 1e-6)
        assertEquals(35.0, p1.ele!!, 1e-9)
        assertEquals(120.0, p1.hr)
        assertEquals(80.0, p1.cad)

        val p2 = a.points[1]
        assertEquals(ms(T0 + 10), p2.ts)
        assertNull(p2.ele)
        assertNull(p2.hr)
        assertEquals(82.0, p2.cad)

        val p3 = a.points[2]
        assertEquals(ms(T0 + 15), p3.ts)
        assertEquals(130.0, p3.hr)
        assertNull(p3.cad)
    }

    @Test
    fun `le message sport ne compte que si la session ne l'a pas donné`() {
        val onlySport = Fit()
            .definition(3, 12, listOf(Triple(0, 1, 0x00)))
            .u8(0x03).u8(2)
            .definition(0, 20, recordFields)
            .u8(0x00).u32(T0).s32(semi(1.0)).s32(semi(1.0)).u16(2500).u8(100).u8(70)
            .bytes()
        val a = FitDecoder.parse(onlySport).single()
        assertEquals(ParsedSport.CYCLING, a.sport)
        assertEquals(ms(T0), a.startedAt)
        assertNull(a.distanceM)
    }

    @Test
    fun `altitude enhanced (78) prime sur le champ standard (2)`() {
        val bytes = Fit()
            .definition(0, 20, listOf(Triple(253, 4, 0x86), Triple(2, 2, 0x84), Triple(78, 4, 0x86)))
            .u8(0x00).u32(T0).u16((10 + 500) * 5).u32(((200 + 500) * 5).toLong())
            .bytes()
        assertEquals(200.0, FitDecoder.parse(bytes).single().points[0].ele!!, 1e-9)
    }

    @Test
    fun `mapSport`() {
        assertEquals(ParsedSport.UNKNOWN, FitDecoder.mapSport(null))
        assertEquals(ParsedSport.UNKNOWN, FitDecoder.mapSport(0))
        assertEquals(ParsedSport.CYCLING, FitDecoder.mapSport(2))
        assertEquals(ParsedSport.RUNNING, FitDecoder.mapSport(1))
        assertEquals(ParsedSport.WALKING, FitDecoder.mapSport(11))
        assertEquals(ParsedSport.WALKING, FitDecoder.mapSport(17))
        assertEquals(ParsedSport.OTHER, FitDecoder.mapSport(5))
    }

    @Test
    fun `refuse un fichier trop court ou sans signature`() {
        assertEquals("FIT : fichier trop court.", assertThrows(StravaFileException::class.java) { FitDecoder.parse(ByteArray(10)) }.message)
        val bad = sample().also { it[8] = 'X'.code.toByte() }
        assertEquals("FIT : signature « .FIT » absente.", assertThrows(StravaFileException::class.java) { FitDecoder.parse(bad) }.message)
    }

    @Test
    fun `un flux tronqué ou sans définition s'arrête proprement`() {
        val full = sample()
        val truncated = full.copyOf(14 + 17 + 24 + 17 + 7)
        val a = FitDecoder.parse(truncated).single()
        assertTrue(a.points.isNotEmpty())
        val orphan = Fit().u8(0x05).u32(T0).bytes()
        assertTrue(FitDecoder.parse(orphan).single().points.isEmpty())
    }
}
