// Import de la configuration S3 par QR code (port de `src/lib/backup-qr.ts`) :
// on ne tape pas une clé secrète de 40 caractères sur un clavier de téléphone.
// Le QR est généré côté serveur (cf. docs/SAUVEGARDE.md) et lu avec la
// caméra ; il ne transite par aucun service — le décodage est local.
package ovh.battistella.elan.data.backup

import org.json.JSONException
import org.json.JSONObject
import java.net.URLDecoder

/** Champs de config qu'un QR peut renseigner (jamais `enabled`), avec leur libellé FR. */
enum class BackupField(val label: String) {
    ENDPOINT("endpoint"),
    BUCKET("bucket"),
    ACCESS_KEY_ID("access key"),
    SECRET_ACCESS_KEY("secret key"),
    REGION("région"),
    OBJECT_KEY("nom de l'objet"),
}

/**
 * Fragment de configuration lu dans un QR : champs renseignés, dans l'ordre
 * où le QR les donne (le message de confirmation les liste dans cet ordre).
 */
typealias BackupQrPatch = Map<BackupField, String>

/** Alias tolérés dans le JSON (conventions AWS / MinIO / snake_case), clés en minuscules. */
private val ALIASES: Map<String, BackupField> = mapOf(
    "endpoint" to BackupField.ENDPOINT,
    "url" to BackupField.ENDPOINT,
    "endpoint_url" to BackupField.ENDPOINT,
    "bucket" to BackupField.BUCKET,
    "accesskeyid" to BackupField.ACCESS_KEY_ID,
    "accesskey" to BackupField.ACCESS_KEY_ID,
    "access_key" to BackupField.ACCESS_KEY_ID,
    "access_key_id" to BackupField.ACCESS_KEY_ID,
    "aws_access_key_id" to BackupField.ACCESS_KEY_ID,
    "secretaccesskey" to BackupField.SECRET_ACCESS_KEY,
    "secretkey" to BackupField.SECRET_ACCESS_KEY,
    "secret_key" to BackupField.SECRET_ACCESS_KEY,
    "secret_access_key" to BackupField.SECRET_ACCESS_KEY,
    "aws_secret_access_key" to BackupField.SECRET_ACCESS_KEY,
    "region" to BackupField.REGION,
    "objectkey" to BackupField.OBJECT_KEY,
    "object_key" to BackupField.OBJECT_KEY,
    "key" to BackupField.OBJECT_KEY,
    "object" to BackupField.OBJECT_KEY,
)

// s3://ACCESS:SECRET@host[:port]/bucket[/objet] — identifiants encodés en URL
// (un secret peut contenir « / » ou « + »).
private val S3_URL_RE = Regex(
    "^s3://(?:([^:@/]+)(?::([^@/]*))?@)?([^/?#]+)(?:/([^/?#]+))?(?:/([^?#]*))?$",
    RegexOption.IGNORE_CASE,
)

/**
 * Décode le contenu d'un QR code en fragment de configuration. Deux formats :
 *
 * - JSON : `{"endpoint":"https://s3.x.tld","bucket":"elan","accessKeyId":"…","secretAccessKey":"…"}`
 *   (clés insensibles à la casse, alias snake_case/AWS acceptés, champs partiels OK) ;
 * - URL : `s3://ACCESS:SECRET@s3.x.tld/bucket[/objet]` (hôte en HTTPS implicite).
 *
 * Renvoie `null` si rien d'exploitable (QR étranger). Les valeurs sont nettoyées
 * des espaces ; les champs vides sont ignorés pour ne pas écraser une saisie.
 */
fun parseBackupQr(data: String): BackupQrPatch? {
    val text = data.trim()
    if (text.isEmpty()) return null
    val patch = (if (text.startsWith("{")) parseJson(text) else parseUrl(text)) ?: return null
    val cleaned = LinkedHashMap<BackupField, String>()
    for ((field, value) in patch) {
        val v = value?.trim()
        if (!v.isNullOrEmpty()) cleaned[field] = v
    }
    return cleaned.ifEmpty { null }
}

private fun parseJson(text: String): Map<BackupField, String?>? {
    val obj = try {
        JSONObject(text)
    } catch (e: JSONException) {
        return null
    }
    val patch = LinkedHashMap<BackupField, String?>()
    for (rawKey in obj.keys()) {
        val field = ALIASES[rawKey.lowercase()] ?: continue
        val value = obj.opt(rawKey) as? String ?: continue
        patch[field] = value
    }
    return patch
}

private fun parseUrl(text: String): Map<BackupField, String?>? {
    val m = S3_URL_RE.matchEntire(text) ?: return null
    val (access, secret, host, bucket, obj) = m.destructured
    fun dec(s: String): String? {
        if (s.isEmpty()) return null
        return try {
            // decodeURIComponent : « + » reste un plus (URLDecoder le changerait en espace).
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            s
        }
    }
    return linkedMapOf(
        BackupField.ENDPOINT to "https://$host",
        BackupField.BUCKET to dec(bucket),
        BackupField.ACCESS_KEY_ID to dec(access),
        BackupField.SECRET_ACCESS_KEY to dec(secret),
        BackupField.OBJECT_KEY to dec(obj),
    )
}

/** Libellés français des champs remplis, pour le message de confirmation. */
fun describeQrPatch(patch: BackupQrPatch): String = patch.keys.joinToString(", ") { it.label }
