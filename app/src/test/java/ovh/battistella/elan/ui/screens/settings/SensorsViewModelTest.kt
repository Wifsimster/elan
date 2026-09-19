package ovh.battistella.elan.ui.screens.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.WHEEL_SIZES
import ovh.battistella.elan.sensors.ble.BleScanner
import ovh.battistella.elan.sensors.ble.CadenceSpeedManager
import ovh.battistella.elan.sensors.ble.FakeBleAdapterState
import ovh.battistella.elan.sensors.ble.FakeGattLinkFactory
import ovh.battistella.elan.sensors.ble.FakeLeScanner
import ovh.battistella.elan.sensors.ble.HeartRateManager
import ovh.battistella.elan.sensors.ble.MutableClock
import ovh.battistella.elan.sensors.ble.ScannedDevice
import ovh.battistella.elan.sensors.ble.SensorStatus
import ovh.battistella.elan.sensors.ble.inMemorySettings

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SensorsViewModelTest {

    private class Harness(scope: TestScope) {
        val le = FakeLeScanner()
        val links = FakeGattLinkFactory().apply { names["HRM"] = "Polar H10" }
        val settings: SettingsRepository = inMemorySettings()
        val scanner = BleScanner(le, scope.backgroundScope)
        val adapter = FakeBleAdapterState(true)
        val clock = MutableClock(0L)
        val hr = HeartRateManager(scanner, links, adapter, { true }, settings, clock, scope.backgroundScope)
        val csc = CadenceSpeedManager(scanner, links, adapter, { true }, settings, clock, scope.backgroundScope)
        val vm = SensorsViewModel(hr, csc)
    }

    @Test
    fun `taille de pneu - preset reconnu, personnalisé sinon, bornes de la circonférence`() = runTest {
        val h = Harness(this)
        runCurrent()

        // Défaut 700×25c.
        assertEquals(2105, h.vm.csc.value.wheelCircumferenceMm)
        assertEquals("700×25c", h.vm.currentWheelSize()?.label)

        h.vm.setWheelMm(WHEEL_SIZES.first { it.label == "29×2.1" }.mm)
        runCurrent()
        assertEquals(2288, h.vm.csc.value.wheelCircumferenceMm)
        assertEquals("29×2.1", h.vm.currentWheelSize()?.label)
        assertEquals(2288.0, SettingsJson.parseWheelMm(h.settings.getSetting(SettingsRepository.Keys.CSC_WHEEL_MM)), 0.0)

        h.vm.setWheelMm(2107)
        runCurrent()
        assertNull(h.vm.currentWheelSize())

        h.vm.setWheelMm(500)
        runCurrent()
        assertEquals(WHEEL_MM_MIN, h.vm.csc.value.wheelCircumferenceMm)
        h.vm.setWheelMm(9000)
        runCurrent()
        assertEquals(WHEEL_MM_MAX, h.vm.csc.value.wheelCircumferenceMm)
    }

    @Test
    fun `ceinture - scan, détection, connexion et déconnexion via le ViewModel`() = runTest {
        val h = Harness(this)
        runCurrent()
        assertEquals(SensorStatus.Idle, h.vm.hr.value.status)

        h.vm.startHrScan()
        runCurrent()
        assertEquals(SensorStatus.Scanning, h.vm.hr.value.status)
        h.le.results.emit(ScannedDevice("HRM", "Polar H10"))
        runCurrent()
        assertEquals(listOf("Polar H10"), h.vm.hr.value.scanned.map { it.name })

        h.vm.connectHr("HRM")
        runCurrent()
        assertEquals(SensorStatus.Connected, h.vm.hr.value.status)
        assertEquals("HRM", h.vm.hr.value.device?.id)

        h.vm.disconnectHr()
        runCurrent()
        assertEquals(SensorStatus.Idle, h.vm.hr.value.status)

        h.vm.startHrScan()
        runCurrent()
        h.vm.stopHrScan()
        runCurrent()
        assertEquals(SensorStatus.Idle, h.vm.hr.value.status)
    }
}
