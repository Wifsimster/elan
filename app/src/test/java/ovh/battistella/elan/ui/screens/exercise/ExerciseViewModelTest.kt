package ovh.battistella.elan.ui.screens.exercise

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.ProgressionAdvice
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(name: String) = ExerciseViewModel(SavedStateHandle(mapOf("name" to name)), repos.sessions)

    private suspend fun seed(exercise: String, startedAt: Long, sets: List<Pair<Int, Double>>, difficulty: Difficulty?) {
        repos.sessions.saveMuscuSession(
            id = null,
            startedAt = startedAt,
            patch = SessionUpdate { endedAt = startedAt + 1_800_000; durationSec = 1800 },
            sets = sets.mapIndexed { i, (reps, kg) -> TestSupport.muscuSetInput(exercise, i + 1, reps, kg, difficulty) },
        )
    }

    @Test
    fun `guide - programme d'abord, puis catalogue, sinon aucun`() {
        val program = resolveGuide("Goblet squat")!!
        assertEquals("goblet-squat", program.imageKey)
        assertTrue(program.howTo.startsWith("Tiens un haltère"))
        assertEquals(listOf("Quadriceps", "Fessiers", "Adducteurs", "Gainage"), program.muscles)

        val catalog = resolveGuide("Squat barre")!!
        assertNotNull(catalog.icon)
        assertTrue(catalog.muscles.isNotEmpty())
        assertTrue(catalog.howTo.isNotEmpty())

        assertNull(resolveGuide("Mon exo perso"))
    }

    @Test
    fun `historique, records, 1RM et conseil`() = runTest(mainDispatcher.dispatcher) {
        seed("Goblet squat", now - 20 * day, listOf(10 to 20.0, 10 to 20.0), Difficulty.MOYEN)
        seed("Goblet squat", now - 10 * day, listOf(8 to 22.5, 8 to 22.5, 6 to 25.0), Difficulty.FACILE)
        seed("Goblet squat", now - 3 * day, listOf(10 to 22.5), Difficulty.FACILE)

        val vm = vm("Goblet squat")
        assertNull(vm.ui.value.points)
        advanceUntilIdle()
        val ui = vm.ui.value
        assertEquals(3, ui.points!!.size)
        assertEquals(25.0, ui.best, 1e-9)
        assertEquals(20.0, ui.first, 1e-9)
        assertEquals(22.5, ui.last, 1e-9)
        assertEquals(2.5, ui.delta, 1e-9)

        // 1RM Epley : record 25 × (1 + 6/30) = 30 ; actuel 22,5 × (1 + 10/30) = 30.
        assertEquals(30.0, ui.best1rm, 1e-9)
        assertEquals(30.0, ui.last1rm, 1e-9)

        assertEquals(3, ui.bars.size)
        assertEquals(listOf(20.0, 25.0, 22.5), ui.bars.map { it.value })

        assertTrue(ui.hasRating)
        assertEquals(ProgressionAdvice.AUGMENTE, ui.advice)
        assertEquals(Difficulty.FACILE, ui.lastRating)
        assertNotNull(ui.guide)
    }

    @Test
    fun `sans ressenti - pas de conseil, gainage - 1RM nul`() = runTest(mainDispatcher.dispatcher) {
        seed("Gainage planche", now - 2 * day, listOf(30 to 0.0), null)
        val vm = vm("Gainage planche")
        advanceUntilIdle()
        val ui = vm.ui.value
        assertFalse(ui.hasRating)
        assertEquals(ProgressionAdvice.MAINTIENS, ui.advice)
        assertNull(ui.lastRating)
        assertEquals(0.0, ui.best1rm, 1e-9)
    }

    @Test
    fun `dix dernières séances seulement dans la courbe`() = runTest(mainDispatcher.dispatcher) {
        repeat(12) { i -> seed("Squat", now - (12 - i) * day, listOf(5 to (50.0 + i)), null) }
        val vm = vm("Squat")
        advanceUntilIdle()
        assertEquals(10, vm.ui.value.bars.size)
        assertEquals(52.0, vm.ui.value.bars.first().value, 1e-9)
        assertEquals(61.0, vm.ui.value.bars.last().value, 1e-9)
    }

    @Test
    fun `exercice inconnu - liste vide sans requête`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm("")
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.ui.value.points)
    }
}
