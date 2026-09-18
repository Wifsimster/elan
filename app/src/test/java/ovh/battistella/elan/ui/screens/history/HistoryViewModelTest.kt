package ovh.battistella.elan.ui.screens.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

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

    private fun vm() = HistoryViewModel(repos.sessions, Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()))

    private suspend fun seed(type: ActivityType, daysAgo: Int, notes: String? = null) =
        db.sessionDao().insert(TestSupport.session(type = type, startedAt = now - daysAgo * day, notes = notes))

    @Test
    fun `première page de 50 puis pagination`() = runTest(mainDispatcher.dispatcher) {
        repeat(60) { seed(ActivityType.VELO, it) }
        val vm = vm()
        advanceUntilIdle()

        assertTrue(vm.ui.value.loaded)
        assertEquals(50, vm.ui.value.sessions.size)
        assertTrue(vm.ui.value.hasMore)
        assertFalse(vm.ui.value.loading)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(60, vm.ui.value.sessions.size)
        assertFalse(vm.ui.value.hasMore)

        // Plus rien à charger : appel sans effet.
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(60, vm.ui.value.sessions.size)
    }

    @Test
    fun `filtre par type`() = runTest(mainDispatcher.dispatcher) {
        seed(ActivityType.VELO, 1)
        seed(ActivityType.MUSCU, 2)
        seed(ActivityType.COURSE, 3)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(3, vm.ui.value.sessions.size)
        assertFalse(vm.ui.value.filtered)

        vm.setTypeFilter(ActivityType.MUSCU)
        advanceUntilIdle()
        assertEquals(listOf(ActivityType.MUSCU), vm.ui.value.sessions.map { it.type })
        assertTrue(vm.ui.value.filtered)

        vm.setTypeFilter(null)
        advanceUntilIdle()
        assertEquals(3, vm.ui.value.sessions.size)
    }

    @Test
    fun `plage de dates`() = runTest(mainDispatcher.dispatcher) {
        seed(ActivityType.VELO, 2)
        seed(ActivityType.VELO, 20)
        seed(ActivityType.VELO, 60)
        seed(ActivityType.VELO, 200)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(4, vm.ui.value.sessions.size)

        vm.setRange(HistoryRange.DAYS_7)
        advanceUntilIdle()
        assertEquals(1, vm.ui.value.sessions.size)

        vm.setRange(HistoryRange.DAYS_30)
        advanceUntilIdle()
        assertEquals(2, vm.ui.value.sessions.size)

        vm.setRange(HistoryRange.DAYS_90)
        advanceUntilIdle()
        assertEquals(3, vm.ui.value.sessions.size)

        vm.setRange(HistoryRange.ALL)
        advanceUntilIdle()
        assertEquals(4, vm.ui.value.sessions.size)
    }

    @Test
    fun `recherche avec debounce de 200 ms`() = runTest(mainDispatcher.dispatcher) {
        seed(ActivityType.VELO, 1, notes = "col du Galibier")
        seed(ActivityType.VELO, 2, notes = "tour du lac")
        val vm = vm()
        advanceUntilIdle()
        assertEquals(2, vm.ui.value.sessions.size)

        vm.setSearchInput("gali")
        advanceTimeBy(100)
        // Sous les 200 ms : rien ne bouge encore.
        assertEquals(2, vm.ui.value.sessions.size)
        assertEquals("gali", vm.ui.value.searchInput)

        advanceTimeBy(150)
        advanceUntilIdle()
        assertEquals(1, vm.ui.value.sessions.size)
        assertEquals("col du Galibier", vm.ui.value.sessions.single().notes)
        assertTrue(vm.ui.value.filtered)

        // Effacement : retour immédiat à la liste complète.
        vm.setSearchInput("")
        advanceUntilIdle()
        assertEquals(2, vm.ui.value.sessions.size)
    }

    @Test
    fun `frappes rapprochées - une seule requête après la dernière`() = runTest(mainDispatcher.dispatcher) {
        seed(ActivityType.VELO, 1, notes = "col du Galibier")
        seed(ActivityType.VELO, 2, notes = "tour du lac")
        val vm = vm()
        advanceUntilIdle()

        vm.setSearchInput("g")
        advanceTimeBy(100)
        vm.setSearchInput("ga")
        advanceTimeBy(100)
        vm.setSearchInput("lac")
        advanceTimeBy(100)
        assertEquals(2, vm.ui.value.sessions.size)
        advanceUntilIdle()
        assertEquals("tour du lac", vm.ui.value.sessions.single().notes)
    }

    @Test
    fun `état vide absolu vs filtré`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertTrue(vm.ui.value.loaded)
        assertTrue(vm.ui.value.sessions.isEmpty())
        assertFalse(vm.ui.value.filtered)
        assertFalse(vm.ui.value.hasMore)

        vm.setRange(HistoryRange.DAYS_7)
        advanceUntilIdle()
        assertTrue(vm.ui.value.filtered)
    }

    @Test
    fun `refresh recharge la première page`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(0, vm.ui.value.sessions.size)

        seed(ActivityType.MARCHE, 1)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.ui.value.sessions.size)
    }
}
