package ovh.battistella.elan.tracking

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transposition de `use-stopwatch.ts` : temps virtuel, horloge calée dessus. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchTest {

    private fun TestScope.stopwatch() = Stopwatch(SchedulerClock(testScheduler, 1_000_000L), backgroundScope)

    @Test
    fun `démarre à zéro et compte les secondes entières`() = runTest {
        val w = stopwatch()
        assertEquals(0, w.elapsedSec.value)
        assertFalse(w.running.value)

        w.start()
        assertTrue(w.running.value)
        advanceTimeBy(2_600)
        runCurrent()

        assertEquals(2, w.elapsedSec.value)
        assertEquals(2, w.getElapsedSec())
    }

    @Test
    fun `le tick rafraîchit toutes les 250 ms`() = runTest {
        val w = stopwatch()
        w.start()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, w.elapsedSec.value) // dernier tick à 750 ms : 0 s entière
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, w.elapsedSec.value) // tick à 1 000 ms
    }

    @Test
    fun `la pause fige le temps et la reprise enchaîne`() = runTest {
        val w = stopwatch()
        w.start()
        advanceTimeBy(3_100)
        runCurrent()

        w.pause()
        assertFalse(w.running.value)
        assertEquals(3, w.elapsedSec.value)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, w.elapsedSec.value)
        assertEquals(3, w.getElapsedSec())

        w.start()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(5, w.getElapsedSec())
    }

    @Test
    fun `getElapsedSec lit l'horloge en direct, entre deux ticks`() = runTest {
        val w = stopwatch()
        w.start()
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(1, w.elapsedSec.value)
        // Sans faire courir le tick : la valeur live avance quand même.
        testScheduler.advanceTimeBy(900)
        assertEquals(2, w.getElapsedSec())
    }

    @Test
    fun `reset remet à zéro et arrête`() = runTest {
        val w = stopwatch()
        w.start()
        advanceTimeBy(5_000)
        runCurrent()

        w.reset()

        assertEquals(0, w.elapsedSec.value)
        assertFalse(w.running.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, w.getElapsedSec())
    }

    @Test
    fun `seed pré-charge sans démarrer et le start suivant enchaîne`() = runTest {
        val w = stopwatch()
        w.seed(120)
        assertEquals(120, w.elapsedSec.value)
        assertFalse(w.running.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(120, w.getElapsedSec())

        w.start()
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(124, w.getElapsedSec())
    }

    @Test
    fun `seed négatif est ramené à zéro et start est idempotent`() = runTest {
        val w = stopwatch()
        w.seed(-5)
        assertEquals(0, w.elapsedSec.value)

        w.start()
        advanceTimeBy(2_000)
        w.start() // ne redémarre pas le décompte
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(4, w.getElapsedSec())
    }
}
