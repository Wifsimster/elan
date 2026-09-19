// Transposition de __tests__/lib/ble.test.ts, complétée pour les trames CSC
// pédalier seul, roue + pédalier et tronquées.
package ovh.battistella.elan.sensors.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GattFramesTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // ---- parseHeartRate -------------------------------------------------

    @Test
    fun `décode une FC 8 bits (bit drapeau 0)`() {
        assertEquals(72, parseHeartRate(bytes(0x00, 72)))
    }

    @Test
    fun `décode une FC 16 bits (bit drapeau 1, little-endian)`() {
        // 0x0102 = 258
        assertEquals(258, parseHeartRate(bytes(0x01, 0x02, 0x01)))
    }

    @Test
    fun `lit l'octet de FC comme non signé`() {
        assertEquals(200, parseHeartRate(bytes(0x00, 200)))
    }

    @Test
    fun `rejette une trame 16 bits tronquée`() {
        assertNull(parseHeartRate(bytes(0x01, 0x50)))
    }

    @Test
    fun `traite une FC de 0 (contact perdu) comme absente`() {
        assertNull(parseHeartRate(bytes(0x00, 0)))
        assertNull(parseHeartRate(bytes(0x01, 0, 0)))
    }

    @Test
    fun `renvoie null pour une valeur absente ou trop courte`() {
        assertNull(parseHeartRate(ByteArray(0)))
        assertNull(parseHeartRate(bytes(0x00)))
    }

    @Test
    fun `ignore les autres drapeaux (contact, énergie, RR)`() {
        // 0x16 : contact détecté + énergie + RR présents, valeur 8 bits.
        assertEquals(133, parseHeartRate(bytes(0x16, 133, 0x10, 0x00, 0x34, 0x03)))
    }

    // ---- parseCsc -------------------------------------------------------

    @Test
    fun `décode les révolutions et temps de roue (bit drapeau 0)`() {
        // flags=0x01 (roue présente), wheelRevs=1 (uint32 LE), wheelTime=1024 (uint16 LE)
        val raw = parseCsc(bytes(0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x04))
        assertNotNull(raw)
        assertEquals(1L, raw!!.wheelRevs)
        assertEquals(1024, raw.wheelTime)
        assertNull(raw.crankRevs)
        assertNull(raw.crankTime)
    }

    @Test
    fun `décode le pédalier seul (bit drapeau 1)`() {
        // flags=0x02, crankRevs=0x0102=258, crankTime=0x0400=1024
        val raw = parseCsc(bytes(0x02, 0x02, 0x01, 0x00, 0x04))
        assertNotNull(raw)
        assertNull(raw!!.wheelRevs)
        assertNull(raw.wheelTime)
        assertEquals(258, raw.crankRevs)
        assertEquals(1024, raw.crankTime)
    }

    @Test
    fun `décode roue et pédalier ensemble`() {
        val raw = parseCsc(bytes(0x03, 0x10, 0x00, 0x00, 0x00, 0x00, 0x08, 0x05, 0x00, 0x00, 0x02))
        assertNotNull(raw)
        assertEquals(16L, raw!!.wheelRevs)
        assertEquals(2048, raw.wheelTime)
        assertEquals(5, raw.crankRevs)
        assertEquals(512, raw.crankTime)
    }

    @Test
    fun `lit les compteurs comme non signés jusqu'aux bornes`() {
        val raw = parseCsc(bytes(0x03, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF))
        assertEquals(0xFFFF_FFFFL, raw!!.wheelRevs)
        assertEquals(0xFFFF, raw.wheelTime)
        assertEquals(0xFFFF, raw.crankRevs)
        assertEquals(0xFFFF, raw.crankTime)
    }

    @Test
    fun `renvoie null pour une trame roue tronquée`() {
        assertNull(parseCsc(bytes(0x01, 0x01, 0x00)))
    }

    @Test
    fun `renvoie null pour une trame pédalier tronquée`() {
        assertNull(parseCsc(bytes(0x02, 0x01, 0x00, 0x00)))
        // roue complète mais pédalier annoncé et absent
        assertNull(parseCsc(bytes(0x03, 0x01, 0x00, 0x00, 0x00, 0x00, 0x04, 0x01)))
    }

    @Test
    fun `renvoie null pour une trame vide et des compteurs absents sans drapeau`() {
        assertNull(parseCsc(ByteArray(0)))
        assertEquals(CscRaw(null, null, null, null), parseCsc(bytes(0x00)))
    }

    @Test
    fun `expose les UUID 128 bits des profils`() {
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", BleUuids.HEART_RATE_SERVICE.toString())
        assertEquals("00002a37-0000-1000-8000-00805f9b34fb", BleUuids.HEART_RATE_MEASUREMENT.toString())
        assertEquals("00001816-0000-1000-8000-00805f9b34fb", BleUuids.CSC_SERVICE.toString())
        assertEquals("00002a5b-0000-1000-8000-00805f9b34fb", BleUuids.CSC_MEASUREMENT.toString())
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", BleUuids.CCCD.toString())
    }
}
