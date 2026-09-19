// Client S3 minimal (PUT / GET d'un objet) sur OkHttp, signé SigV4 — port de
// `putObject` / `getObject` de `src/lib/s3.ts`. Le corps est un fichier lu et
// écrit en flux : une sauvegarde de plusieurs dizaines de Mo ne passe jamais
// entière en mémoire.
package ovh.battistella.elan.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class S3Client @Inject constructor(
    http: OkHttpClient,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    /**
     * Délai d'expiration (connexion, lecture, écriture) : un NAS injoignable ne
     * doit pas laisser la sauvegarde bloquée « en cours » indéfiniment.
     */
    private val client: OkHttpClient = http.newBuilder()
        .connectTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /** Téléverse `file` comme objet JSON dans le bucket. Lève [S3Exception] en cas d'échec. */
    suspend fun putObject(config: S3Config, file: File): Unit = withContext(io) {
        val payloadHash = sha256HexOf(file)
        val signed = S3Signer.sign(config, S3Method.PUT, payloadHash, clock.instant())
        val request = Request.Builder()
            .url(signed.url)
            .put(FileBody(file))
            .apply { signed.headers.forEach { (k, v) -> header(k, v) } }
            .header("Content-Type", "application/json")
            .build()
        execute(request).use { res ->
            if (!res.isSuccessful) {
                val detail = runCatching { res.body?.string() ?: "" }.getOrDefault("")
                throw S3Exception(describeS3Failure(S3Method.PUT, res.code, detail, config))
            }
        }
    }

    /**
     * Télécharge l'objet dans `dest` et le renvoie, ou `null` s'il n'existe pas
     * (404) — sauf si c'est le bucket qui manque, auquel cas l'utilisateur doit
     * corriger sa config ([S3Exception]).
     */
    suspend fun getObject(config: S3Config, dest: File): File? = withContext(io) {
        val signed = S3Signer.sign(config, S3Method.GET, S3Signer.EMPTY_SHA256, clock.instant())
        val request = Request.Builder()
            .url(signed.url)
            .get()
            .apply { signed.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        execute(request).use { res ->
            if (res.code == 404) {
                val detail = runCatching { res.body?.string() ?: "" }.getOrDefault("")
                if (parseS3Error(detail).code == "NoSuchBucket") {
                    throw S3Exception(describeS3Failure(S3Method.GET, 404, detail, config))
                }
                return@withContext null
            }
            if (!res.isSuccessful) {
                val detail = runCatching { res.body?.string() ?: "" }.getOrDefault("")
                throw S3Exception(describeS3Failure(S3Method.GET, res.code, detail, config))
            }
            val body = res.body ?: throw S3Exception("Réponse vide du serveur.")
            dest.parentFile?.mkdirs()
            try {
                body.byteStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
            } catch (e: IOException) {
                dest.delete()
                throw S3Exception(describeNetworkFailure(e), e)
            }
            dest
        }
    }

    /** Exécute la requête ; un échec réseau (jamais de réponse) devient un [S3Exception] traduit. */
    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (e: IOException) {
        throw S3Exception(describeNetworkFailure(e), e)
    }

    private fun sha256HexOf(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return S3Signer.toHex(md.digest())
    }

    /** Corps de requête lu en flux depuis un fichier (longueur connue, pas de chunked). */
    private class FileBody(private val file: File) : RequestBody() {
        override fun contentType() = JSON
        override fun contentLength(): Long = file.length()
        override fun writeTo(sink: BufferedSink) {
            file.source().use { sink.writeAll(it) }
        }
    }

    companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
        private val JSON = "application/json".toMediaType()
    }
}
