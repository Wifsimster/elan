package ovh.battistella.elan

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.elan.data.legacy.ImportOutcome
import ovh.battistella.elan.data.legacy.LegacyDatabaseImporter
import ovh.battistella.elan.data.legacy.MigrationGate
import ovh.battistella.elan.data.legacy.MigrationState
import java.time.Clock

class StartupTasksTest {

    private val importer = mockk<LegacyDatabaseImporter>()

    @Test
    fun `la barrière de migration passe en premier puis la purge différée`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.NothingToDo
        coEvery { importer.cleanupIfDue(any()) } returns Unit
        val gate = MigrationGate(importer, this)

        StartupTasks(gate, importer, Clock.systemUTC()).run()

        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 1) { importer.runIfNeeded(any()) }
        coVerify(exactly = 1) { importer.cleanupIfDue(any()) }
    }

    @Test
    fun `tant que la migration n'est pas réglée rien d'autre ne tourne`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.Failed("boum", 1, retriable = false)
        val gate = MigrationGate(importer, this)

        StartupTasks(gate, importer, Clock.systemUTC()).run()

        assertEquals(MigrationState.Failed("boum", canRetry = false), gate.state.value)
        coVerify(exactly = 0) { importer.cleanupIfDue(any()) }
    }

    @Test
    fun `une purge qui échoue n'empêche pas le démarrage`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.NothingToDo
        coEvery { importer.cleanupIfDue(any()) } throws IllegalStateException("disque")
        val gate = MigrationGate(importer, this)
        StartupTasks(gate, importer, Clock.systemUTC()).run()
        assertEquals(MigrationState.Ready, gate.state.value)
    }
}
