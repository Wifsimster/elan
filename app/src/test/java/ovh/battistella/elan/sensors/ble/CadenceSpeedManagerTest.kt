// Capteurs cadence/vitesse sur doublures : maths CSC (deltas, bouclage,
// plausibilité, silence), deux capteurs en parallèle, reconnexion par capteur.
// Robolectric uniquement pour `org.json` (sérialisation de `csc_devices`).
package ovh.battistella.elan.sensors.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.settings.BleDevice
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.tracking.CscFrame

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CadenceSpeedManagerTest {

    private class Harness(scope: TestScope, adapterOn: Boolean = true) {
        val le = FakeLeScanner()
        val links = FakeGattLinkFactory().apply {
            names["CAD"] = "CAD70"
            names["SPD"] = "SPD70"
        }
        val adapter = FakeBleAdapterState(adapterOn)
        val settings = inMemorySettings()
        val clock = MutableClock(0L)
        var permissions = true
        val scanner = BleScanner(le, scope.backgroundScope)
        lateinit var manager: CadenceSpeedManager
        val frames = mutableListOf<CscFrame>()

        fun start(scope: TestScope): CadenceSpeedManager {
            manager = CadenceSpeedManager(scanner, links, adapter, { permissions }, settings, clock, scope.backgroundScope)
            scope.backgroundScope.launch { manager.frames().collect { frames += it } }
            return manager
        }

        val state get() = manager.state.value
        suspend fun savedDevices() = SettingsJson.parseCscDevices(settings.getSetting(SettingsRepository.Keys.CSC_DEVICES))
    }

    private fun TestScope.harness(adapterOn: Boolean = true) = Harness(this, adapterOn).also { it.start(this) }

    private fun TestScope.connected(h: Harness, vararg ids: String): List<FakeGattLink> {
        ids.forEach { h.manager.connect(it) }
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        return ids.map { h.links.last(it) }
    }

    // ---- scan / connexion ------------------------------------------------

    @Test
    fun `le scan liste les capteurs sauf ceux déjà connectés`() = runTest {
        val h = harness()
        connected(h, "CAD")

        h.manager.startScan()
        runCurrent()
        assertEquals(SensorStatus.Scanning, h.state.status)
        assertEquals(BleUuids.CSC_SERVICE, h.le.lastServiceUuid)
        h.le.results.emit(ScannedDevice("CAD", "CAD70"))
        h.le.results.emit(ScannedDevice("SPD", "SPD70"))
        h.le.results.emit(ScannedDevice("SPD", "SPD70"))
        runCurrent()
        assertEquals(listOf(ScannedDevice("SPD", "SPD70")), h.state.scanned)

        h.manager.stopScan()
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status) // repos = connecté, un capteur l'est
        assertEquals(0, h.le.active)
    }

    @Test
    fun `la connexion active les notifications CSC et mémorise la liste`() = runTest {
        val h = harness()
        val (link) = connected(h, "CAD")
        assertTrue(link.discovered)
        assertEquals(BleUuids.CSC_SERVICE to BleUuids.CSC_MEASUREMENT, link.notifications)
        assertEquals(listOf(ScannedDevice("CAD", "CAD70")), h.state.devices)
        assertEquals(listOf(BleDevice("CAD", "CAD70")), h.savedDevices())
        assertNull(h.state.cadenceRpm)
        assertNull(h.state.speedKmh)
    }

    @Test
    fun `deux capteurs maximum`() = runTest {
        val h = harness()
        connected(h, "CAD", "SPD")
        h.manager.connect("XX")
        runCurrent()
        assertEquals(2, h.links.created.size)
        assertEquals("Deux capteurs maximum.", h.state.error)
        assertEquals(SensorStatus.Connected, h.state.status)
    }

    @Test
    fun `un scan volé ramène au statut de repos`() = runTest {
        val h = harness()
        h.manager.startScan()
        runCurrent()
        launch { h.scanner.acquire(Any(), BleUuids.HEART_RATE_SERVICE) {}.collect {} }
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
    }

    // ---- maths CSC -------------------------------------------------------

    @Test
    fun `cadence 90 tr-min à partir des deltas de pédalier`() = runTest {
        val h = harness()
        val (link) = connected(h, "CAD")

        link.notify(crankFrame(10, 0))
        runCurrent()
        assertNull(h.state.cadenceRpm) // deux mesures nécessaires
        assertTrue(h.frames.isEmpty())

        h.clock.nowMs = 5_000L
        link.notify(crankFrame(13, 2048)) // 3 tours en 2 s
        runCurrent()
        assertEquals(90, h.state.cadenceRpm)
        assertNull(h.state.speedKmh)
        assertEquals(listOf(CscFrame(5_000L, 90, null)), h.frames)
    }

    @Test
    fun `vitesse à partir des deltas de roue avec 2105 mm`() = runTest {
        val h = harness()
        val (link) = connected(h, "SPD")
        assertEquals(2105, h.state.wheelCircumferenceMm)

        link.notify(wheelFrame(100, 0))
        link.notify(wheelFrame(110, 4096)) // 10 tours en 4 s = 5,2625 m/s
        runCurrent()
        assertEquals(18.945, h.state.speedKmh!!, 1e-9)
        assertNull(h.state.cadenceRpm)
        assertEquals(1, h.frames.size)
        assertEquals(18.945, h.frames[0].speedKmh!!, 1e-9)
        assertNull(h.frames[0].cadenceRpm)
    }

    @Test
    fun `bouclage des compteurs 16 bits (pédalier, temps) et 32 bits (roue)`() = runTest {
        val h = harness()
        val (cad, spd) = connected(h, "CAD", "SPD")

        cad.notify(crankFrame(0xFFFF, 0xFC00))
        cad.notify(crankFrame(0x0002, 0x0400)) // 3 tours, 2048 ticks
        spd.notify(wheelFrame(0xFFFF_FFFFL, 0xFC00))
        spd.notify(wheelFrame(9L, 0x0C00)) // 10 tours, 4096 ticks
        runCurrent()
        assertEquals(90, h.state.cadenceRpm)
        assertEquals(18.945, h.state.speedKmh!!, 1e-9)
    }

    @Test
    fun `sans nouveau tour, cadence et vitesse tombent à 0 et la trame est émise`() = runTest {
        val h = harness()
        val (link) = connected(h, "CAD")
        link.notify(cscFrame(50, 0, 10, 0))
        link.notify(cscFrame(50, 1024, 10, 1024))
        runCurrent()
        assertEquals(0, h.state.cadenceRpm)
        assertEquals(0.0, h.state.speedKmh!!, 0.0)
        assertEquals(listOf(CscFrame(0L, 0, 0.0)), h.frames)
    }

    @Test
    fun `les valeurs invraisemblables sont ignorées`() = runTest {
        val h = harness()
        val (link) = connected(h, "CAD")
        link.notify(cscFrame(0, 0, 0, 0))
        link.notify(cscFrame(100, 1024, 100, 1024)) // 6000 tr/min, 757 km/h
        runCurrent()
        assertNull(h.state.cadenceRpm)
        assertNull(h.state.speedKmh)
        assertTrue(h.frames.isEmpty())

        // Un delta de temps nul n'est pas exploitable non plus.
        link.notify(cscFrame(101, 1024, 101, 1024))
        runCurrent()
        assertNull(h.state.cadenceRpm)
        assertTrue(h.frames.isEmpty())
    }

    @Test
    fun `chaque trame exploitable est retransmise, même à valeur identique`() = runTest {
        val h = harness()
        val (link) = connected(h, "CAD")
        link.notify(crankFrame(0, 0))
        link.notify(crankFrame(3, 2048))
        link.notify(crankFrame(6, 4096))
        runCurrent()
        assertEquals(listOf(90, 90), h.frames.map { it.cadenceRpm })
    }

    @Test
    fun `après 3 s de silence, cadence et vitesse retombent à 0`() = runTest {
        val h = harness()
        val (link) = connected(h, "CAD")
        h.clock.nowMs = 10_000L
        link.notify(cscFrame(0, 0, 0, 0))
        link.notify(cscFrame(10, 4096, 3, 2048))
        runCurrent()
        assertEquals(90, h.state.cadenceRpm)
        assertEquals(18.945, h.state.speedKmh!!, 1e-9)

        h.clock.nowMs = 12_500L
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(90, h.state.cadenceRpm) // pas encore périmé

        h.clock.nowMs = 13_100L
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, h.state.cadenceRpm)
        assertEquals(0.0, h.state.speedKmh!!, 0.0)
    }

    @Test
    fun `deux capteurs en parallèle, cadence de l'un et vitesse de l'autre`() = runTest {
        val h = harness()
        val (cad, spd) = connected(h, "CAD", "SPD")
        assertEquals(listOf(ScannedDevice("CAD", "CAD70"), ScannedDevice("SPD", "SPD70")), h.state.devices)
        assertEquals(listOf(BleDevice("CAD", "CAD70"), BleDevice("SPD", "SPD70")), h.savedDevices())

        cad.notify(crankFrame(0, 0))
        spd.notify(wheelFrame(0, 0))
        cad.notify(crankFrame(3, 2048))
        runCurrent()
        assertEquals(CscFrame(0L, 90, null), h.frames.last())
        spd.notify(wheelFrame(10, 4096))
        runCurrent()
        assertEquals(90, h.state.cadenceRpm)
        assertEquals(18.945, h.state.speedKmh!!, 1e-9)
        assertEquals(90, h.frames.last().cadenceRpm)
        assertEquals(18.945, h.frames.last().speedKmh!!, 1e-9)

        // Déconnecter l'un garde l'autre connecté ; les mesures ne sont
        // remises à `null` que lorsqu'il ne reste plus aucun capteur.
        h.manager.disconnect("SPD")
        runCurrent()
        assertTrue(spd.closed)
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(listOf(ScannedDevice("CAD", "CAD70")), h.state.devices)
        assertEquals(listOf(BleDevice("CAD", "CAD70")), h.savedDevices())
        assertEquals(90, h.state.cadenceRpm)

        h.manager.disconnect("CAD")
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertNull(h.state.cadenceRpm)
        assertNull(h.state.speedKmh)
        assertTrue(h.savedDevices().isEmpty())
    }

    // ---- reconnexion -----------------------------------------------------

    @Test
    fun `la reconnexion est gérée capteur par capteur`() = runTest {
        val h = harness()
        val (cad, spd) = connected(h, "CAD", "SPD")

        spd.drop(8)
        runCurrent()
        assertTrue(spd.closed)
        assertEquals(SensorStatus.Connected, h.state.status) // CAD tient toujours
        assertEquals(listOf(ScannedDevice("CAD", "CAD70")), h.state.devices)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, h.state.devices.size)
        assertEquals(3, h.links.created.size)

        cad.drop(8)
        h.links.last("SPD").drop(8)
        runCurrent()
        assertEquals(SensorStatus.Reconnecting, h.state.status)
        assertNull(h.state.cadenceRpm)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(2, h.state.devices.size)
    }

    @Test
    fun `un GATT 133 ferme le lien, replanifie en back-off et abandonne après 5 tentatives`() = runTest {
        val h = harness()
        h.links.configure = { it.discoverFailure = GattException(133) }
        h.manager.connect("CAD")
        runCurrent()
        assertEquals(SensorStatus.Reconnecting, h.state.status)
        assertEquals("Erreur GATT 133.", h.state.error)
        assertTrue(h.links.created[0].closed)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, h.links.created.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, h.links.created.size)

        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(6, h.links.created.size) // 1 + 5 tentatives
        assertTrue(h.links.created.all { it.closed })
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals("Erreur GATT 133.", h.state.error)
    }

    @Test
    fun `la déconnexion volontaire désarme la reconnexion de ce capteur`() = runTest {
        val h = harness()
        val (cad) = connected(h, "CAD")
        h.manager.disconnect("CAD")
        runCurrent()
        assertTrue(cad.closed)
        cad.drop(8)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, h.links.created.size)
        assertEquals(SensorStatus.Idle, h.state.status)
    }

    // ---- adaptateur et réglages ------------------------------------------

    @Test
    fun `au lancement, reconnexion en parallèle aux capteurs mémorisés`() = runTest {
        val h = Harness(this)
        h.settings.setSetting(SettingsRepository.Keys.CSC_DEVICES, """[{"id":"CAD","name":"CAD70"},{"id":"SPD","name":"SPD70"}]""")
        h.settings.setSetting(SettingsRepository.Keys.CSC_WHEEL_MM, "2096")
        h.links.configure = { it.connectDelayMs = 1_000 }
        h.start(this)
        advanceTimeBy(1_500) // les deux tentatives se chevauchent : 1 s au total, pas 2
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(2, h.state.devices.size)
        assertEquals(2096, h.state.wheelCircumferenceMm)
    }

    @Test
    fun `Bluetooth coupé puis rallumé, les capteurs mémorisés sont reconnectés`() = runTest {
        val h = harness()
        val (cad, spd) = connected(h, "CAD", "SPD")
        cad.notify(crankFrame(0, 0))
        cad.notify(crankFrame(3, 2048))
        runCurrent()
        assertEquals(90, h.state.cadenceRpm)

        h.adapter.turnOff()
        runCurrent()
        assertEquals(SensorStatus.Error, h.state.status)
        assertEquals("Bluetooth désactivé.", h.state.error)
        assertNull(h.state.cadenceRpm)
        cad.drop(8)
        spd.drop(8)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, h.links.created.size) // aucune tentative radio éteinte

        h.adapter.turnOn()
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(4, h.links.created.size)
        assertEquals(2, h.state.devices.size)
        assertNull(h.state.error)
    }

    @Test
    fun `la circonférence de roue est mémorisée et utilisée pour la vitesse`() = runTest {
        val h = harness()
        h.manager.setWheelCircumference(2000)
        runCurrent()
        assertEquals(2000, h.state.wheelCircumferenceMm)
        assertEquals("2000", h.settings.getSetting(SettingsRepository.Keys.CSC_WHEEL_MM))

        val (link) = connected(h, "SPD")
        link.notify(wheelFrame(0, 0))
        link.notify(wheelFrame(10, 4096)) // 10 × 2 m en 4 s = 5 m/s
        runCurrent()
        assertEquals(18.0, h.state.speedKmh!!, 1e-9)
    }
}
