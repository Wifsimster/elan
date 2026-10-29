package ovh.battistella.elan.data.backup

import org.json.JSONException
import org.json.JSONObject
import java.net.URLDecoder

enum class BackupField(val label: String) {
    ENDPOINT("endpoint"),
    BUCKET("bucket"),
    ACCESS_KEY_ID("access key"),
    SECRET_ACCESS_KEY("secret key"),
    REGION("région"),
    OBJECT_KEY("nom de l'objet"),
}

typealias BackupQrPatch = Map<BackupField, String>

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

private val S3_URL_RE = Regex(
    "^s3://(?:([^:@/]+)(?::([^@/]*))?@)?([^/?#]+)(?:/([^/?#]+))?(?:/([^?#]*))?$",
    RegexOption.IGNORE_CASE,
)

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

fun describeQrPatch(patch: BackupQrPatch): String = patch.keys.joinToString(", ") { it.label }
