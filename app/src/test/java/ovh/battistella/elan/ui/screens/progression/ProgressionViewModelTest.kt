package ovh.battistella.elan.ui.screens.progression

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.isoWeekKey
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProgressionViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val zone: ZoneId = ZoneId.systemDefault()
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

    private fun vm() = ProgressionViewModel(repos.sessions, repos.settings, Clock.fixed(Instant.ofEpochMilli(now), zone))

    private suspend fun seed(exercise: String, startedAt: Long, weightKg: Double, difficulty: Difficulty?) {
        repos.sessions.saveMuscuSession(
            id = null,
            startedAt = startedAt,
            patch = SessionUpdate { endedAt = startedAt + 1_800_000; durationSec = 1800 },
            sets = listOf(TestSupport.muscuSetInput(exercise, 1, 10, weightKg, difficulty)),
        )
    }

    @Test
    fun `chargement - null puis liste vide`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        assertNull(vm.ui.value.items)
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.ui.value.items)
        assertTrue(vm.ui.value.changes.isEmpty())
    }

    @Test
    fun `index des exercices - plus récents d'abord, dernier état`() = runTest(mainDispatcher.dispatcher) {
        seed("Goblet squat", now - 10 * day, 20.0, Difficulty.MOYEN)
        seed("Goblet squat", now - 3 * day, 22.5, Difficulty.FACILE)
        seed("Tractions", now - 5 * day, 0.0, Difficulty.DUR)

        val vm = vm()
        advanceUntilIdle()
        val items = vm.ui.value.items!!
        assertEquals(listOf("Goblet squat", "Tractions"), items.map { it.exercise })
        assertEquals(2, items[0].sessions)
        assertEquals(22.5, items[0].lastWeightKg, 1e-9)
        assertEquals(Difficulty.FACILE, items[0].lastDifficulty)
        assertEquals(Difficulty.DUR, items[1].lastDifficulty)
    }

    @Test
    fun `changements de la semaine - seulement si l'état date de la semaine courante`() = runTest(mainDispatcher.dispatcher) {
        val change = ProgressionChange("Goblet squat", ProgressKind.LOAD, 20.0, 22.5, Direction.UP)
        repos.settings.setAutoProgressionState(AutoProgressionState(week = "2020-W01", changes = listOf(change)))
        val stale = vm()
        advanceUntilIdle()
        assertTrue(stale.ui.value.changes.isEmpty())

        repos.settings.setAutoProgressionState(AutoProgressionState(week = isoWeekKey(now, zone), changes = listOf(change)))
        val fresh = vm()
        advanceUntilIdle()
        assertEquals(listOf(change), fresh.ui.value.changes)

        // Rafraîchi au focus.
        repos.settings.setAutoProgressionState(AutoProgressionState(week = isoWeekKey(now, zone), changes = emptyList(), dismissed = true))
        fresh.refresh()
        advanceUntilIdle()
        assertTrue(fresh.ui.value.changes.isEmpty())
    }
}
