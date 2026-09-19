package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.backup.BackupManager
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.secrets.InMemorySecretStore
import ovh.battistella.elan.data.secrets.SecretStore
import ovh.battistella.elan.data.settings.BackupLast
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val complete = BackupFormConfig(endpoint = "https://s3.test", bucket = "elan", accessKeyId = "AK", secretAccessKey = "SK")

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** État courant après propagation des flux (combine sur le dispatcher de test). */
    private fun TestScope.ui(vm: BackupViewModel): BackupUi {
        advanceUntilIdle()
        return vm.ui.value
    }

    private fun TestScope.vm(port: BackupPort): BackupViewModel {
        val vm = BackupViewModel(port, repos.settings)
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `config incomplète - boutons désactivés, complète - prêts`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeBackupPort()
        val vm = vm(port)
        assertTrue(ui(vm).loaded)
        assertFalse(ui(vm).ready)

        vm.update(BackupPatch(endpoint = "https://s3.test", bucket = "elan", accessKeyId = "AK"))
        advanceUntilIdle()
        assertFalse(ui(vm).ready)

        vm.update(BackupPatch(secretAccessKey = "SK"))
        advanceUntilIdle()
        assertTrue(ui(vm).ready)
        assertEquals("elan", ui(vm).config.bucket)
        // Un patch vide n'est pas transmis.
        vm.update(BackupPatch())
        advanceUntilIdle()
        assertEquals(2, port.patches.size)
    }

    @Test
    fun `saisie - le champ suit la frappe pendant l'écriture, puis le persisté reprend la main`() = runTest(mainDispatcher.dispatcher) {
        // Port dont chaque écriture attend un feu vert : simule l'aller-retour Room.
        val gate = CompletableDeferred<Unit>()
        val port = object : FakeBackupPort() {
            override suspend fun updateConfig(patch: BackupPatch) {
                gate.await()
                super.updateConfig(patch)
            }
        }
        val vm = vm(port)

        vm.update(BackupPatch(endpoint = "h"))
        vm.update(BackupPatch(endpoint = "ht"))
        vm.update(BackupPatch(bucket = "e"))
        advanceUntilIdle()
        // Rien n'est encore persisté, mais l'écran montre déjà la saisie complète.
        assertEquals("", port.state.value.endpoint)
        assertEquals("ht", ui(vm).config.endpoint)
        assertEquals("e", ui(vm).config.bucket)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(BackupPatch(endpoint = "h"), BackupPatch(endpoint = "ht"), BackupPatch(bucket = "e")), port.patches)
        assertEquals("ht", port.state.value.endpoint)
        assertEquals("e", port.state.value.bucket)
        assertEquals("ht", ui(vm).config.endpoint)
    }

    @Test
    fun `QR - patch appliqué et champs listés, QR inconnu signalé`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeBackupPort()
        val vm = vm(port)

        vm.onQrScanned("""{"Endpoint":"https://minio.lan","bucket":"elan","access_key":"AK","SecretKey":"SK"}""")
        advanceUntilIdle()
        val dialog = ui(vm).dialog as BackupDialog.QrImported
        assertEquals("endpoint, bucket, access key, secret key", dialog.fields)
        assertEquals("https://minio.lan", ui(vm).config.endpoint)
        assertEquals("SK", ui(vm).config.secretAccessKey)
        assertTrue(ui(vm).ready)
        // `enabled` n'est jamais renseigné par un QR.
        assertFalse(ui(vm).config.enabled)
        vm.dismissDialog()

        vm.onQrScanned("bonjour")
        assertEquals(BackupDialog.QrUnrecognized, ui(vm).dialog)
    }

    @Test
    fun `décodage QR - URL s3 et alias JSON`() {
        val url = parseBackupQrPatch("s3://AK:S%2FK@minio.lan/elan/backup.json")!!
        assertEquals("https://minio.lan", url.endpoint)
        assertEquals("elan", url.bucket)
        assertEquals("backup.json", url.objectKey)
        assertEquals("AK", url.accessKeyId)
        assertEquals("S/K", url.secretAccessKey)
        assertNull(url.region)

        val json = parseBackupQrPatch("""{"url":" https://x.y ","aws_access_key_id":"A","region":"eu-west-3","object":""}""")!!
        assertEquals("https://x.y", json.endpoint)
        assertEquals("A", json.accessKeyId)
        assertEquals("eu-west-3", json.region)
        assertNull(json.objectKey)
        assertNull(parseBackupQrPatch("{}"))
        assertNull(parseBackupQrPatch("s3://"))
        assertNull(parseBackupQrPatch(""))
    }

    @Test
    fun `sauvegarde immédiate - statut, erreur lisible, dernier statut lu des réglages`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeBackupPort(complete)
        val vm = vm(port)
        assertNull(ui(vm).last)

        vm.backupNow()
        advanceUntilIdle()
        assertEquals(1, port.backups)
        assertNull(ui(vm).error)
        assertEquals(BackupStatus.Idle, ui(vm).status)

        repos.settings.setBackupLast(BackupLast(at = 1_700_000_000_000L, ok = false, error = "AccessDenied"))
        advanceUntilIdle()
        assertEquals(false, ui(vm).last?.ok)

        port.failure = IllegalStateException("Identifiants S3 refusés (SignatureDoesNotMatch).")
        vm.backupNow()
        advanceUntilIdle()
        assertEquals("Identifiants S3 refusés (SignatureDoesNotMatch).", ui(vm).error)
        assertEquals(BackupStatus.Idle, ui(vm).status)
    }

    @Test
    fun `restauration depuis la carte - confirmation puis dialogue avec le nombre de séances`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeBackupPort(complete)
        val vm = vm(port)

        vm.requestRestore()
        assertEquals(BackupDialog.ConfirmRestore, ui(vm).dialog)
        assertEquals(0, port.restores)

        vm.dismissDialog()
        assertEquals(BackupDialog.None, ui(vm).dialog)
        assertEquals(0, port.restores)

        vm.requestRestore()
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(1, port.restores)
        assertEquals(BackupDialog.RestoreDone(12), ui(vm).dialog)
        // Pas d'activation automatique depuis les Réglages.
        assertTrue(port.patches.none { it.enabled == true })
    }

    @Test
    fun `restauration premier lancement - active la sauvegarde auto et émet l'événement`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeBackupPort(complete)
        val vm = vm(port)
        val events = mutableListOf<BackupEvent>()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.restoreNow()
        advanceUntilIdle()
        assertEquals(listOf<BackupEvent>(BackupEvent.Restored(12)), events)
        assertTrue(ui(vm).config.enabled)
        assertEquals(BackupDialog.None, ui(vm).dialog)

        port.failure = IllegalStateException("Aucune sauvegarde trouvée sur le serveur.")
        vm.restoreNow()
        advanceUntilIdle()
        assertEquals("Aucune sauvegarde trouvée sur le serveur.", ui(vm).error)
        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun `port réel - config lue des réglages et secrets du SecretStore, clé ressaisie lève l'invitation`() = runTest(mainDispatcher.dispatcher) {
        val secrets: SecretStore = InMemorySecretStore()
        repos.settings.setSetting(SettingsRepository.Keys.BACKUP_SECRETS_MISSING, "1")
        val context: Context = ApplicationProvider.getApplicationContext()
        val port = BackupManagerPort(BackupManager(context, repos.settings, secrets, repos.snapshot, mockk(), Clock.systemUTC()))
        val vm = vm(port)
        assertTrue(ui(vm).secretsMissing)

        vm.update(BackupPatch(endpoint = "https://s3.test", bucket = "elan"))
        advanceUntilIdle()
        assertEquals("https://s3.test", repos.settings.snapshot().backupConfig.endpoint)
        assertTrue(ui(vm).secretsMissing)

        vm.update(BackupPatch(accessKeyId = "AK", secretAccessKey = "SK"))
        advanceUntilIdle()
        assertEquals("AK", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("SK", ui(vm).config.secretAccessKey)
        assertFalse(ui(vm).secretsMissing)
        assertTrue(ui(vm).ready)
        // Les secrets ne vont jamais dans la table `settings`.
        assertFalse(repos.settings.getSetting(SettingsRepository.Keys.BACKUP_S3)!!.contains("SK"))
    }
}
