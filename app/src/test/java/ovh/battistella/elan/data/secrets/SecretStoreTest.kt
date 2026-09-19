package ovh.battistella.elan.data.secrets

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
class SecretStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `InMemorySecretStore get put remove`() = runTest {
        val store = InMemorySecretStore()
        assertNull(store.get("a"))
        store.put("a", "1")
        store.put("b", "2")
        assertEquals("1", store.get("a"))
        store.put("a", "3")
        assertEquals("3", store.get("a"))
        store.remove("a")
        assertNull(store.get("a"))
        assertEquals("2", store.get("b"))
    }

    // ---- KeystoreSecretStore, chiffrement + fichiers avec une clé AES locale ---

    /** Clé AES-256 en mémoire : exerce tout le chemin chiffrement/fichier hors KeyStore Android. */
    private fun localKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun fileStore(dir: File, key: SecretKey = localKey()) =
        KeystoreSecretStore(dir, { key }, Dispatchers.Unconfined)

    private fun tempDir() = File(context.filesDir, "secrets-test-${System.nanoTime()}")

    @Test
    fun `chiffre sur disque et relit avec la même clé`() = runTest {
        val dir = tempDir()
        val key = localKey()
        val store = fileStore(dir, key)
        assertNull(store.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))

        store.put(SecretStore.BACKUP_S3_ACCESS_KEY_ID, "AKIAEXAMPLE")
        store.put(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY, "sécrét/très+long=")
        assertEquals("AKIAEXAMPLE", store.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("sécrét/très+long=", store.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))

        // Une nouvelle instance sur le même dossier et la même clé relit les fichiers.
        assertEquals("AKIAEXAMPLE", fileStore(dir, key).get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))

        // Rien en clair sur le disque : blob Base64 `iv || chiffré`, IV de 12 octets.
        val file = File(dir, "${SecretStore.BACKUP_S3_ACCESS_KEY_ID}.bin")
        assertTrue(file.exists())
        val raw = file.readText()
        assertFalse(raw.contains("AKIAEXAMPLE"))
        val blob = Base64.decode(raw, Base64.NO_WRAP)
        assertEquals(12 + "AKIAEXAMPLE".length + 16, blob.size) // iv + texte + tag GCM

        store.remove(SecretStore.BACKUP_S3_ACCESS_KEY_ID)
        assertFalse(file.exists())
        assertNull(store.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("sécrét/très+long=", store.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))
    }

    @Test
    fun `une autre clé ou un blob corrompu valent absence de secret`() = runTest {
        val dir = tempDir()
        fileStore(dir).put("s", "valeur")
        // Clé perdue (restauration système) : pas d'exception, juste null.
        assertNull(fileStore(dir).get("s"))

        val store = fileStore(dir)
        File(dir, "s.bin").writeText("pas du base64 !!")
        assertNull(store.get("s"))
        File(dir, "s.bin").writeText(Base64.encodeToString(ByteArray(5), Base64.NO_WRAP))
        assertNull(store.get("s"))
    }

    @Test
    fun `réécriture atomique la valeur précédente survit à un nouveau put`() = runTest {
        val dir = tempDir()
        val store = fileStore(dir)
        store.put("s", "v1")
        store.put("s", "v2")
        assertEquals("v2", store.get("s"))
        assertEquals(listOf("s.bin"), dir.list()!!.toList())
    }

    @Test
    fun `un nom de secret ne peut pas sortir du dossier`() = runTest {
        val store = fileStore(tempDir())
        for (bad in listOf("../etc", "a/b", "", "a b")) {
            try {
                store.put(bad, "x")
                fail("nom accepté : $bad")
            } catch (e: IllegalArgumentException) {
                // attendu
            }
        }
    }

    @Test
    fun `KeystoreSecretStore complet si l'AndroidKeyStore est disponible`() = runTest {
        val available = runCatching {
            KeyStore.getInstance("AndroidKeyStore").load(null)
        }.isSuccess
        assumeTrue("AndroidKeyStore indisponible sous Robolectric", available)

        val store = KeystoreSecretStore(context, Dispatchers.Unconfined)
        store.put("k", "v")
        assertEquals("v", store.get("k"))
        store.remove("k")
        assertNull(store.get("k"))
    }
}
