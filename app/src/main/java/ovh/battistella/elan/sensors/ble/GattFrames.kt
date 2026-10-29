package ovh.battistella.elan.sensors.ble

import java.util.UUID

object BleUuids {
    val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    val CSC_SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    val CSC_MEASUREMENT: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

const val CONNECT_TIMEOUT_MS = 10_000L

const val SCAN_TIMEOUT_MS = 20_000L

data class CscRaw(
    val wheelRevs: Long?,
    val wheelTime: Int?,
    val crankRevs: Int?,
    val crankTime: Int?,
)

private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

private fun ByteArray.readLE(off: Int, len: Int): Long {
    var v = 0L
    for (i in 0 until len) v = v or (u8(off + i).toLong() shl (8 * i))
    return v
}

fun parseHeartRate(bytes: ByteArray): Int? {
    if (bytes.size < 2) return null
    val is16bit = (bytes.u8(0) and 0x01) == 0x01
    val bpm = if (is16bit) {
        if (bytes.size < 3) return null
        bytes.u8(1) or (bytes.u8(2) shl 8)
    } else {
        bytes.u8(1)
    }
    return if (bpm <= 0) null else bpm
}

fun parseCsc(bytes: ByteArray): CscRaw? {
    if (bytes.isEmpty()) return null
    val flags = bytes.u8(0)
    val wheelPresent = (flags and 0x01) == 0x01
    val crankPresent = (flags and 0x02) == 0x02

    var i = 1
    var wheelRevs: Long? = null
    var wheelTime: Int? = null
    var crankRevs: Int? = null
    var crankTime: Int? = null

    if (wheelPresent) {
        if (bytes.size < i + 6) return null
        wheelRevs = bytes.readLE(i, 4)
        wheelTime = bytes.readLE(i + 4, 2).toInt()
        i += 6
    }
    if (crankPresent) {
        if (bytes.size < i + 4) return null
        crankRevs = bytes.readLE(i, 2).toInt()
        crankTime = bytes.readLE(i + 2, 2).toInt()
    }
    return CscRaw(wheelRevs, wheelTime, crankRevs, crankTime)
}
