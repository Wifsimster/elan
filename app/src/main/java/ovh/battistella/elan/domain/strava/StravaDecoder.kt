// Aiguillage de décodage des fichiers d'export Strava, à partir d'octets bruts
// (port de `src/lib/strava/decode.ts`). Trois cas, dans cet ordre :
//   1. gzip (.gpx.gz / .tcx.gz / .fit.gz de l'export en masse) → décompression ;
//   2. FIT binaire (signature « .FIT ») → `FitDecoder` ;
//   3. XML texte (GPX / TCX, export par activité) → `GpxTcxParser`.
// 100 % local, aucun appel réseau.
package ovh.battistella.elan.domain.strava

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

object StravaDecoder {

    /** Taille maximale après décompression (octets) — garde-fou mémoire / DoS. */
    const val MAX_DECOMPRESSED_BYTES = 60L * 1024 * 1024

    fun isGzip(b: ByteArray): Boolean = b.size >= 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte()

    /**
     * Taille décompressée annoncée par l'en-tête gzip (champ ISIZE : 4 octets
     * de fin, little-endian, modulo 2^32). Permet de refuser une « bombe »
     * AVANT de l'inflater en mémoire.
     */
    fun declaredGunzipSize(b: ByteArray): Long {
        if (b.size < 4) return 0
        val n = b.size
        return (b[n - 4].toLong() and 0xff) or
            ((b[n - 3].toLong() and 0xff) shl 8) or
            ((b[n - 2].toLong() and 0xff) shl 16) or
            ((b[n - 1].toLong() and 0xff) shl 24)
    }

    /** Décode un fichier d'export Strava (octets) vers la forme commune [ParseResult]. */
    fun decode(bytes: ByteArray): ParseResult {
        var data = bytes
        if (isGzip(data)) {
            // Garde-fou anti « zip bomb » : on rejette sur la taille annoncée AVANT
            // d'allouer le tampon décompressé…
            if (declaredGunzipSize(data) > MAX_DECOMPRESSED_BYTES) {
                throw StravaFileException("Fichier décompressé trop volumineux.")
            }
            data = gunzip(data)
        }

        if (FitDecoder.isFit(data)) {
            return ParseResult(StravaFormat.FIT, FitDecoder.parse(data))
        }

        // Sinon, on suppose du XML (GPX/TCX) : décodage UTF-8 puis parseur texte.
        return GpxTcxParser.parse(String(data, Charsets.UTF_8))
    }

    /** Inflate en flux, en s'arrêtant dès que la taille RÉELLE dépasse le plafond (ISIZE n'est pas fiable à 100 %). */
    private fun gunzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(minOf(declaredGunzipSize(data), MAX_DECOMPRESSED_BYTES).toInt().coerceAtLeast(1024))
        try {
            GZIPInputStream(data.inputStream()).use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DECOMPRESSED_BYTES) throw StravaFileException("Fichier décompressé trop volumineux.")
                    out.write(buf, 0, n)
                }
            }
        } catch (e: IOException) {
            throw StravaFileException("Archive gzip illisible.", e)
        }
        return out.toByteArray()
    }
}
