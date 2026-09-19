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

/**
 * Accès à la clé AES qu'expo-secure-store a générée dans l'AndroidKeyStore.
 * Interface pour que les tests JVM injectent une clé en mémoire (le KeyStore
 * Android n'existe pas sous Robolectric).
 */
fun interface KeyStoreAccess {
    /** `null` si l'alias est inconnu (secret jamais écrit, ou KeyStore réinitialisé). */
    fun secretKey(alias: String): SecretKey?
}

/** Implémentation réelle : l'AndroidKeyStore de l'appareil. */
object AndroidKeyStoreAccess : KeyStoreAccess {
    override fun secretKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }
}

/**
 * Relit et déchiffre les secrets qu'expo-secure-store a laissés sur
 * l'appareil (identifiants S3 de la sauvegarde), pour les rapatrier dans
 * [ovh.battistella.elan.data.secrets.SecretStore] une fois pour toutes.
 *
 * Format d'expo-secure-store (Android) : un `SharedPreferences` nommé
 * `SecureStore` ; la valeur de la clé `key_v1-<nom>` (ou `<nom>` nu pour les
 * entrées les plus anciennes) est un JSON
 * `{"ct":"<b64>","iv":"<b64>","tlen":128,"scheme":"aes","usesKeystoreSuffix":true}`,
 * chiffré en AES/GCM avec la clé du KeyStore d'alias
 * `AES/GCM/NoPadding:key_v1:keystoreUnauthenticated` (`usesKeystoreSuffix`)
 * ou `AES/GCM/NoPadding:key_v1` (entrées anciennes).
 *
 * Tout échec (entrée absente, JSON illisible, clé perdue, tag GCM invalide)
 * vaut `null` : l'utilisateur ressaisira ses identifiants, l'import continue.
 */
@Singleton
class LegacySecretsReader internal constructor(
    private val prefs: () -> SharedPreferences?,
    private val keyStore: KeyStoreAccess,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        prefs = {
            // Ne pas créer le fichier de préférences s'il n'a jamais existé.
            val paths = LegacyPaths(context)
            if (paths.sharedPrefsDir.resolve(LegacyPaths.SECURE_STORE_PREFS_FILE).isFile) {
                context.getSharedPreferences(LegacyPaths.SECURE_STORE_PREFS, Context.MODE_PRIVATE)
            } else {
                null
            }
        },
        keyStore = AndroidKeyStoreAccess,
    )

    /** Valeur en clair du secret [name], ou `null` s'il est absent ou illisible. */
    fun read(name: String): String? {
        val p = try {
            prefs() ?: return null
        } catch (e: Exception) {
            return null
        }
        val raw = p.getString("$KEY_PREFIX$name", null) ?: p.getString(name, null) ?: return null
        return decrypt(raw)
    }

    /** Déchiffre une valeur JSON d'expo-secure-store ; `null` si quoi que ce soit cloche. */
    fun decrypt(raw: String): String? = try {
        val entry = parse(raw) ?: return null
        val key = keyStore.secretKey(entry.alias) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(entry.tagLengthBits, entry.iv))
        String(cipher.doFinal(entry.ciphertext), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    /** Entrée chiffrée décodée (JSON + Base64), sans déchiffrement. */
    data class Entry(val ciphertext: ByteArray, val iv: ByteArray, val tagLengthBits: Int, val alias: String)

    /** `null` si le JSON n'a pas la forme attendue (schéma inconnu, champs manquants). */
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
