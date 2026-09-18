package ovh.battistella.elan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.legacy.ImportOutcome
import ovh.battistella.elan.data.legacy.LegacyDatabaseImporter
import ovh.battistella.elan.data.legacy.MigrationGate
import ovh.battistella.elan.data.legacy.MigrationState
import ovh.battistella.elan.tracking.RecoveryOutcome
import ovh.battistella.elan.tracking.SessionRecovery
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
class StartupTasksTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val importer = mockk<LegacyDatabaseImporter>()
    private val recovery = mockk<SessionRecovery>().also {
        coEvery { it.recoverOrphans() } returns RecoveryOutcome(0, 0)
        every { it.reconcileOrphanService(any()) } returns Unit
    }

    private fun tasks(gate: MigrationGate) = StartupTasks(context, gate, importer, recovery, Clock.systemUTC())

    @Test
    fun `la barrière de migration passe en premier puis la purge différée`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.NothingToDo
        coEvery { importer.cleanupIfDue(any()) } returns Unit
        val gate = MigrationGate(importer, this)

        tasks(gate).run()

        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 1) { importer.runIfNeeded(any()) }
        coVerify(exactly = 1) { importer.cleanupIfDue(any()) }
    }

    @Test
    fun `une fois la base prête les séances orphelines et le service GPS sont réconciliés`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.NothingToDo
        coEvery { importer.cleanupIfDue(any()) } returns Unit
        val gate = MigrationGate(importer, this)

        tasks(gate).run()

        coVerify(exactly = 1) { recovery.recoverOrphans() }
        verify(exactly = 1) { recovery.reconcileOrphanService(context) }
    }

    @Test
    fun `tant que la migration n'est pas réglée rien d'autre ne tourne`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.Failed("boum", 1, retriable = false)
        val gate = MigrationGate(importer, this)

        tasks(gate).run()

        assertEquals(MigrationState.Failed("boum", canRetry = false), gate.state.value)
        coVerify(exactly = 0) { importer.cleanupIfDue(any()) }
        coVerify(exactly = 0) { recovery.recoverOrphans() }
    }

    @Test
    fun `une purge qui échoue n'empêche pas le démarrage`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.NothingToDo
        coEvery { importer.cleanupIfDue(any()) } throws IllegalStateException("disque")
        val gate = MigrationGate(importer, this)
        tasks(gate).run()
        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 1) { recovery.recoverOrphans() }
    }
}
