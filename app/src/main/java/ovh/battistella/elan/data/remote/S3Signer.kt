package ovh.battistella.elan.data.remote

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class S3Config(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val objectKey: String,
)

data class SignedRequest(val url: String, val headers: Map<String, String>)

data class S3Endpoint(val origin: String, val host: String, val basePath: String)

enum class S3Method { PUT, GET }

object S3Signer {

    const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private val AMZ_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private val ENDPOINT_RE = Regex("^https://([^/]+)(/[^?#]*)?$", RegexOption.IGNORE_CASE)

    fun parseEndpoint(endpoint: String): S3Endpoint {
        val ep = endpoint.trim().trimEnd('/')
        val m = ENDPOINT_RE.matchEntire(ep)
            ?: throw IllegalArgumentException("Endpoint S3 invalide : HTTPS requis (attendu https://hôte).")
        val authority = m.groupValues[1]
        require('@' !in authority) { "Endpoint S3 invalide : identifiants dans l'URL non pris en charge." }
        val host = authority.lowercase().removeSuffix(":443")
        val basePath = m.groupValues[2].trimEnd('/')
        return S3Endpoint(origin = "https://$host", host = host, basePath = basePath)
    }

    fun uriEncode(str: String, encodeSlash: Boolean = true): String {
        val out = StringBuilder(str.length + 16)
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

    fun sign(config: S3Config, method: S3Method, payloadSha256Hex: String, nowUtc: Instant): SignedRequest {
        val (origin, host, basePath) = parseEndpoint(config.endpoint)
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
