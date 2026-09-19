package ovh.battistella.elan.ui.screens.outing

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.ui.screens.FakeCadencePort
import ovh.battistella.elan.ui.screens.FakeOutingPort

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OutingViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private fun vm(
        type: String = "velo",
        port: FakeOutingPort = FakeOutingPort(),
        cadence: FakeCadencePort = FakeCadencePort(),
    ) = OutingViewModel(SavedStateHandle(mapOf("type" to type)), port, cadence)

    @Test
    fun `type de la route - allure à pied vitesse à vélo repli vélo`() = runTest(mainDispatcher.dispatcher) {
        val course = vm("course")
        assertEquals(ActivityType.COURSE, course.type)
        backgroundScope.launch { course.ui.collect {} }
        advanceUntilIdle()
        assertTrue(course.ui.value.pace)
        assertEquals("Course à pied", course.ui.value.meta.label)

        val velo = vm("velo")
        backgroundScope.launch { velo.ui.collect {} }
        advanceUntilIdle()
        assertFalse(velo.ui.value.pace)

        assertEquals(ActivityType.VELO, vm("inconnu").type)
    }

    @Test
    fun `phase vers contrôles`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort()
        val vm = vm(port = port)
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertEquals(OutingControls.Start(requesting = false), vm.ui.value.controls)

        port.update { copy(phase = OutingPhase.Requesting) }
        advanceUntilIdle()
        assertEquals(OutingControls.Start(requesting = true), vm.ui.value.controls)

        port.update { copy(phase = OutingPhase.Active) }
        advanceUntilIdle()
        assertEquals(OutingControls.Running(paused = false, saving = false), vm.ui.value.controls)

        port.update { copy(phase = OutingPhase.Paused) }
        advanceUntilIdle()
        assertEquals(OutingControls.Running(paused = true, saving = false), vm.ui.value.controls)

        port.update { copy(phase = OutingPhase.Saving) }
        advanceUntilIdle()
        assertEquals(OutingControls.Running(paused = false, saving = true), vm.ui.value.controls)

        port.update { copy(phase = OutingPhase.SaveFailed) }
        advanceUntilIdle()
        assertEquals(OutingControls.SaveFailed, vm.ui.value.controls)
    }

    @Test
    fun `permission précise démarre, approximative ou refusée alerte`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort()
        val vm = vm("marche", port = port)
        backgroundScope.launch { vm.ui.collect {} }

        vm.onLocationPermission(fine = false, coarse = true)
        advanceUntilIdle()
        assertEquals(OutingAlert.Coarse, vm.ui.value.alert)
        assertTrue(port.calls.isEmpty())

        vm.dismissAlert()
        vm.onLocationPermission(fine = false, coarse = false)
        advanceUntilIdle()
        assertEquals(OutingAlert.Denied, vm.ui.value.alert)

        vm.dismissAlert()
        vm.onLocationPermission(fine = true, coarse = true)
        advanceUntilIdle()
        assertNull(vm.ui.value.alert)
        assertEquals(listOf("begin:marche"), port.calls)
    }

    @Test
    fun `pause reprise terminer avec confirmation`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Active))
        val vm = vm(port = port)
        backgroundScope.launch { vm.ui.collect {} }

        vm.pause()
        vm.resume()
        assertEquals(listOf("pause", "resume"), port.calls)

        vm.requestFinish()
        advanceUntilIdle()
        assertEquals(OutingDialog.Finish, vm.ui.value.dialog)
        vm.dismissDialog()
        advanceUntilIdle()
        assertEquals(OutingDialog.None, vm.ui.value.dialog)
        assertFalse("finish" in port.calls)

        vm.requestFinish()
        vm.confirmFinish()
        advanceUntilIdle()
        assertEquals(OutingDialog.None, vm.ui.value.dialog)
        assertTrue("finish" in port.calls)

        // En plein enregistrement : plus de confirmation empilée.
        port.update { copy(phase = OutingPhase.Saving) }
        advanceUntilIdle()
        vm.requestFinish()
        advanceUntilIdle()
        assertEquals(OutingDialog.None, vm.ui.value.dialog)
    }

    @Test
    fun `abandon - sortie directe en idle, confirmation sinon, ignoré en enregistrement`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort()
        val vm = vm(port = port)
        val events = mutableListOf<OutingEvent>()
        backgroundScope.launch { vm.ui.collect {} }
        val eventsJob = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.requestDiscard()
        advanceUntilIdle()
        assertEquals(listOf<OutingEvent>(OutingEvent.Exit), events)
        assertEquals(OutingDialog.None, vm.ui.value.dialog)
        assertTrue(port.calls.isEmpty())

        port.update { copy(phase = OutingPhase.Active) }
        advanceUntilIdle()
        vm.requestDiscard()
        advanceUntilIdle()
        assertEquals(OutingDialog.Discard, vm.ui.value.dialog)
        vm.confirmDiscard()
        advanceUntilIdle()
        assertEquals(listOf("discard"), port.calls)
        assertEquals(2, events.count { it == OutingEvent.Exit })

        port.update { copy(phase = OutingPhase.Saving) }
        advanceUntilIdle()
        vm.requestDiscard()
        advanceUntilIdle()
        assertEquals(OutingDialog.None, vm.ui.value.dialog)
        assertEquals(2, events.size)
        eventsJob.cancel()
    }

    @Test
    fun `échec d'enregistrement - alerte, abandon direct et réessai`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Saving, sessionId = 7))
        val vm = vm(port = port)
        val events = mutableListOf<OutingEvent>()
        backgroundScope.launch { vm.ui.collect {} }
        val eventsJob = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        port.update { copy(phase = OutingPhase.SaveFailed, errorMessage = "disque plein") }
        advanceUntilIdle()
        assertEquals(OutingAlert.SaveFailed, vm.ui.value.alert)
        assertEquals(OutingControls.SaveFailed, vm.ui.value.controls)

        vm.retrySave()
        assertEquals(listOf("retrySave"), port.calls)

        vm.discardAfterFailure()
        advanceUntilIdle()
        assertEquals(listOf("retrySave", "discard"), port.calls)
        assertEquals(listOf<OutingEvent>(OutingEvent.Exit), events)
        eventsJob.cancel()
    }

    @Test
    fun `échec au démarrage - alerte Impossible de démarrer`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort()
        val vm = vm(port = port)
        backgroundScope.launch { vm.ui.collect {} }
        port.update { copy(phase = OutingPhase.Idle, errorMessage = "base indisponible") }
        advanceUntilIdle()
        assertEquals(OutingAlert.StartFailed, vm.ui.value.alert)
    }

    @Test
    fun `séance enregistrée - événement de navigation une seule fois`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Saving))
        val vm = vm(port = port)
        val events = mutableListOf<OutingEvent>()
        val eventsJob = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        port.update { copy(phase = OutingPhase.Idle, savedSessionId = 42) }
        advanceUntilIdle()
        port.update { copy(elapsedSec = 1) }
        advanceUntilIdle()
        assertEquals(listOf<OutingEvent>(OutingEvent.Saved(42)), events)
        eventsJob.cancel()
    }

    @Test
    fun `état hérité du port - ni navigation ni alerte à l'ouverture d'un nouvel écran`() = runTest(mainDispatcher.dispatcher) {
        // Le port (singleton) garde l'id de la sortie précédente et sa dernière erreur.
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Idle, savedSessionId = 41, errorMessage = "Localisation refusée"))
        val vm = vm(port = port)
        val events = mutableListOf<OutingEvent>()
        backgroundScope.launch { vm.ui.collect {} }
        val eventsJob = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        assertTrue(events.isEmpty())
        assertNull(vm.ui.value.alert)

        // Une nouvelle erreur identique à l'ancienne (refus répété) alerte bien.
        port.update { copy(phase = OutingPhase.Requesting, errorMessage = null) }
        advanceUntilIdle()
        port.update { copy(phase = OutingPhase.Idle, errorMessage = "Localisation refusée") }
        advanceUntilIdle()
        assertEquals(OutingAlert.StartFailed, vm.ui.value.alert)

        // Et une nouvelle séance enregistrée navigue.
        port.update { copy(savedSessionId = 42, errorMessage = null) }
        advanceUntilIdle()
        assertEquals(listOf<OutingEvent>(OutingEvent.Saved(42)), events)
        eventsJob.cancel()
    }

    @Test
    fun `capteur cadence - via le port ou une valeur reçue`() = runTest(mainDispatcher.dispatcher) {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Active, gpsStatus = GpsStatus.TRACKING))
        val cadence = FakeCadencePort(hasSensor = false)
        val vm = vm(port = port, cadence = cadence)
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertFalse(vm.ui.value.hasCadenceSensor)

        cadence.hasSensor.value = true
        advanceUntilIdle()
        assertTrue(vm.ui.value.hasCadenceSensor)

        cadence.hasSensor.value = false
        port.update { copy(cadenceRpm = 85) }
        advanceUntilIdle()
        assertTrue(vm.ui.value.hasCadenceSensor)
    }
}
