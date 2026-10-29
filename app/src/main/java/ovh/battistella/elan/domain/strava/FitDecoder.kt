package ovh.battistella.elan.domain.strava

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FitDecoder {

    const val FIT_EPOCH_OFFSET_S = 631_065_600L

    const val SEMICIRCLE_TO_DEG = 180.0 / 2147483648.0

    private const val MSG_RECORD = 20
    private const val MSG_SESSION = 18
    private const val MSG_SPORT = 12

    fun isFit(b: ByteArray): Boolean =
        b.size >= 12 && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte() &&
            b[10] == 'I'.code.toByte() && b[11] == 'T'.code.toByte()

    private class FieldDef(val num: Int, val size: Int, val baseType: Int)
    private class MsgDef(val globalNum: Int, val littleEndian: Boolean, val fields: List<FieldDef>, val devSize: Int)

    fun mapSport(sport: Long?): ParsedSport = when (sport) {
        null -> ParsedSport.UNKNOWN
        0L -> ParsedSport.UNKNOWN
        1L -> ParsedSport.RUNNING
        2L -> ParsedSport.CYCLING
        11L, 17L -> ParsedSport.WALKING
        else -> ParsedSport.OTHER
    }

    private fun fitTimeToMs(fitSeconds: Long): Long = (fitSeconds + FIT_EPOCH_OFFSET_S) * 1000

    fun parse(bytes: ByteArray): List<ParsedActivity> {
        if (bytes.size < 14) throw StravaFileException("FIT : fichier trop court.")
        val headerSize = bytes[0].toInt() and 0xff
        if (bytes.size < headerSize || !isFit(bytes)) throw StravaFileException("FIT : signature « .FIT » absente.")

        val view = ByteBuffer.wrap(bytes)
        val dataSize = view.order(ByteOrder.LITTLE_ENDIAN).getInt(4).toLong() and 0xffffffffL
        val dataEnd = minOf(headerSize + dataSize, (bytes.size - 2).toLong()).toInt()

        val defs = HashMap<Int, MsgDef>()
        val points = ArrayList<ParsedPoint>()
        var sport = ParsedSport.UNKNOWN
        var startedAt: Long? = null
        var distanceM: Double? = null
        var calories: Double? = null
        var lastTimestamp: Long? = null

        var pos = headerSize
        while (pos < dataEnd) {
            val header = bytes[pos++].toInt() and 0xff

            if ((header and 0x80) == 0 && (header and 0x40) != 0) {
                if (pos + 5 > bytes.size) break
                val localType = header and 0x0f
                val hasDev = (header and 0x20) != 0
                pos++
                val le = bytes[pos++].toInt() == 0
                val globalNum = view.order(if (le) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).getShort(pos).toInt() and 0xffff
                pos += 2
                val numFields = bytes[pos++].toInt() and 0xff
                if (pos + numFields * 3 > bytes.size) break
                val fields = ArrayList<FieldDef>(numFields)
                repeat(numFields) {
                    fields.add(
                        FieldDef(
                            num = bytes[pos].toInt() and 0xff,
                            size = bytes[pos + 1].toInt() and 0xff,
                            baseType = bytes[pos + 2].toInt() and 0xff,
                        ),
                    )
                    pos += 3
                }
                var devSize = 0
                if (hasDev) {
                    if (pos >= bytes.size) break
                    val numDev = bytes[pos++].toInt() and 0xff
                    if (pos + numDev * 3 > bytes.size) break
                    repeat(numDev) {
                        devSize += bytes[pos + 1].toInt() and 0xff
                        pos += 3
                    }
                }
                defs[localType] = MsgDef(globalNum, le, fields, devSize)
                continue
            }

            val compressed = (header and 0x80) != 0
            val localType = if (compressed) (header shr 5) and 0x03 else header and 0x0f
            val def = defs[localType] ?: break

            var compressedTs: Long? = null
            val last = lastTimestamp
            if (compressed && last != null) {
                val offset = (header and 0x1f).toLong()
                val next = if (offset >= (last and 0x1f)) (last and 0x1f.toLong().inv()) + offset
                else (last and 0x1f.toLong().inv()) + offset + 0x20
                lastTimestamp = next
                compressedTs = next
            }

            val values = HashMap<Int, Double>()
            val order = if (def.littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
            for (f in def.fields) {
                if (pos + f.size > bytes.size) {
                    pos = bytes.size
                    break
                }
                readNumber(view, pos, f.baseType, order)?.let { values[f.num] = it }
                pos += f.size
            }
            pos += def.devSize

            when (def.globalNum) {
                MSG_RECORD -> {
                    var ts: Long? = null
                    val absolute = values[253]?.toLong()
                    if (absolute != null) {
                        lastTimestamp = absolute
                        ts = fitTimeToMs(absolute)
                    } else if (compressedTs != null) {
                        ts = fitTimeToMs(compressedTs)
                    }
                    val lat = values[0]?.let { it * SEMICIRCLE_TO_DEG }
                    val lon = values[1]?.let { it * SEMICIRCLE_TO_DEG }
                    val altRaw = values[78] ?: values[2]
                    val ele = altRaw?.let { it / 5 - 500 }
                    points.add(ParsedPoint(ts, lat, lon, ele, values[3], values[4]))
                }
                MSG_SESSION -> {
                    values[5]?.let { sport = mapSport(it.toLong()) }
                    values[2]?.let { startedAt = fitTimeToMs(it.toLong()) }
                    values[9]?.let { distanceM = it / 100 }
                    values[11]?.let { calories = it }
                }
                MSG_SPORT -> if (sport == ParsedSport.UNKNOWN) {
                    values[0]?.let { sport = mapSport(it.toLong()) }
                }
            }
        }

        val firstTimed = points.firstOrNull { it.ts != null }
        return listOf(
            ParsedActivity(
                sport = sport,
                startedAt = startedAt ?: firstTimed?.ts,
                points = points,
                distanceM = distanceM,
                calories = calories,
            ),
        )
    }

    private fun readNumber(view: ByteBuffer, offset: Int, baseType: Int, order: ByteOrder): Double? {
        val b = view.order(order)
        return when (baseType) {
            0x00, 0x02, 0x0a, 0x0d -> {
                val v = b.get(offset).toInt() and 0xff
                if (v == 0xff) null else v.toDouble()
            }
            0x01 -> {
                val v = b.get(offset).toInt()
                if (v == 0x7f) null else v.toDouble()
            }
            0x84, 0x8b -> {
                val v = b.getShort(offset).toInt() and 0xffff
                if (v == 0xffff) null else v.toDouble()
            }
            0x83 -> {
                val v = b.getShort(offset).toInt()
                if (v == 0x7fff) null else v.toDouble()
            }
            0x86, 0x8c -> {
                val v = b.getInt(offset).toLong() and 0xffffffffL
                if (v == 0xffffffffL) null else v.toDouble()
            }
            0x85 -> {
                val v = b.getInt(offset)
                if (v == 0x7fffffff) null else v.toDouble()
            }
            0x88 -> {
                val v = b.getFloat(offset)
                if (v.isNaN()) null else v.toDouble()
            }
            0x89 -> {
                val v = b.getDouble(offset)
                if (v.isNaN()) null else v
            }
            else -> null
        }
    }
}
