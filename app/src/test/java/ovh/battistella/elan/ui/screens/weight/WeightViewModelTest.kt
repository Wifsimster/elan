package ovh.battistella.elan.ui.screens.weight

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WeightViewModelTest {

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

    private fun vm() = WeightViewModel(repos.bodyWeight, repos.settings, Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()))

    @Test
    fun `parseWeightInput - virgule, point, arrondi au dixième, bornes`() {
        assertEquals(76.4, parseWeightInput("76,4"))
        assertEquals(76.4, parseWeightInput("76.4"))
        assertEquals(76.0, parseWeightInput(" 76 "))
        assertEquals(76.5, parseWeightInput("76,46"))
        assertEquals(20.0, parseWeightInput("20"))
        assertEquals(300.0, parseWeightInput("300"))
        assertNull(parseWeightInput("19,9"))
        assertNull(parseWeightInput("300,1"))
        assertNull(parseWeightInput(""))
        assertNull(parseWeightInput("abc"))
        assertNull(parseWeightInput("NaN"))
    }

    @Test
    fun `formats - kg et delta signé`() {
        assertEquals("76", fmtWeight(76.0))
        assertEquals("76,4", fmtWeight(76.4))
        assertEquals("+0,4", fmtWeightDelta(0.4))
        assertEquals("−1,2", fmtWeightDelta(-1.2))
        assertEquals("0", fmtWeightDelta(0.0))
    }

    @Test
    fun `pré-remplissage depuis le profil puis depuis la dernière pesée`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals("70", vm.ui.value.input)
        assertTrue(vm.ui.value.items.orEmpty().isEmpty())

        vm.setInput("76,4")
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.ui.value.error)
        assertEquals(1, vm.ui.value.items?.size)
        assertEquals(76.4, vm.ui.value.latest?.weightKg)
        assertEquals("76,4", vm.ui.value.input)
        // Le profil suit la dernière pesée.
        assertEquals(76.4, repos.settings.getProfile().weightKg, 0.0)
    }

    @Test
    fun `saisie invalide - message d'erreur, rien d'écrit`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.setInput("12")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.ui.value.error)
        assertTrue(vm.ui.value.items.orEmpty().isEmpty())

        vm.setInput("80")
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.ui.value.error)
    }

    @Test
    fun `saisie limitée à 6 caractères`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        vm.setInput("123,4567")
        assertEquals("123,45", vm.ui.value.input)
    }

    @Test
    fun `deltas 30 jours et depuis le début`() = runTest(mainDispatcher.dispatcher) {
        repos.bodyWeight.logBodyWeight(80.0, now - 90 * day)
        repos.bodyWeight.logBodyWeight(78.5, now - 31 * day)
        repos.bodyWeight.logBodyWeight(78.0, now - 10 * day)
        repos.bodyWeight.logBodyWeight(77.2, now)
        val vm = vm()
        advanceUntilIdle()

        val ui = vm.ui.value
        assertEquals(77.2, ui.latest?.weightKg)
        assertEquals(78.5, ui.monthAgo?.weightKg)
        assertEquals(-1.3, ui.deltaMonth!!, 1e-9)
        assertEquals(-2.8, ui.deltaSinceStart!!, 1e-9)
    }

    @Test
    fun `sans pesée de référence à 30 jours - delta absent`() = runTest(mainDispatcher.dispatcher) {
        repos.bodyWeight.logBodyWeight(78.0, now - 10 * day)
        repos.bodyWeight.logBodyWeight(77.2, now)
        val vm = vm()
        advanceUntilIdle()
        assertNull(vm.ui.value.deltaMonth)
        assertEquals(-0.8, vm.ui.value.deltaSinceStart!!, 1e-9)
    }

    @Test
    fun `suppression avec confirmation`() = runTest(mainDispatcher.dispatcher) {
        repos.bodyWeight.logBodyWeight(78.0, now - day)
        repos.bodyWeight.logBodyWeight(77.2, now)
        val vm = vm()
        advanceUntilIdle()
        val latest = vm.ui.value.latest!!

        vm.requestDelete(latest)
        assertEquals(latest, vm.ui.value.pendingDelete)
        vm.dismissDelete()
        assertNull(vm.ui.value.pendingDelete)
        assertEquals(2, vm.ui.value.items?.size)

        vm.requestDelete(latest)
        vm.confirmDelete()
        advanceUntilIdle()
        assertNull(vm.ui.value.pendingDelete)
        assertEquals(1, vm.ui.value.items?.size)
        assertEquals(78.0, vm.ui.value.latest?.weightKg)
        assertEquals(78.0, repos.settings.getProfile().weightKg, 0.0)
    }
}
