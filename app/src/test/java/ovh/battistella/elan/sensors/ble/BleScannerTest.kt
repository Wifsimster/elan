// Arbitrage du scan partagé : un seul scan matériel, propriétaire volé
// notifié, relâchement borné au propriétaire, arrêt automatique à 20 s.
package ovh.battistella.elan.sensors.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerTest {

    private val hr = Any()
    private val csc = Any()

    @Test
    fun `émet les appareils dédupliqués par adresse avec le service demandé`() = runTest {
        val le = FakeLeScanner()
        val scanner = BleScanner(le, backgroundScope)
        val seen = mutableListOf<ScannedDevice>()

        val job = launch { scanner.acquire(hr, BleUuids.HEART_RATE_SERVICE) {}.collect { seen += it } }
        runCurrent()
        assertEquals(1, le.active)
        assertEquals(BleUuids.HEART_RATE_SERVICE, le.lastServiceUuid)
        assertSame(hr, scanner.currentOwner)

        le.results.emit(ScannedDevice("AA", "Polar H10"))
        le.results.emit(ScannedDevice("AA", "Polar H10"))
        le.results.emit(ScannedDevice("BB", "Capteur inconnu"))
        runCurrent()
        assertEquals(listOf(ScannedDevice("AA", "Polar H10"), ScannedDevice("BB", "Capteur inconnu")), seen)

        scanner.release(hr)
        runCurrent()
        assertEquals(0, le.active)
        assertNull(scanner.currentOwner)
        assertTrue(job.isCompleted) // le flux se termine au relâchement
    }

    @Test
    fun `un nouveau propriétaire vole le scan et l'ancien est notifié`() = runTest {
        val le = FakeLeScanner()
        val scanner = BleScanner(le, backgroundScope)
        var hrStolen = 0
        var cscStolen = 0

        val hrJob = launch { scanner.acquire(hr, BleUuids.HEART_RATE_SERVICE) { hrStolen++ }.collect {} }
        runCurrent()
        assertEquals(1, le.active)

        val cscJob = launch { scanner.acquire(csc, BleUuids.CSC_SERVICE) { cscStolen++ }.collect {} }
        runCurrent()
        assertEquals(1, hrStolen)
        assertEquals(0, cscStolen)
        assertEquals(1, le.active) // toujours un seul scan matériel
        assertEquals(BleUuids.CSC_SERVICE, le.lastServiceUuid)
        assertSame(csc, scanner.currentOwner)
        assertTrue(hrJob.isCompleted)

        // Relâcher par l'ancien propriétaire ne coupe pas le scan du voleur.
        scanner.release(hr)
        runCurrent()
        assertEquals(1, le.active)
        assertSame(csc, scanner.currentOwner)
        assertFalse(cscJob.isCompleted)

        scanner.release(csc)
        runCurrent()
        assertEquals(0, le.active)
    }

    @Test
    fun `le même propriétaire peut relancer son scan sans être notifié`() = runTest {
        val le = FakeLeScanner()
        val scanner = BleScanner(le, backgroundScope)
        var stolen = 0

        launch { scanner.acquire(hr, BleUuids.HEART_RATE_SERVICE) { stolen++ }.collect {} }
        runCurrent()
        launch { scanner.acquire(hr, BleUuids.HEART_RATE_SERVICE) { stolen++ }.collect {} }
        runCurrent()

        assertEquals(0, stolen)
        assertEquals(1, le.active)
        assertEquals(2, le.starts)
        scanner.release(hr)
    }

    @Test
    fun `s'arrête tout seul après 20 s`() = runTest {
        val le = FakeLeScanner()
        val scanner = BleScanner(le, backgroundScope)

        val job = launch { scanner.acquire(hr, BleUuids.HEART_RATE_SERVICE) {}.collect {} }
        advanceTimeBy(SCAN_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(1, le.active)
        assertFalse(job.isCompleted)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(0, le.active)
        assertTrue(job.isCompleted)
        assertNull(scanner.currentOwner)
    }

    @Test
    fun `un échec de la radio fait échouer le flux`() = runTest {
        val le = FakeLeScanner().apply { failure = BleScanException("Bluetooth désactivé.") }
        val scanner = BleScanner(le, backgroundScope)

        var error: Throwable? = null
        val job = launch {
            try {
                scanner.acquire(hr, BleUuids.HEART_RATE_SERVICE) {}.toList()
            } catch (e: BleScanException) {
                error = e
            }
        }
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals("Bluetooth désactivé.", error?.message)
        assertEquals(0, le.active)
        assertNull(scanner.currentOwner)
    }
}
