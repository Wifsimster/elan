// Décodage des trames GATT standard (miroir de `parseHeartRate` / `parseCsc`
// dans lib/ble.ts) et UUID des profils utilisés. Fonctions pures, sans Android.
package ovh.battistella.elan.sensors.ble

import java.util.UUID

/** UUID 128 bits des services/caractéristiques Bluetooth SIG utilisés. */
object BleUuids {
    /** Service « Heart Rate » (0x180D). */
    val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /** Caractéristique « Heart Rate Measurement » (0x2A37). */
    val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /**
     * Service « Cycling Speed and Cadence » (0x1816). Couvre les capteurs
     * iGPSPORT CAD70 (cadence) et SPD70 (vitesse), et tout capteur conforme.
     */
    val CSC_SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    /** Caractéristique « CSC Measurement » (0x2A5B). */
    val CSC_MEASUREMENT: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")

    /** Descripteur « Client Characteristic Configuration » (0x2902) : active les notifications. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/** Délai max d'une tentative de connexion (ms) : un capteur endormi ne doit pas bloquer ~30 s. */
const val CONNECT_TIMEOUT_MS = 10_000L

/** Durée max d'un scan avant arrêt automatique (ms). */
const val SCAN_TIMEOUT_MS = 20_000L

/**
 * Compteurs bruts d'une mesure CSC (caractéristique 0x2A5B). Un capteur peut
 * fournir la roue (vitesse), le pédalier (cadence) ou les deux ; les champs
 * absents valent `null`. Les temps sont en 1/1024 s et bouclent à 65536.
 */
data class CscRaw(
    /** Révolutions de roue cumulées (uint32). */
    val wheelRevs: Long?,
    /** Horodatage du dernier passage de roue, en 1/1024 s (uint16). */
    val wheelTime: Int?,
    /** Révolutions de pédalier cumulées (uint16). */
    val crankRevs: Int?,
    /** Horodatage du dernier tour de pédalier, en 1/1024 s (uint16). */
    val crankTime: Int?,
)

private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

/** Lit un entier little-endian non signé de `len` octets à partir de `off`. */
private fun ByteArray.readLE(off: Int, len: Int): Long {
    var v = 0L
    for (i in 0 until len) v = v or (u8(off + i).toLong() shl (8 * i))
    return v
}

/**
 * Extrait la fréquence cardiaque (bpm) d'une trame « Heart Rate Measurement ».
 * Octet 0 = drapeaux ; bit 0 indique une valeur 8 bits (0) ou 16 bits (1).
 *
 * Une trame 16 bits annoncée mais tronquée à 2 octets est rejetée plutôt que
 * de remonter une FC fausse. FC = 0 (contact perdu, trame de repli) n'est pas
 * une valeur physiologique : traitée comme « absente ».
 */
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

/**
 * Décode une mesure CSC. Octet 0 = drapeaux : bit 0 → données roue présentes
 * (uint32 révolutions + uint16 temps), bit 1 → données pédalier présentes
 * (uint16 révolutions + uint16 temps). Trame tronquée → `null`.
 */
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
