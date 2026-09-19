package ovh.battistella.elan.sync

import androidx.work.ListenableWorker.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.elan.data.backup.BackupConfigFull
import ovh.battistella.elan.data.backup.BackupException
import ovh.battistella.elan.data.backup.BackupManager
import ovh.battistella.elan.data.settings.BackupLast

/** Le cœur du worker (`execute`) sur un gestionnaire factice : sans WorkManager ni Android. */
class BackupWorkerTest {

    private val manager = mockk<BackupManager>()
    private val complete = BackupConfigFull(
        enabled = true, endpoint = "https://s3.x.tld", bucket = "elan", accessKeyId = "AK", secretAccessKey = "SK",
    )

    @Test
    fun `sauvegarde désactivée ou incomplète → succès sans rien faire`() = runTest {
        coEvery { manager.currentConfig() } returns complete.copy(enabled = false)
        assertEquals(Result.success(), BackupWorker.execute(manager, 0))

        coEvery { manager.currentConfig() } returns complete.copy(secretAccessKey = "")
        assertEquals(Result.success(), BackupWorker.execute(manager, 0))

        coVerify(exactly = 0) { manager.runBackup(any()) }
    }

    @Test
    fun `sauvegarde réussie → succès`() = runTest {
        coEvery { manager.currentConfig() } returns complete
        coEvery { manager.runBackup(complete) } returns BackupLast(1, true)
        assertEquals(Result.success(), BackupWorker.execute(manager, 0))
        coVerify(exactly = 1) { manager.runBackup(complete) }
    }

    @Test
    fun `échec → réessai tant que le budget de tentatives n'est pas épuisé`() = runTest {
        coEvery { manager.currentConfig() } returns complete
        coEvery { manager.runBackup(any()) } throws BackupException("Serveur injoignable")
        assertEquals(Result.retry(), BackupWorker.execute(manager, 0))
        assertEquals(Result.retry(), BackupWorker.execute(manager, 1))
        assertEquals(Result.failure(), BackupWorker.execute(manager, BackupWorker.MAX_ATTEMPTS - 1))
    }

    @Test
    fun `config illisible → échec sans réessai`() = runTest {
        coEvery { manager.currentConfig() } throws IllegalStateException("base fermée")
        assertEquals(Result.failure(), BackupWorker.execute(manager, 0))
    }
}
