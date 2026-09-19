package ovh.battistella.elan.maps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.domain.LatLon

@RunWith(RobolectricTestRunner::class)
class MapSnapshotsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val route = listOf(LatLon(48.8566, 2.3522), LatLon(48.8600, 2.3600))

    @Test
    fun `sans style - null, sans toucher MapLibre`() = runTest {
        assertNull(MapSnapshots.snapshotRoute(context, route, styleUrl = ""))
        assertNull(MapSnapshots.snapshotRoute(context, route, styleUrl = "   "))
    }

    @Test
    fun `style non HTTPS - null`() = runTest {
        assertNull(MapSnapshots.snapshotRoute(context, route, styleUrl = "http://tiles.example.org/style.json"))
    }

    @Test
    fun `moins de 2 points - null`() = runTest {
        assertNull(MapSnapshots.snapshotRoute(context, route.take(1), styleUrl = OPENFREEMAP_STYLE_URL))
        assertNull(MapSnapshots.snapshotRoute(context, emptyList(), styleUrl = OPENFREEMAP_STYLE_URL))
    }
}
