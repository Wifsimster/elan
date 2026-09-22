// Signature AWS Signature V4 (style « path ») pour un PUT / GET d'objet —
// port de `src/lib/s3.ts`. Compatible MinIO, SeaweedFS, Garage et tout
// stockage S3-compatible auto-hébergé. Pur : (config, méthode, empreinte du
// corps, instant) → URL + en-têtes ; aucun accès réseau ni horloge implicite.
package ovh.battistella.elan.data.remote

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Paramètres d'accès à un objet S3 (secrets compris — jamais persistés tels quels). */
data class S3Config(
    /** URL de base du service, ex. https://minio.mon-homelab.tld (style « path »). */
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    /** Clé de l'objet, ex. elan-backup.json. */
    val objectKey: String,
)

/** Requête signée : URL absolue et en-têtes à joindre tels quels. */
data class SignedRequest(val url: String, val headers: Map<String, String>)

/** Endpoint décomposé : origine HTTPS, hôte (avec port éventuel) et préfixe de chemin. */
data class S3Endpoint(val origin: String, val host: String, val basePath: String)

/** Méthodes HTTP signées. */
enum class S3Method { PUT, GET }

object S3Signer {

    /** Empreinte SHA-256 hexadécimale d'un corps vide (GET). */
    const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private val AMZ_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private val ENDPOINT_RE = Regex("^https://([^/]+)(/[^?#]*)?$", RegexOption.IGNORE_CASE)

    /**
     * HTTPS obligatoire : une sauvegarde (clé secrète S3 + base entière) ne
     * doit jamais transiter en clair. Le chemin éventuel de l'endpoint
     * (ex. https://host/s3) est CONSERVÉ et préfixé à l'URI canonique — le
     * jeter cassait la signature et la cible derrière un reverse-proxy à
     * préfixe de chemin.
     */
    fun parseEndpoint(endpoint: String): S3Endpoint {
        val ep = endpoint.trim().trimEnd('/')
        val m = ENDPOINT_RE.matchEntire(ep)
            ?: throw IllegalArgumentException("Endpoint S3 invalide : HTTPS requis (attendu https://hôte).")
        // Hôte signé = en-tête `Host` qu'OkHttp enverra : en minuscules, sans
        // le port HTTPS par défaut. Sinon, 403 SignatureDoesNotMatch.
        val authority = m.groupValues[1]
        require('@' !in authority) { "Endpoint S3 invalide : identifiants dans l'URL non pris en charge." }
        val host = authority.lowercase().removeSuffix(":443")
        val basePath = m.groupValues[2].trimEnd('/')
        return S3Endpoint(origin = "https://$host", host = host, basePath = basePath)
    }

    /** Encodage URI conforme RFC 3986 attendu par AWS (non réservés `A-Za-z0-9_.~-`, `%XX` majuscules UTF-8). */
    fun uriEncode(str: String, encodeSlash: Boolean = true): String {
        val out = StringBuilder(str.length + 16)
        // Par point de code (comme `for…of` en JS) : un emoji tient sur deux
        // unités UTF-16 et doit être encodé comme UN caractère UTF-8 de 4 octets.
        var i = 0
        while (i < str.length) {
            val cp = str.codePointAt(i)
            i += Character.charCount(cp)
            val ch = cp.toChar()
            when {
                cp < 0x80 && (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '.' || ch == '~' || ch == '-') ->
                    out.append(ch)
                cp == '/'.code && !encodeSlash -> out.append(ch)
                else -> for (b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)) {
                    out.append('%').append(HEX[(b.toInt() shr 4) and 0xf]).append(HEX[b.toInt() and 0xf])
                }
            }
        }
        return out.toString()
    }

    /**
     * Construit l'URL + en-têtes signés SigV4 d'une requête (style path).
     * `payloadSha256Hex` est l'empreinte du corps (déjà calculée, le corps
     * pouvant être un fichier lu en flux) ; `nowUtc` l'instant de signature.
     */
    fun sign(config: S3Config, method: S3Method, payloadSha256Hex: String, nowUtc: Instant): SignedRequest {
        val (origin, host, basePath) = parseEndpoint(config.endpoint)
        // basePath (déjà sans slash final) encodé en préservant ses slashes ; vide → ''.
        val canonicalUri = uriEncode(basePath, false) + "/" + uriEncode(config.bucket) + "/" +
            uriEncode(config.objectKey, false)
        val amzdate = AMZ_DATE.format(nowUtc)
        val datestamp = amzdate.substring(0, 8)

        val canonicalHeaders = "host:$host\n" +
            "x-amz-content-sha256:$payloadSha256Hex\n" +
            "x-amz-date:$amzdate\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest = listOf(
            method.name,
            canonicalUri,
            "",
            canonicalHeaders,
            signedHeaders,
            payloadSha256Hex,
        ).joinToString("\n")

        val scope = "$datestamp/${config.region}/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzdate,
            scope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)),
        ).joinToString("\n")

        var key = hmac(("AWS4" + config.secretAccessKey).toByteArray(Charsets.UTF_8), datestamp)
        key = hmac(key, config.region)
        key = hmac(key, "s3")
        key = hmac(key, "aws4_request")
        val signature = toHex(hmac(key, stringToSign))

        val authorization = "AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        return SignedRequest(
            url = origin + canonicalUri,
            headers = linkedMapOf(
                "x-amz-date" to amzdate,
                "x-amz-content-sha256" to payloadSha256Hex,
                "Authorization" to authorization,
            ),
        )
    }

    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hmac(key: ByteArray, msg: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
    }

    private val HEX = "0123456789ABCDEF".toCharArray()

    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX_LOWER[v shr 4]).append(HEX_LOWER[v and 0xf])
        }
        return sb.toString()
    }

    private val HEX_LOWER = "0123456789abcdef".toCharArray()
}
