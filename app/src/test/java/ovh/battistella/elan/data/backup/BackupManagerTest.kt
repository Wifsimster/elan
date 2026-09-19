package ovh.battistella.elan.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.remote.S3Client
import ovh.battistella.elan.data.remote.S3Config
import ovh.battistella.elan.data.remote.S3Exception
import ovh.battistella.elan.data.secrets.InMemorySecretStore
import ovh.battistella.elan.data.secrets.SecretStore
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import ovh.battistella.elan.testing.TestSupport
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val secrets = InMemorySecretStore()
    private val s3 = mockk<S3Client>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_200_000_000L), ZoneOffset.UTC)
    private lateinit var manager: BackupManager

    private val complete = BackupConfigFull(
        enabled = true, endpoint = "https://s3.x.tld", bucket = "elan", accessKeyId = "AK", secretAccessKey = "SK",
    )

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        manager = BackupManager(context, repos.settings, secrets, repos.snapshot, s3, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Le faux S3 renvoie `json` au GET (ou rien si `null`). */
    private fun serverHolds(json: String?) {
        coEvery { s3.getObject(any(), any()) } answers {
            val dest = secondArg<File>()
            if (json == null) null else dest.also { it.parentFile?.mkdirs(); it.writeText(json) }
        }
    }

    private suspend fun expectFailure(block: suspend () -> Unit): String {
        try {
            block()
        } catch (e: BackupException) {
            return e.message!!
        }
        throw AssertionError("BackupException attendue")
    }

    // ---- config ----------------------------------------------------------

    @Test
    fun `effectiveConfig nettoie et complète, isConfigComplete exige les quatre champs`() {
        val c = BackupConfigFull(endpoint = " https://s3 ", bucket = " b ", accessKeyId = " a ", secretAccessKey = " s ", region = " ", objectKey = "")
        val e = effectiveConfig(c)
        assertEquals(BackupConfigFull(false, "https://s3", "us-east-1", "b", "elan-backup.json", "a", "s"), e)
        assertTrue(isConfigComplete(c))
        assertFalse(isConfigComplete(c.copy(secretAccessKey = "  ")))
        assertFalse(isConfigComplete(BackupConfigFull()))
    }

    @Test
    fun `les secrets vont dans le SecretStore, jamais dans le réglage`() = runTest {
        manager.updateConfig(BackupConfigPatch(endpoint = "https://s3.x.tld", bucket = "elan", accessKeyId = "AK", secretAccessKey = "SK", enabled = true))

        val raw = repos.settings.getSetting(Keys.BACKUP_S3)!!
        assertFalse(raw.contains("AK"))
        assertFalse(raw.contains("SK"))
        assertEquals("https://s3.x.tld", JSONObject(raw).getString("endpoint"))
        assertEquals("AK", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("SK", secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))
        assertEquals(complete, manager.currentConfig())
        assertEquals(complete, manager.config.first())

        // Un secret vidé est retiré du stockage sécurisé.
        manager.updateConfig(BackupConfigPatch(secretAccessKey = ""))
        assertNull(secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))
        assertEquals("AK", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
    }

    @Test
    fun `migre les secrets d'une config héritée restés dans le JSON`() = runTest {
        repos.settings.setSetting(
            Keys.BACKUP_S3,
            """{"enabled":true,"endpoint":"https://s3.x.tld","bucket":"elan","accessKeyId":"OLDAK","secretAccessKey":"OLDSK","region":"","objectKey":""}""",
        )

        val cfg = manager.currentConfig()

        assertEquals(complete.copy(accessKeyId = "OLDAK", secretAccessKey = "OLDSK"), cfg)
        assertEquals("OLDAK", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        val raw = repos.settings.getSetting(Keys.BACKUP_S3)!!
        assertFalse(raw.contains("OLDAK"))
        assertFalse(raw.contains("OLDSK"))
    }

    @Test
    fun `renseigner les deux identifiants efface le marqueur « secrets manquants »`() = runTest {
        repos.settings.setSetting(Keys.BACKUP_SECRETS_MISSING, "1")
        assertTrue(manager.secretsMissing())
        manager.updateConfig(BackupConfigPatch(accessKeyId = "AK"))
        assertTrue(manager.secretsMissing())
        manager.updateConfig(BackupConfigPatch(secretAccessKey = "SK"))
        assertFalse(manager.secretsMissing())
    }

    @Test
    fun `un patch QR ne touche jamais enabled`() {
        val patch = parseBackupQr("s3://AK:SK@s3.x.tld/elan")!!.toConfigPatch()
        assertNull(patch.enabled)
        assertEquals("https://s3.x.tld", patch.endpoint)
        assertEquals("AK", patch.accessKeyId)
    }

    // ---- sauvegarde ------------------------------------------------------

    @Test
    fun `runBackup refuse une config incomplète sans rien consigner`() = runTest {
        val msg = expectFailure { manager.runBackup() }
        assertEquals("Configuration S3 incomplète.", msg)
        assertNull(repos.settings.getSetting(Keys.BACKUP_LAST))
        coVerify(exactly = 0) { s3.putObject(any(), any()) }
    }

    @Test
    fun `runBackup téléverse l'enveloppe complète et consigne le succès`() = runTest {
        db.sessionDao().insert(TestSupport.session())
        repos.settings.setSetting(Keys.MAP_STYLE_URL, "https://tiles")
        val uploaded = slot<File>()
        var content = ""
        val cfgSlot = slot<S3Config>()
        coEvery { s3.putObject(capture(cfgSlot), capture(uploaded)) } answers { content = uploaded.captured.readText() }

        val last = manager.runBackup(complete.copy(endpoint = " https://s3.x.tld "))

        assertTrue(last.ok)
        assertEquals(1_700_200_000_000L, last.at)
        assertEquals(last, repos.settings.snapshot().backupLast)
        assertEquals("https://s3.x.tld", cfgSlot.captured.endpoint)
        assertFalse("fichier temporaire supprimé", uploaded.captured.exists())
        val env = BackupSnapshotCodec.read(java.io.StringReader(content))
        assertEquals("suivi-sport", env.app)
        assertEquals(1, env.format)
        assertEquals(7, env.schema)
        assertEquals(1, env.data!!.sessions.size)
        assertTrue("map_style_url exclue", env.data!!.settings.none { it.key == Keys.MAP_STYLE_URL })
    }

    @Test
    fun `runBackup consigne l'échec dans backup_last et le relance`() = runTest {
        coEvery { s3.putObject(any(), any()) } throws S3Exception("Le bucket « elan » n’existe pas sur ce serveur.")

        val msg = expectFailure { manager.runBackup(complete) }

        assertEquals("Le bucket « elan » n’existe pas sur ce serveur.", msg)
        val last = repos.settings.snapshot().backupLast!!
        assertFalse(last.ok)
        assertEquals(msg, last.error)
        assertEquals(1_700_200_000_000L, last.at)
    }

    @Test
    fun `autoBackup ne fait rien si désactivée et n'échoue jamais`() = runTest {
        manager.updateConfig(BackupConfigPatch(endpoint = "https://s3.x.tld", bucket = "elan", accessKeyId = "AK", secretAccessKey = "SK", enabled = false))
        manager.autoBackup()
        coVerify(exactly = 0) { s3.putObject(any(), any()) }

        manager.updateConfig(BackupConfigPatch(enabled = true))
        coEvery { s3.putObject(any(), any()) } throws S3Exception("boom")
        manager.autoBackup() // ne lève pas
        coVerify(exactly = 1) { s3.putObject(any(), any()) }
        assertEquals("boom", repos.settings.snapshot().backupLast!!.error)
    }

    // ---- restauration ----------------------------------------------------

    @Test
    fun `restoreBackup applique les contrôles dans l'ordre`() = runTest {
        assertEquals("Configuration S3 incomplète.", expectFailure { manager.restoreBackup() })

        serverHolds(null)
        assertEquals("Aucune sauvegarde trouvée sur le serveur.", expectFailure { manager.restoreBackup(complete) })

        serverHolds("{pas du json")
        assertEquals("Sauvegarde illisible (JSON invalide).", expectFailure { manager.restoreBackup(complete) })

        serverHolds("""{"format":1,"app":"autre","data":{}}""")
        assertEquals("Format de sauvegarde non reconnu.", expectFailure { manager.restoreBackup(complete) })

        serverHolds("""{"format":1,"app":"suivi-sport"}""")
        assertEquals("Format de sauvegarde non reconnu.", expectFailure { manager.restoreBackup(complete) })

        serverHolds("""{"format":2,"app":"suivi-sport","data":{"sessions":[]}}""")
        assertEquals(
            "Sauvegarde créée par une version plus récente (format 2). Mets l'application à jour.",
            expectFailure { manager.restoreBackup(complete) },
        )

        serverHolds("""{"format":1,"schema":8,"app":"suivi-sport","data":{"sessions":[]}}""")
        assertEquals(
            "Sauvegarde créée par une version plus récente (schéma 8). Mets l'application à jour.",
            expectFailure { manager.restoreBackup(complete) },
        )
        assertTrue(db.sessionDao().getAll().isEmpty())
    }

    @Test
    fun `restoreBackup remplace les données locales et renvoie le nombre de séances`() = runTest {
        db.sessionDao().insert(TestSupport.session(id = 99))
        repos.settings.setSetting(Keys.PRIVACY_ZONE_M, "0")
        repos.settings.setSetting(Keys.BACKUP_LAST, """{"at":1,"ok":true}""")
        serverHolds(
            """{"format":1,"app":"suivi-sport","exportedAt":1,"data":{""" +
                """"sessions":[{"id":3,"type":"velo","startedAt":1000,"endedAt":2000,"durationSec":1},""" +
                """{"id":4,"type":"muscu","startedAt":3000,"endedAt":4000,"durationSec":1}],""" +
                """"trackPoints":[{"id":1,"sessionId":3,"ts":1000,"lat":1.5,"lon":2.5}],""" +
                """"muscuSets":[],"settings":[{"key":"privacy_zone_m","value":"500"},{"key":"backup_last","value":"{\"at\":2,\"ok\":false}"}]}}""",
        )

        val n = manager.restoreBackup(complete)

        assertEquals(2, n)
        assertEquals(listOf(3L, 4L), db.sessionDao().getAll().map { it.id }.sorted())
        assertEquals(1, db.trackPointDao().getAll().size)
        assertEquals("500", repos.settings.getSetting(Keys.PRIVACY_ZONE_M))
        assertEquals("""{"at":1,"ok":true}""", repos.settings.getSetting(Keys.BACKUP_LAST))
    }

    @Test
    fun `le mutex sérialise sauvegarde et restauration`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val events = ArrayList<String>()
        coEvery { s3.getObject(any(), any()) } coAnswers {
            events.add("get:start")
            gate.await()
            events.add("get:end")
            null
        }
        coEvery { s3.putObject(any(), any()) } answers { events.add("put") }

        val restore = launch { runCatching { manager.restoreBackup(complete) } }
        advanceUntilIdle()
        val backup = launch { manager.runBackup(complete) }
        advanceUntilIdle()
        assertEquals(listOf("get:start"), events)

        gate.complete(Unit)
        advanceUntilIdle()
        restore.join()
        backup.join()
        assertEquals(listOf("get:start", "get:end", "put"), events)
    }
}
