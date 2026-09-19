package ovh.battistella.elan.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.net.ssl.SSLHandshakeException

class S3ClientTest {

    private val server = MockWebServer()
    private lateinit var client: S3Client
    private val config = S3Config(
        endpoint = "https://s3.example.tld",
        region = "us-east-1",
        bucket = "elan",
        accessKeyId = "AK",
        secretAccessKey = "SK",
        objectKey = "elan-backup.json",
    )
    private val tmp = createTempDir("s3client")

    @Before
    fun setUp() {
        server.start()
        // L'endpoint signé est HTTPS (exigé par le signataire) ; un
        // intercepteur redirige la connexion vers le serveur factice en clair.
        val rewrite = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(req.newBuilder().url(url).build())
        }
        val http = OkHttpClient.Builder().addInterceptor(rewrite).build()
        client = S3Client(http, Clock.fixed(Instant.parse("2026-09-11T10:06:00Z"), ZoneOffset.UTC), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.shutdown()
        tmp.deleteRecursively()
    }

    private suspend fun expectS3(block: suspend () -> Unit): S3Exception {
        try {
            block()
        } catch (e: S3Exception) {
            return e
        }
        throw AssertionError("S3Exception attendue")
    }

    private fun xml(code: String, msg: String = "x") =
        "<?xml version=\"1.0\"?><Error><Code>$code</Code><Message>$msg</Message></Error>"

    @Test
    fun `PUT envoie le fichier signé avec le bon type de contenu`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val file = File(tmp, "up.json").apply { writeText("{\"format\":1}") }

        client.putObject(config, file)

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/elan/elan-backup.json", req.path)
        assertEquals("application/json", req.getHeader("Content-Type"))
        assertEquals("20260911T100600Z", req.getHeader("x-amz-date"))
        assertEquals(S3Signer.sha256Hex("{\"format\":1}".toByteArray()), req.getHeader("x-amz-content-sha256"))
        assertTrue(req.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256 Credential=AK/20260911/us-east-1/s3/aws4_request, "))
        assertEquals("{\"format\":1}", req.body.readUtf8())
    }

    @Test
    fun `PUT refusé → message traduit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody(xml("SignatureDoesNotMatch")))
        val file = File(tmp, "up.json").apply { writeText("{}") }
        val e = expectS3 { client.putObject(config, file) }
        assertTrue(e.message!!.contains("clé secrète"))
    }

    @Test
    fun `GET écrit l'objet dans le fichier de destination`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"app\":\"suivi-sport\"}"))
        val dest = File(tmp, "down/backup.json")

        val got = client.getObject(config, dest)

        assertEquals(dest, got)
        assertEquals("{\"app\":\"suivi-sport\"}", dest.readText())
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals(S3Signer.EMPTY_SHA256, req.getHeader("x-amz-content-sha256"))
        assertNull(req.getHeader("Content-Type"))
    }

    @Test
    fun `GET 404 sans objet → null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(xml("NoSuchKey")))
        val dest = File(tmp, "backup.json")
        assertNull(client.getObject(config, dest))
        assertFalse(dest.exists())
    }

    @Test
    fun `GET 404 sur bucket manquant → erreur`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(xml("NoSuchBucket")))
        val e = expectS3 { client.getObject(config, File(tmp, "b.json")) }
        assertEquals("Le bucket « elan » n’existe pas sur ce serveur.", e.message)
    }

    @Test
    fun `GET 500 → erreur serveur`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val e = expectS3 { client.getObject(config, File(tmp, "b.json")) }
        assertEquals("Erreur côté serveur (HTTP 503) : réessaie plus tard.", e.message)
    }

    // ---- describeS3Failure / describeNetworkFailure ---------------------

    @Test
    fun `describeS3Failure couvre chaque code S3`() {
        assertEquals(
            "Signature refusée : la clé secrète ne correspond pas à l’access key. Vérifie-la caractère par caractère (« Afficher »).",
            describeS3Failure(S3Method.PUT, 403, xml("SignatureDoesNotMatch"), config),
        )
        assertEquals("Access key « AK » inconnue du serveur.", describeS3Failure(S3Method.GET, 403, xml("InvalidAccessKeyId"), config))
        assertEquals("Le bucket « elan » n’existe pas sur ce serveur.", describeS3Failure(S3Method.PUT, 404, xml("NoSuchBucket"), config))
        assertEquals(
            "Accès refusé : cette clé n’a pas le droit d’écrire dans le bucket « elan ».",
            describeS3Failure(S3Method.PUT, 403, xml("AccessDenied"), config),
        )
        assertEquals(
            "Accès refusé : cette clé n’a pas le droit de lire dans le bucket « elan ».",
            describeS3Failure(S3Method.GET, 403, xml("AccessDenied"), config),
        )
        assertEquals(
            "Horloge du téléphone trop décalée par rapport au serveur (signature expirée).",
            describeS3Failure(S3Method.PUT, 403, xml("RequestTimeTooSkewed"), config),
        )
    }

    @Test
    fun `describeS3Failure retombe sur les statuts HTTP`() {
        assertEquals("Identifiants refusés par le serveur (HTTP 401).", describeS3Failure(S3Method.PUT, 401, "", config))
        assertEquals("Identifiants refusés par le serveur (HTTP 403).", describeS3Failure(S3Method.PUT, 403, "", config))
        assertEquals(
            "Le bucket « elan » ou le chemin de l’endpoint est introuvable (HTTP 404).",
            describeS3Failure(S3Method.GET, 404, "", config),
        )
        assertEquals("Erreur côté serveur (HTTP 503) : réessaie plus tard.", describeS3Failure(S3Method.PUT, 503, "", config))
        val m = describeS3Failure(S3Method.PUT, 400, xml("MalformedXML", "bad body"), config)
        assertEquals("Le serveur a refusé la requête (HTTP 400) : bad body", m)
        assertFalse(m.contains("<"))
        assertEquals("Le serveur a refusé la requête (HTTP 400) : Oops", describeS3Failure(S3Method.PUT, 400, "<html><b>Oops</b></html>", config))
        assertEquals("Le serveur a refusé la requête (HTTP 418).", describeS3Failure(S3Method.PUT, 418, "", config))
        assertEquals("Le serveur a refusé la requête (HTTP 400) : ${"x".repeat(120)}", describeS3Failure(S3Method.PUT, 400, "x".repeat(300), config))
    }

    @Test
    fun `describeNetworkFailure traduit délai, TLS et réseau`() {
        assertTrue(describeNetworkFailure(SocketTimeoutException("timeout")).contains("délai"))
        assertTrue(describeNetworkFailure(java.io.InterruptedIOException("timeout")).contains("délai"))
        assertTrue(describeNetworkFailure(SSLHandshakeException("bad cert")).startsWith("Certificat"))
        assertTrue(describeNetworkFailure(Exception("Trust anchor for certification path not found")).startsWith("Certificat"))
        assertTrue(describeNetworkFailure(UnknownHostException("s3.example.tld")).contains("injoignable"))
        assertTrue(describeNetworkFailure(ConnectException("Connection refused")).contains("injoignable"))
        assertTrue(describeNetworkFailure(Exception("Network request failed")).contains("injoignable"))
        assertEquals("boom", describeNetworkFailure(Exception("boom")))
        assertEquals("Échec réseau.", describeNetworkFailure(Exception()))
    }
}
