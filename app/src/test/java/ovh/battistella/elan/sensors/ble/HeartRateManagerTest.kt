// Machine d'états de la ceinture cardiaque sur doublures, en temps virtuel.
// Robolectric uniquement pour `org.json` (sérialisation de `hr_device`).
package ovh.battistella.elan.sensors.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.settings.BleDevice
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.tracking.HrFrame

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HeartRateManagerTest {

    private class Harness(private val scope: TestScope, adapterOn: Boolean = true, supported: Boolean = true) {
        val le = FakeLeScanner()
        val links = FakeGattLinkFactory().apply { names["AA"] = "Polar H10" }
        val adapter = FakeBleAdapterState(adapterOn, supported)
        val settings = inMemorySettings()
        val clock = MutableClock()
        var permissions = true
        val scanner = BleScanner(le, scope.backgroundScope)
        lateinit var manager: HeartRateManager

        fun start(): HeartRateManager {
            manager = HeartRateManager(scanner, links, adapter, { permissions }, settings, clock, scope.backgroundScope)
            return manager
        }

        val state get() = manager.state.value
        suspend fun savedDevice() = SettingsJson.parseHrDevice(settings.getSetting(SettingsRepository.Keys.HR_DEVICE))
    }

    private fun TestScope.harness(adapterOn: Boolean = true, supported: Boolean = true) =
        Harness(this, adapterOn, supported).also { it.start() }

    /** Connecte « AA » et attend l'établissement. */
    private fun TestScope.connected(h: Harness): FakeGattLink {
        h.manager.connect("AA")
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        return h.links.last("AA")
    }

    // ---- scan ------------------------------------------------------------

    @Test
    fun `le scan liste les ceintures dédupliquées puis s'arrête à la demande`() = runTest {
        val h = harness()
        h.manager.startScan()
        runCurrent()
        assertEquals(SensorStatus.Scanning, h.state.status)
        assertEquals(BleUuids.HEART_RATE_SERVICE, h.le.lastServiceUuid)

        h.le.results.emit(ScannedDevice("AA", "Polar H10"))
        h.le.results.emit(ScannedDevice("AA", "Polar H10"))
        h.le.results.emit(ScannedDevice("BB", UNKNOWN_DEVICE_NAME))
        runCurrent()
        assertEquals(listOf(ScannedDevice("AA", "Polar H10"), ScannedDevice("BB", UNKNOWN_DEVICE_NAME)), h.state.scanned)

        h.manager.stopScan()
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals(0, h.le.active)
    }

    @Test
    fun `le scan s'arrête tout seul après 20 s`() = runTest {
        val h = harness()
        h.manager.startScan()
        runCurrent()
        advanceTimeBy(SCAN_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals(0, h.le.active)
    }

    @Test
    fun `un scan volé par les capteurs vélo ramène à l'arrêt`() = runTest {
        val h = harness()
        h.manager.startScan()
        runCurrent()
        assertEquals(SensorStatus.Scanning, h.state.status)

        launch { h.scanner.acquire(Any(), BleUuids.CSC_SERVICE) {}.collect {} }
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals(1, h.le.active) // le scan du voleur tourne, pas le nôtre
    }

    @Test
    fun `sans permission le scan passe en erreur`() = runTest {
        val h = harness()
        h.permissions = false
        h.manager.startScan()
        runCurrent()
        assertEquals(SensorStatus.Error, h.state.status)
        assertEquals("Permissions Bluetooth refusées.", h.state.error)
        assertEquals(0, h.le.starts)
    }

    @Test
    fun `un échec de la radio pendant le scan passe en erreur`() = runTest {
        val h = harness()
        h.le.failure = BleScanException("Échec du scan Bluetooth (2).")
        h.manager.startScan()
        runCurrent()
        assertEquals(SensorStatus.Error, h.state.status)
        assertEquals("Échec du scan Bluetooth (2).", h.state.error)
    }

    // ---- connexion -------------------------------------------------------

    @Test
    fun `la connexion découvre, active les notifications, vide le scan et mémorise la ceinture`() = runTest {
        val h = harness()
        h.manager.startScan()
        runCurrent()
        h.le.results.emit(ScannedDevice("AA", "Polar H10"))
        runCurrent()

        val link = connected(h)
        assertTrue(link.discovered)
        assertEquals(BleUuids.HEART_RATE_SERVICE to BleUuids.HEART_RATE_MEASUREMENT, link.notifications)
        assertEquals(ScannedDevice("AA", "Polar H10"), h.state.device)
        assertTrue(h.state.scanned.isEmpty())
        assertEquals(0, h.le.active) // le scan est coupé par la connexion
        assertNull(h.state.error)
        assertEquals(BleDevice("AA", "Polar H10"), h.savedDevice())
    }

    @Test
    fun `sans nom système la ceinture reçoit un nom de repli`() = runTest {
        val h = harness()
        h.manager.connect("CC")
        runCurrent()
        assertEquals(ScannedDevice("CC", "Ceinture cardiaque"), h.state.device)
    }

    @Test
    fun `chaque trame est retransmise, même à valeur identique, et la FC 0 est ignorée`() = runTest {
        val h = harness()
        val link = connected(h)
        val frames = mutableListOf<HrFrame>()
        backgroundScope.launch { h.manager.frames().collect { frames += it } }
        runCurrent()

        h.clock.nowMs = 1_000L
        link.notify(hrFrame(120))
        h.clock.nowMs = 2_000L
        link.notify(hrFrame(120))
        link.notify(hrFrame(0))
        runCurrent()

        assertEquals(listOf(HrFrame(1_000L, 120), HrFrame(2_000L, 120)), frames)
        assertEquals(120, h.state.bpm)
    }

    @Test
    fun `se connecter à une autre ceinture déconnecte la première`() = runTest {
        val h = harness()
        val first = connected(h)
        h.links.names["BB"] = "Garmin HRM"

        h.manager.connect("BB")
        runCurrent()
        assertTrue(first.closed)
        assertEquals(ScannedDevice("BB", "Garmin HRM"), h.state.device)
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(BleDevice("BB", "Garmin HRM"), h.savedDevice())
    }

    @Test
    fun `une reconnexion au même appareil est ignorée`() = runTest {
        val h = harness()
        connected(h)
        h.manager.connect("AA")
        runCurrent()
        assertEquals(1, h.links.created.size)
    }

    @Test
    fun `une tentative en vol bloque une seconde connexion`() = runTest {
        val h = harness()
        h.links.configure = { it.connectDelayMs = 500 }
        h.manager.connect("AA")
        runCurrent()
        assertEquals(SensorStatus.Connecting, h.state.status)
        h.manager.connect("BB")
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("AA"), h.links.created.map { it.address })
        assertEquals(ScannedDevice("AA", "Polar H10"), h.state.device)
    }

    @Test
    fun `sans permission la connexion échoue avec le message dédié`() = runTest {
        val h = harness()
        h.permissions = false
        h.manager.connect("AA")
        runCurrent()
        assertEquals("Permissions Bluetooth refusées.", h.state.error)
        assertEquals(0, h.links.created.size)
    }

    // ---- reconnexion -----------------------------------------------------

    @Test
    fun `un GATT 133 ferme le lien et replanifie en back-off exponentiel borné à 5 tentatives`() = runTest {
        val h = harness()
        h.links.configure = { it.discoverFailure = GattException(133) }

        h.manager.connect("AA")
        runCurrent()
        assertEquals(SensorStatus.Reconnecting, h.state.status)
        assertEquals("Erreur GATT 133.", h.state.error)
        assertEquals(1, h.links.created.size)
        assertTrue(h.links.created[0].closed)

        // Délais : 1 s, 2 s, 4 s, 8 s, 15 s (plafond) → tirs à 1 s, 3 s, 7 s, 15 s, 30 s.
        var fireAt = testScheduler.currentTime
        for ((i, delayMs) in listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L).withIndex()) {
            fireAt += delayMs
            advanceTimeBy(fireAt - 1 - testScheduler.currentTime)
            runCurrent()
            assertEquals("avant le tir $i", i + 1, h.links.created.size)
            advanceTimeBy(2)
            runCurrent()
            assertEquals("après le tir $i", i + 2, h.links.created.size)
            assertTrue(h.links.created[i + 1].closed)
        }

        // Budget épuisé : retour au repos, message conservé, plus aucune tentative.
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals("Erreur GATT 133.", h.state.error)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(6, h.links.created.size)
    }

    @Test
    fun `une coupure involontaire reconnecte et le compteur repart à zéro après succès`() = runTest {
        val h = harness()
        val first = connected(h)
        h.manager.frames() // sans effet, juste pour l'API

        first.drop(8)
        runCurrent()
        assertEquals(SensorStatus.Reconnecting, h.state.status)
        assertNull(h.state.bpm)
        assertNull(h.state.device)
        assertTrue(first.closed)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(2, h.links.created.size)

        // Nouvelle coupure : le premier délai est de nouveau 1 s (compteur remis à zéro).
        h.links.last("AA").drop(8)
        runCurrent()
        assertEquals(SensorStatus.Reconnecting, h.state.status)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(2, h.links.created.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(3, h.links.created.size)
        assertEquals(SensorStatus.Connected, h.state.status)
    }

    @Test
    fun `la déconnexion volontaire désarme la reconnexion`() = runTest {
        val h = harness()
        val link = connected(h)

        h.manager.disconnect()
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertNull(h.state.device)
        assertNull(h.state.bpm)
        assertTrue(link.closed)

        link.drop(8) // rappel tardif du lien fermé : ignoré
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals(1, h.links.created.size)
    }

    @Test
    fun `la déconnexion volontaire annule une reconnexion en attente`() = runTest {
        val h = harness()
        val link = connected(h)
        link.drop(8)
        runCurrent()
        assertEquals(SensorStatus.Reconnecting, h.state.status)

        h.manager.disconnect()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals(1, h.links.created.size)
    }

    // ---- adaptateur Bluetooth --------------------------------------------

    @Test
    fun `au lancement, reconnexion à la ceinture mémorisée`() = runTest {
        val h = Harness(this)
        h.settings.setSetting(SettingsRepository.Keys.HR_DEVICE, """{"id":"AA","name":"Polar H10"}""")
        h.start()
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        assertEquals(ScannedDevice("AA", "Polar H10"), h.state.device)
    }

    @Test
    fun `sans ceinture mémorisée, rien ne se passe au lancement`() = runTest {
        val h = harness()
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals(0, h.links.created.size)
    }

    @Test
    fun `Bluetooth coupé puis rallumé, la ceinture mémorisée est reconnectée`() = runTest {
        val h = harness()
        val link = connected(h)
        link.notify(hrFrame(110))
        runCurrent()
        assertEquals(110, h.state.bpm)

        h.adapter.turnOff()
        runCurrent()
        assertEquals(SensorStatus.Error, h.state.status)
        assertEquals("Bluetooth désactivé.", h.state.error)
        assertNull(h.state.bpm)

        link.drop(8) // la pile coupe le lien dans la foulée
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(SensorStatus.Error, h.state.status) // pas de tentative radio éteinte
        assertEquals(1, h.links.created.size)

        h.adapter.turnOn()
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
        assertNull(h.state.error)
        assertEquals(2, h.links.created.size)
        assertEquals(ScannedDevice("AA", "Polar H10"), h.state.device)
    }

    @Test
    fun `Bluetooth éteint au lancement, la reconnexion attend l'allumage`() = runTest {
        val h = Harness(this, adapterOn = false)
        h.settings.setSetting(SettingsRepository.Keys.HR_DEVICE, """{"id":"AA","name":"Polar H10"}""")
        h.start()
        runCurrent()
        assertEquals(SensorStatus.Idle, h.state.status)
        assertEquals("Bluetooth désactivé.", h.state.error)
        assertEquals(0, h.links.created.size)

        h.adapter.turnOn()
        runCurrent()
        assertEquals(SensorStatus.Connected, h.state.status)
    }

    @Test
    fun `sans radio BLE, le gestionnaire reste non pris en charge`() = runTest {
        val h = harness(supported = false)
        assertEquals(SensorStatus.Unsupported, h.state.status)
        h.manager.startScan()
        h.manager.connect("AA")
        runCurrent()
        assertEquals(SensorStatus.Unsupported, h.state.status)
        assertEquals(0, h.le.starts)
        assertEquals(0, h.links.created.size)
        assertNotNull(h.manager.frames())
        assertFalse(h.adapter.supported)
    }
}
