package ovh.battistella.elan.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Vérifie la signature SigV4 produite contre un calcul indépendant (chaîne
 * HMAC écrite ici, à la main) : la dérivation de clé et la requête canonique
 * doivent être conformes au format AWS — un écart se traduit par un
 * `SignatureDoesNotMatch` côté serveur. Port de `__tests__/lib/s3.test.ts`.
 */
class S3SignerTest {

    private val config = S3Config(
        endpoint = "https://s3.example.tld",
        region = "us-east-1",
        bucket = "suivi-sport",
        accessKeyId = "AKIDEXAMPLE",
        secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
        objectKey = "suivi-sport.json",
    )
    private val body = "{\"format\":1}"
    private val now: Instant = Instant.parse("2026-09-11T10:06:00Z")

    // ---- référence indépendante -------------------------------------------

    private fun sha256(s: String): String = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))

    private fun hmac(key: ByteArray, msg: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(msg.toByteArray())

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun `signe un PUT comme la référence`() {
        val payloadHash = sha256(body)
        val signed = S3Signer.sign(config, S3Method.PUT, payloadHash, now)

        assertEquals("https://s3.example.tld/suivi-sport/suivi-sport.json", signed.url)
        val amzdate = "20260911T100600Z"
        val datestamp = "20260911"
        assertEquals(amzdate, signed.headers["x-amz-date"])
        assertEquals(payloadHash, signed.headers["x-amz-content-sha256"])

        val canonicalRequest = listOf(
            "PUT",
            "/suivi-sport/suivi-sport.json",
            "",
            "host:s3.example.tld\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzdate\n",
            "host;x-amz-content-sha256;x-amz-date",
            payloadHash,
        ).joinToString("\n")
        val scope = "$datestamp/us-east-1/s3/aws4_request"
        val stringToSign = listOf("AWS4-HMAC-SHA256", amzdate, scope, sha256(canonicalRequest)).joinToString("\n")
        val kSigning = hmac(hmac(hmac(hmac(("AWS4" + config.secretAccessKey).toByteArray(), datestamp), "us-east-1"), "s3"), "aws4_request")
        val signature = hex(hmac(kSigning, stringToSign))

        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/$scope, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=$signature",
            signed.headers["Authorization"],
        )
    }

    @Test
    fun `le GET signe l'empreinte du corps vide`() {
        val signed = S3Signer.sign(config, S3Method.GET, S3Signer.EMPTY_SHA256, now)
        assertEquals(sha256(""), signed.headers["x-amz-content-sha256"])
        assertEquals(S3Signer.EMPTY_SHA256, sha256(""))
    }

    @Test
    fun `conserve le préfixe de chemin de l'endpoint`() {
        val cfg = config.copy(endpoint = "https://host.tld/s3/", objectKey = "dossier/élan backup.json")
        val signed = S3Signer.sign(cfg, S3Method.PUT, S3Signer.EMPTY_SHA256, now)
        assertEquals("https://host.tld/s3/suivi-sport/dossier/%C3%A9lan%20backup.json", signed.url)
        assertEquals(S3Endpoint("https://host.tld", "host.tld", "/s3"), S3Signer.parseEndpoint("https://host.tld/s3/"))
        assertEquals(S3Endpoint("https://h:9000", "h:9000", ""), S3Signer.parseEndpoint("  https://h:9000/  "))
    }

    @Test
    fun `refuse le HTTP en clair`() {
        val e = assertThrows(IllegalArgumentException::class.java) { S3Signer.parseEndpoint("http://minio.local") }
        assertEquals("Endpoint S3 invalide : HTTPS requis (attendu https://hôte).", e.message)
        assertThrows(IllegalArgumentException::class.java) { S3Signer.parseEndpoint("minio.local") }
    }

    @Test
    fun `uriEncode suit la RFC 3986 attendue par AWS`() {
        assertEquals("a-b_c.d~e", S3Signer.uriEncode("a-b_c.d~e"))
        assertEquals("a%2Fb", S3Signer.uriEncode("a/b"))
        assertEquals("a/b", S3Signer.uriEncode("a/b", encodeSlash = false))
        assertEquals("%C3%A9%20%2B%2A", S3Signer.uriEncode("é +*"))
        assertEquals("%F0%9F%9A%B4", S3Signer.uriEncode("🚴"))
    }
}
