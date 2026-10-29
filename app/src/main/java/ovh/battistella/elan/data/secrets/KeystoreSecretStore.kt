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
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

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
            null
        } catch (e: IOException) {
            null
        } catch (e: ProviderException) {
            null
        }
    }

    override suspend fun put(name: String, value: String) = withContext(io) {
        dir.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
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
