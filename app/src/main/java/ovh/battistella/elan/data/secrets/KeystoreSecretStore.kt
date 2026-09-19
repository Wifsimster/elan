package ovh.battistella.elan.data.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secrets chiffrés sur disque avec une clé AES-256 qui ne quitte jamais
 * l'AndroidKeyStore (alias `elan.secrets.v1`, GCM, sans authentification
 * utilisateur pour que la sauvegarde automatique marche en arrière-plan).
 *
 * Un secret = un fichier `files/secrets/<nom>.bin` contenant `iv || chiffré`
 * en Base64, écrit atomiquement ([AtomicFile]) : une coupure en pleine écriture
 * laisse l'ancienne valeur intacte plutôt qu'un blob tronqué illisible.
 *
 * Un blob illisible (clé perdue après restauration système, fichier corrompu)
 * vaut « pas de secret » : l'utilisateur ressaisit ses identifiants, l'app ne
 * plante pas au démarrage.
 */
@Singleton
class KeystoreSecretStore internal constructor(
    private val dir: File,
    private val keyProvider: () -> SecretKey,
    private val io: CoroutineDispatcher,
) : SecretStore {

    @Inject
    constructor(@ApplicationContext context: Context, io: CoroutineDispatcher) :
        this(File(context.filesDir, DIR_NAME), ::keystoreKey, io)

    override suspend fun get(name: String): String? = withContext(io) {
        val file = fileFor(name)
        if (!file.exists()) return@withContext null
        try {
            val blob = Base64.decode(AtomicFile(file).readFully(), Base64.NO_WRAP)
            if (blob.size <= IV_BYTES) return@withContext null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider(),
                GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES),
            )
            String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            // Base64 invalide
            null
        }
    }

    override suspend fun put(name: String, value: String) = withContext(io) {
        dir.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Pas d'IV fourni : le KeyStore en tire un aléatoire (obligatoire avec
        // `setRandomizedEncryptionRequired`), qu'on stocke en tête du blob.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "IV GCM inattendu : ${iv.size} octets" }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = Base64.encode(iv + encrypted, Base64.NO_WRAP)

        val atomic = AtomicFile(fileFor(name))
        val out = atomic.startWrite()
        try {
            out.write(blob)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    override suspend fun remove(name: String) = withContext(io) {
        AtomicFile(fileFor(name)).delete()
    }

    private fun fileFor(name: String): File {
        // Le nom devient un nom de fichier : on refuse tout ce qui pourrait
        // sortir du dossier (séparateurs, `..`).
        require(SAFE_NAME.matches(name)) { "nom de secret invalide : $name" }
        return File(dir, "$name.bin")
    }

    companion object {
        const val KEY_ALIAS = "elan.secrets.v1"
        private const val DIR_NAME = "secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val SAFE_NAME = Regex("[A-Za-z0-9_.-]+")

        /** Clé AES-256 du KeyStore Android, générée au premier appel. */
        fun keystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
