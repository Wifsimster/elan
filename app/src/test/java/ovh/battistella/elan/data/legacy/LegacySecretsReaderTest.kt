package ovh.battistella.elan.data.legacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
class LegacySecretsReaderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("SecureStore-test", Context.MODE_PRIVATE)

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val suffixedKey = newKey()
    private val baseKey = newKey()

    /** KeyStore factice : une clé par alias, et mémorise les alias demandés. */
    private val requestedAliases = mutableListOf<String>()
    private val keyStore = KeyStoreAccess { alias ->
        requestedAliases += alias
        when (alias) {
            LegacySecretsReader.ALIAS_UNAUTHENTICATED -> suffixedKey
            LegacySecretsReader.ALIAS_BASE -> baseKey
            else -> null
        }
    }
    private val reader = LegacySecretsReader({ prefs }, keyStore)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    private fun encrypt(plaintext: String, key: SecretKey, usesSuffix: Boolean?, tlen: Int = 128): String {
        val iv = ByteArray(12) { (it + 3).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(tlen, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val o = JSONObject()
            .put("ct", Base64.encodeToString(ct, Base64.DEFAULT))
            .put("iv", Base64.encodeToString(iv, Base64.DEFAULT))
            .put("tlen", tlen)
            .put("scheme", "aes")
        if (usesSuffix != null) o.put("usesKeystoreSuffix", usesSuffix)
        return o.toString()
    }

    @Test
    fun `parse extrait ct iv tlen et choisit l'alias selon usesKeystoreSuffix`() {
        val withSuffix = reader.parse(encrypt("x", suffixedKey, usesSuffix = true))!!
        assertEquals(LegacySecretsReader.ALIAS_UNAUTHENTICATED, withSuffix.alias)
        assertEquals(128, withSuffix.tagLengthBits)
        assertArrayEquals(ByteArray(12) { (it + 3).toByte() }, withSuffix.iv)
        assertEquals(1 + 16, withSuffix.ciphertext.size)

        assertEquals(LegacySecretsReader.ALIAS_BASE, reader.parse(encrypt("x", baseKey, usesSuffix = false))!!.alias)
        // Champ absent (entrée très ancienne) : alias sans suffixe.
        assertEquals(LegacySecretsReader.ALIAS_BASE, reader.parse(encrypt("x", baseKey, usesSuffix = null))!!.alias)
    }

    @Test
    fun `déchiffrement aller-retour avec la clé de chaque alias`() {
        assertEquals("AKIA-suffixe", reader.decrypt(encrypt("AKIA-suffixe", suffixedKey, usesSuffix = true)))
        assertEquals("AKIA-base", reader.decrypt(encrypt("AKIA-base", baseKey, usesSuffix = false)))
        assertEquals("accents éèà/+=", reader.decrypt(encrypt("accents éèà/+=", suffixedKey, usesSuffix = true)))
        // Base64 avec retours à la ligne (Base64.DEFAULT sur un long texte) : accepté.
        val long = "x".repeat(200)
        assertEquals(long, reader.decrypt(encrypt(long, suffixedKey, usesSuffix = true)))
    }

    @Test
    fun `read cherche la clé préfixée key_v1- puis la clé nue`() {
        prefs.edit()
            .putString("key_v1-backup_s3_accessKeyId", encrypt("prefixée", suffixedKey, usesSuffix = true))
            .putString("backup_s3_secretAccessKey", encrypt("nue", baseKey, usesSuffix = false))
            .commit()
        assertEquals("prefixée", reader.read("backup_s3_accessKeyId"))
        assertEquals("nue", reader.read("backup_s3_secretAccessKey"))
        assertNull(reader.read("inconnue"))
        assertEquals(
            listOf(LegacySecretsReader.ALIAS_UNAUTHENTICATED, LegacySecretsReader.ALIAS_BASE),
            requestedAliases,
        )
    }

    @Test
    fun `la clé préfixée prime sur la clé nue`() {
        prefs.edit()
            .putString("key_v1-k", encrypt("v1", suffixedKey, usesSuffix = true))
            .putString("k", encrypt("ancienne", baseKey, usesSuffix = false))
            .commit()
        assertEquals("v1", reader.read("k"))
    }

    @Test
    fun `entrée malformée ou clé absente valent null`() {
        assertNull(reader.decrypt("pas du json"))
        assertNull(reader.decrypt("{}"))
        assertNull(reader.decrypt("""{"ct":"","iv":"","scheme":"aes"}"""))
        assertNull(reader.decrypt("""{"ct":"AAAA","iv":"AAAA","scheme":"rsa"}"""))
        assertNull(reader.decrypt("""{"ct":"%%%","iv":"AAAA","scheme":"aes"}"""))
        // Mauvaise clé pour l'alias (tag GCM invalide).
        assertNull(reader.decrypt(encrypt("x", baseKey, usesSuffix = true)))
        // Alias inconnu du KeyStore.
        val noKeys = LegacySecretsReader({ prefs }, KeyStoreAccess { null })
        assertNull(noKeys.decrypt(encrypt("x", suffixedKey, usesSuffix = true)))
        // Préférences indisponibles ou en erreur.
        assertNull(LegacySecretsReader({ null }, keyStore).read("k"))
        assertNull(LegacySecretsReader({ error("boum") }, keyStore).read("k"))
    }

    @Test
    fun `le constructeur Hilt ne crée pas le fichier de préférences s'il n'existe pas`() {
        val reader = LegacySecretsReader(context)
        assertNull(reader.read("backup_s3_accessKeyId"))
        assertEquals(false, LegacyPaths(context).sharedPrefsDir.resolve(LegacyPaths.SECURE_STORE_PREFS_FILE).exists())
    }
}
