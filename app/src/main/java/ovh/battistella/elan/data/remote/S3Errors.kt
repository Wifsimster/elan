// Traduction des échecs S3 en phrases actionnables pour l'écran Réglages —
// port de `describeS3Failure` / `describeNetworkFailure` de `src/lib/s3.ts`.
// Le XML brut du serveur ne dit pas à l'utilisateur quel champ corriger.
package ovh.battistella.elan.data.remote

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** Échec S3 ou réseau déjà traduit en français (message affichable tel quel). */
class S3Exception(message: String, cause: Throwable? = null) : Exception(message, cause)

/** `<Code>` et `<Message>` d'une réponse d'erreur S3 (XML). */
data class S3Error(val code: String, val message: String)

/** Extrait `<Code>` et `<Message>` d'une réponse d'erreur S3 (XML). */
fun parseS3Error(xml: String): S3Error {
    fun pick(tag: String): String =
        Regex("<$tag>([^<]*)</$tag>").find(xml)?.groupValues?.get(1)?.trim() ?: ""
    return S3Error(code = pick("Code"), message = pick("Message"))
}

/** Traduit un échec HTTP S3 (statut non 2xx) en phrase actionnable. */
fun describeS3Failure(method: S3Method, status: Int, body: String, config: S3Config): String {
    val (code, message) = parseS3Error(body)
    when (code) {
        "SignatureDoesNotMatch" ->
            return "Signature refusée : la clé secrète ne correspond pas à l’access key. Vérifie-la caractère par caractère (« Afficher »)."
        "InvalidAccessKeyId" -> return "Access key « ${config.accessKeyId} » inconnue du serveur."
        "NoSuchBucket" -> return "Le bucket « ${config.bucket} » n’existe pas sur ce serveur."
        "AccessDenied" -> {
            val verb = if (method == S3Method.PUT) "d’écrire" else "de lire"
            return "Accès refusé : cette clé n’a pas le droit $verb dans le bucket « ${config.bucket} »."
        }
        "RequestTimeTooSkewed" ->
            return "Horloge du téléphone trop décalée par rapport au serveur (signature expirée)."
    }
    if (status == 401 || status == 403) return "Identifiants refusés par le serveur (HTTP $status)."
    if (status == 404) return "Le bucket « ${config.bucket} » ou le chemin de l’endpoint est introuvable (HTTP 404)."
    if (status >= 500) return "Erreur côté serveur (HTTP $status) : réessaie plus tard."
    val detail = message.ifEmpty { code }.ifEmpty {
        body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(120)
    }
    return "Le serveur a refusé la requête (HTTP $status)" + (if (detail.isNotEmpty()) " : $detail" else ".")
}

/** Traduit un échec réseau (requête jamais aboutie) : endpoint injoignable, TLS, délai. */
fun describeNetworkFailure(e: Throwable): String {
    val msg = e.message ?: ""
    val timeout = e is SocketTimeoutException ||
        (e is InterruptedIOException && msg.contains("timeout", ignoreCase = true))
    if (timeout) {
        return "Le serveur ne répond pas (délai dépassé). Vérifie l’endpoint et que le serveur est joignable depuis ce réseau."
    }
    val tls = e is SSLException || e is CertificateException ||
        Regex("certificate|ssl|tls|trust anchor", RegexOption.IGNORE_CASE).containsMatchIn(msg)
    if (tls) {
        return "Certificat HTTPS refusé par le téléphone. Le serveur doit présenter un certificat valide (Let’s Encrypt par ex.)."
    }
    val unreachable = e is UnknownHostException || e is ConnectException ||
        Regex("network request failed|unable to resolve|failed to connect|econnrefused|enotfound", RegexOption.IGNORE_CASE)
            .containsMatchIn(msg)
    if (unreachable) {
        return "Serveur injoignable : vérifie l’endpoint (nom de domaine, port) et la connexion réseau."
    }
    return msg.ifEmpty { "Échec réseau." }
}
