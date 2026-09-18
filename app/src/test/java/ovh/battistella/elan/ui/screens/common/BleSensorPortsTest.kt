package ovh.battistella.elan.ui.screens.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.sensors.ble.BleScanner
import ovh.battistella.elan.sensors.ble.CadenceSpeedManager
import ovh.battistella.elan.sensors.ble.FakeBleAdapterState
import ovh.battistella.elan.sensors.ble.FakeGattLinkFactory
import ovh.battistella.elan.sensors.ble.FakeLeScanner
import ovh.battistella.elan.sensors.ble.HeartRateManager
import ovh.battistella.elan.sensors.ble.MutableClock
import ovh.battistella.elan.sensors.ble.crankFrame
import ovh.battistella.elan.sensors.ble.hrFrame
import ovh.battistella.elan.sensors.ble.inMemorySettings

/** Les vues minimales des écrans suivent l'état des gestionnaires BLE. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BleSensorPortsTest {

    @Test
    fun `ceinture - connectée et bpm suivent le gestionnaire`() = runTest {
        val links = FakeGattLinkFactory().apply { names["AA"] = "Polar H10" }
        val manager = HeartRateManager(
            BleScanner(FakeLeScanner(), backgroundScope), links, FakeBleAdapterState(), { true },
            inMemorySettings(), MutableClock(), backgroundScope,
        )
        val port = BleHeartRatePort(manager, backgroundScope)
        runCurrent()
        assertFalse(port.connected.value)
        assertNull(port.bpm.value)

        manager.connect("AA")
        runCurrent()
        assertTrue(port.connected.value)

        links.last("AA").notify(hrFrame(128))
        runCurrent()
        assertEquals(128, port.bpm.value)

        manager.disconnect()
        runCurrent()
        assertFalse(port.connected.value)
        assertNull(port.bpm.value)
    }

    @Test
    fun `capteurs vélo - présence, cadence et vitesse suivent le gestionnaire`() = runTest {
        val links = FakeGattLinkFactory().apply { names["CAD"] = "CAD70" }
        val clock = MutableClock(0L)
        val manager = CadenceSpeedManager(
            BleScanner(FakeLeScanner(), backgroundScope), links, FakeBleAdapterState(), { true },
            inMemorySettings(), clock, backgroundScope,
        )
        val port = BleCadencePort(manager, backgroundScope)
        runCurrent()
        assertFalse(port.hasSensor.value)
        assertNull(port.cadenceRpm.value)
        assertNull(port.speedKmh.value)

        manager.connect("CAD")
        runCurrent()
        assertTrue(port.hasSensor.value)

        // 3 tours de pédalier en 2 s (2 048 ticks) : 90 tr/min.
        val link = links.last("CAD")
        link.notify(crankFrame(10, 0))
        runCurrent()
        clock.nowMs = 5_000L
        link.notify(crankFrame(13, 2048))
        runCurrent()
        assertEquals(90, port.cadenceRpm.value)
        assertNull(port.speedKmh.value)

        manager.disconnectAll()
        runCurrent()
        assertFalse(port.hasSensor.value)
    }
}
