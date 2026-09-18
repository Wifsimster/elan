// Permissions BLE : jeu requis selon l'API (Robolectric tourne en API 34) et
// vérification d'octroi via le shadow de l'application.
package ovh.battistella.elan.sensors.ble

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BlePermissionsTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `API 31+ exige BLUETOOTH_SCAN et BLUETOOTH_CONNECT`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            BlePermissions.required(),
        )
    }

    @Test
    @Config(sdk = [30])
    fun `avant l'API 31, la position précise suffit`() {
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), BlePermissions.required())
    }

    @Test
    fun `granted exige toutes les permissions requises`() {
        assertFalse(BlePermissions.granted(app))
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        assertFalse(BlePermissions.granted(app))
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertTrue(BlePermissions.granted(app))
    }
}
