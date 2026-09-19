package ovh.battistella.elan.data.legacy

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationGateTest {

    private val importer = mockk<LegacyDatabaseImporter>()
    private val counts = ImportCounts(1, 2, 3, 4, 5)

    private fun failed(attempt: Int, retriable: Boolean = true) =
        ImportOutcome.Failed("boum $attempt", attempt, retriable)

    @Test
    fun `rien à importer Pending puis Ready`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns ImportOutcome.NothingToDo
        val gate = MigrationGate(importer, this)
        assertEquals(MigrationState.Pending, gate.state.value)
        gate.start()
        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 1) { importer.runIfNeeded(any()) }
    }

    @Test
    fun `l'avancement de la copie est relayé`() = runTest {
        val gate = MigrationGate(importer, this)
        // Un StateFlow conflate : on relève l'état à chaque étape plutôt que de collecter.
        val seen = mutableListOf<MigrationState>()
        coEvery { importer.runIfNeeded(any()) } coAnswers {
            seen += gate.state.value
            val progress = firstArg<ImportProgress>()
            for ((copied, total) in listOf(0L to 10_000L, 5_000L to 10_000L, 10_000L to 10_000L)) {
                progress(copied, total)
                seen += gate.state.value
            }
            ImportOutcome.Imported(counts)
        }
        seen += gate.state.value
        gate.start()
        seen += gate.state.value
        assertEquals(
            listOf(
                MigrationState.Pending,
                MigrationState.Running(0, 0),
                MigrationState.Running(0, 10_000),
                MigrationState.Running(5_000, 10_000),
                MigrationState.Running(10_000, 10_000),
                MigrationState.Ready,
            ),
            seen,
        )
    }

    @Test
    fun `trois tentatives automatiques puis Failed rejouable`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returnsMany listOf(failed(1), failed(2), failed(3), failed(4))
        val gate = MigrationGate(importer, this)
        gate.start()
        assertEquals(MigrationState.Failed("boum 3", canRetry = true), gate.state.value)
        coVerify(exactly = 3) { importer.runIfNeeded(any()) }
    }

    @Test
    fun `un échec puis un succès donnent Ready`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returnsMany listOf(failed(1), ImportOutcome.Imported(counts))
        val gate = MigrationGate(importer, this)
        gate.start()
        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 2) { importer.runIfNeeded(any()) }
    }

    @Test
    fun `un échec non rejouable arrête tout de suite`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns failed(1, retriable = false)
        val gate = MigrationGate(importer, this)
        gate.start()
        assertEquals(MigrationState.Failed("boum 1", canRetry = false), gate.state.value)
        coVerify(exactly = 1) { importer.runIfNeeded(any()) }
    }

    @Test
    fun `retry rejoue une seule tentative`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returnsMany listOf(
            failed(1), failed(2), failed(3), failed(4), ImportOutcome.Imported(counts),
        )
        val gate = MigrationGate(importer, this)
        gate.start()
        assertEquals(MigrationState.Failed("boum 3", canRetry = true), gate.state.value)

        gate.retry().join()
        assertEquals(MigrationState.Failed("boum 4", canRetry = true), gate.state.value)
        coVerify(exactly = 4) { importer.runIfNeeded(any()) }

        gate.retry().join()
        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 5) { importer.runIfNeeded(any()) }

        // Une fois prête, la barrière ne relance plus rien.
        gate.retry().join()
        gate.start()
        coVerify(exactly = 5) { importer.runIfNeeded(any()) }
    }

    @Test
    fun `skip marque l'import fait et libère l'interface`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns failed(1, retriable = false)
        coEvery { importer.markSkipped() } returns Unit
        val gate = MigrationGate(importer, this)
        gate.start()
        gate.skip().join()
        assertEquals(MigrationState.Ready, gate.state.value)
        coVerify(exactly = 1) { importer.markSkipped() }
        coVerify(exactly = 1) { importer.runIfNeeded(any()) }
    }

    @Test
    fun `un skip qui échoue reste rejouable`() = runTest {
        coEvery { importer.runIfNeeded(any()) } returns failed(1)
        coEvery { importer.markSkipped() } throws IllegalStateException("disque en lecture seule")
        val gate = MigrationGate(importer, this)
        gate.start()
        gate.skip().join()
        assertEquals(MigrationState.Failed("disque en lecture seule", canRetry = true), gate.state.value)
    }
}
