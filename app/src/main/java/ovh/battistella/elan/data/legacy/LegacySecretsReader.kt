package ovh.battistella.elan.data.legacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

fun interface KeyStoreAccess {
    fun secretKey(alias: String): SecretKey?
}

object AndroidKeyStoreAccess : KeyStoreAccess {
    override fun secretKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }
}

@Singleton
class LegacySecretsReader internal constructor(
    private val prefs: () -> SharedPreferences?,
    private val keyStore: KeyStoreAccess,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        prefs = {
            val paths = LegacyPaths(context)
            if (paths.sharedPrefsDir.resolve(LegacyPaths.SECURE_STORE_PREFS_FILE).isFile) {
                context.getSharedPreferences(LegacyPaths.SECURE_STORE_PREFS, Context.MODE_PRIVATE)
            } else {
                null
            }
        },
        keyStore = AndroidKeyStoreAccess,
    )

    fun read(name: String): String? {
        val p = try {
            prefs() ?: return null
        } catch (e: Exception) {
            return null
        }
        val raw = p.getString("$KEY_PREFIX$name", null) ?: p.getString(name, null) ?: return null
        return decrypt(raw)
    }

    fun decrypt(raw: String): String? = try {
        val entry = parse(raw) ?: return null
        val key = keyStore.secretKey(entry.alias) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(entry.tagLengthBits, entry.iv))
        String(cipher.doFinal(entry.ciphertext), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    data class Entry(val ciphertext: ByteArray, val iv: ByteArray, val tagLengthBits: Int, val alias: String)

    fun parse(raw: String): Entry? = try {
        val o = JSONObject(raw)
        val scheme = o.optString("scheme", "aes")
        val ct = o.optString("ct", "")
        val iv = o.optString("iv", "")
        if (scheme != "aes" || ct.isEmpty() || iv.isEmpty()) {
            null
        } else {
            val suffix = o.optBoolean("usesKeystoreSuffix", false)
            Entry(
                ciphertext = Base64.decode(ct, Base64.DEFAULT),
                iv = Base64.decode(iv, Base64.DEFAULT),
                tagLengthBits = o.optInt("tlen", DEFAULT_TAG_BITS),
                alias = if (suffix) ALIAS_UNAUTHENTICATED else ALIAS_BASE,
            )
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val KEY_PREFIX = "key_v1-"
        const val ALIAS_BASE = "AES/GCM/NoPadding:key_v1"
        const val ALIAS_UNAUTHENTICATED = "$ALIAS_BASE:keystoreUnauthenticated"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEFAULT_TAG_BITS = 128
    }
}
