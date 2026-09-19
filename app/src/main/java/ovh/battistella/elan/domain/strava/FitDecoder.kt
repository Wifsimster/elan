// Décodeur FIT (Flexible and Interoperable Data Transfer) — port de
// `src/lib/strava/fit.ts`. C'est le format binaire produit par les capteurs
// Garmin/Wahoo/Coros et renvoyé par l'« export original » et l'export en masse
// de Strava (souvent compressé en .gz, décompressé en amont par `StravaDecoder`).
//
// On ne décode que ce dont l'app a besoin (messages `record` + résumé
// `session` + `sport`) et on renvoie la même forme `ParsedActivity` que le
// parseur GPX/TCX, pour que la normalisation aval soit identique.
//
// Référence : FIT Protocol — en-tête (12/14 o) + enregistrements (définition /
// données) + CRC final. Positions en semicercles, horodatage en secondes depuis
// l'époque FIT (1989-12-31), altitude à l'échelle.
package ovh.battistella.elan.domain.strava

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FitDecoder {

    /** Décalage entre l'époque FIT (1989-12-31T00:00:00Z) et l'époque Unix, en s. */
    const val FIT_EPOCH_OFFSET_S = 631_065_600L

    /** Conversion semicercles → degrés : 180 / 2^31. */
    const val SEMICIRCLE_TO_DEG = 180.0 / 2147483648.0

    /** Numéros de message globaux exploités. */
    private const val MSG_RECORD = 20
    private const val MSG_SESSION = 18
    private const val MSG_SPORT = 12

    /** Vrai si les octets portent la signature « .FIT » aux positions 8–11 de l'en-tête. */
    fun isFit(b: ByteArray): Boolean =
        b.size >= 12 && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte() &&
            b[10] == 'I'.code.toByte() && b[11] == 'T'.code.toByte()

    private class FieldDef(val num: Int, val size: Int, val baseType: Int)
    private class MsgDef(val globalNum: Int, val littleEndian: Boolean, val fields: List<FieldDef>, val devSize: Int)

    /** Mappe l'enum `sport` FIT vers la catégorie du domaine. */
    fun mapSport(sport: Long?): ParsedSport = when (sport) {
        null -> ParsedSport.UNKNOWN
        2L -> ParsedSport.CYCLING // 2 = cycling
        0L -> ParsedSport.UNKNOWN // 0 = generic
        else -> ParsedSport.OTHER
    }

    /** Convertit un horodatage FIT (s depuis l'époque FIT) en ms epoch Unix. */
    private fun fitTimeToMs(fitSeconds: Long): Long = (fitSeconds + FIT_EPOCH_OFFSET_S) * 1000

    /**
     * Décode un fichier FIT (déjà décompressé) et renvoie une activité unique.
     * Lève [StravaFileException] si la signature `.FIT` est absente ou
     * l'en-tête invalide.
     */
    fun parse(bytes: ByteArray): List<ParsedActivity> {
        if (bytes.size < 14) throw StravaFileException("FIT : fichier trop court.")
        val headerSize = bytes[0].toInt() and 0xff
        if (bytes.size < headerSize || !isFit(bytes)) throw StravaFileException("FIT : signature « .FIT » absente.")

        val view = ByteBuffer.wrap(bytes)
        val dataSize = view.order(ByteOrder.LITTLE_ENDIAN).getInt(4).toLong() and 0xffffffffL
        // Fin de la zone de données : exclut le CRC final (2 octets), borné au tampon.
        val dataEnd = minOf(headerSize + dataSize, (bytes.size - 2).toLong()).toInt()

        val defs = HashMap<Int, MsgDef>()
        val points = ArrayList<ParsedPoint>()
        var sport = ParsedSport.UNKNOWN
        var startedAt: Long? = null
        var distanceM: Double? = null
        var calories: Double? = null
        /** Dernier horodatage absolu connu (s FIT), pour les en-têtes compressés. */
        var lastTimestamp: Long? = null

        var pos = headerSize
        while (pos < dataEnd) {
            val header = bytes[pos++].toInt() and 0xff

            // En-tête définition (bit 6) : décrit la disposition d'un type local.
            if ((header and 0x80) == 0 && (header and 0x40) != 0) {
                if (pos + 5 > bytes.size) break
                val localType = header and 0x0f
                val hasDev = (header and 0x20) != 0
                pos++ // octet réservé
                val le = bytes[pos++].toInt() == 0 // architecture : 0 = little-endian
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

            // En-tête données : normal (bit 7 = 0) ou horodatage compressé (bit 7 = 1).
            val compressed = (header and 0x80) != 0
            val localType = if (compressed) (header shr 5) and 0x03 else header and 0x0f
            val def = defs[localType] ?: break // données sans définition préalable → flux illisible

            var compressedTs: Long? = null
            val last = lastTimestamp
            if (compressed && last != null) {
                val offset = (header and 0x1f).toLong()
                val next = if (offset >= (last and 0x1f)) (last and 0x1f.toLong().inv()) + offset
                else (last and 0x1f.toLong().inv()) + offset + 0x20
                lastTimestamp = next
                compressedTs = next
            }

            // Lit chaque champ dans une table {numéro → valeur}.
            val values = HashMap<Int, Double>()
            val order = if (def.littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
            for (f in def.fields) {
                if (pos + f.size > bytes.size) {
                    pos = bytes.size // tronqué → on arrête proprement (message partiel conservé)
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
                    // Altitude : préfère « enhanced » (78) sinon le champ standard (2).
                    val altRaw = values[78] ?: values[2]
                    val ele = altRaw?.let { it / 5 - 500 }
                    points.add(ParsedPoint(ts, lat, lon, ele, values[3], values[4]))
                }
                MSG_SESSION -> {
                    values[5]?.let { sport = mapSport(it.toLong()) } // 5 = sport
                    values[2]?.let { startedAt = fitTimeToMs(it.toLong()) } // 2 = start_time
                    values[9]?.let { distanceM = it / 100 } // 9 = total_distance (cm→m)
                    values[11]?.let { calories = it } // 11 = total_calories (kcal)
                }
                MSG_SPORT -> if (sport == ParsedSport.UNKNOWN) {
                    values[0]?.let { sport = mapSport(it.toLong()) } // 0 = sport
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

    /** Lit un champ scalaire selon son type de base FIT ; null si valeur « invalide ». */
    private fun readNumber(view: ByteBuffer, offset: Int, baseType: Int, order: ByteOrder): Double? {
        val b = view.order(order)
        return when (baseType) {
            0x00, 0x02, 0x0a, 0x0d -> { // enum, uint8, uint8z, byte
                val v = b.get(offset).toInt() and 0xff
                if (v == 0xff) null else v.toDouble()
            }
            0x01 -> { // sint8
                val v = b.get(offset).toInt()
                if (v == 0x7f) null else v.toDouble()
            }
            0x84, 0x8b -> { // uint16, uint16z
                val v = b.getShort(offset).toInt() and 0xffff
                if (v == 0xffff) null else v.toDouble()
            }
            0x83 -> { // sint16
                val v = b.getShort(offset).toInt()
                if (v == 0x7fff) null else v.toDouble()
            }
            0x86, 0x8c -> { // uint32, uint32z
                val v = b.getInt(offset).toLong() and 0xffffffffL
                if (v == 0xffffffffL) null else v.toDouble()
            }
            0x85 -> { // sint32
                val v = b.getInt(offset)
                if (v == 0x7fffffff) null else v.toDouble()
            }
            0x88 -> { // float32
                val v = b.getFloat(offset)
                if (v.isNaN()) null else v.toDouble()
            }
            0x89 -> { // float64
                val v = b.getDouble(offset)
                if (v.isNaN()) null else v
            }
            else -> null // types non gérés (string, int64…) — ignorés
        }
    }
}
