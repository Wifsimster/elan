package ovh.battistella.elan.tracking

import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowPowerManager

/** Le service suit l'état du contrôleur : premier plan dès le démarrage, arrêt dès que la sortie finit. */
@RunWith(RobolectricTestRunner::class)
class TrackingServiceTest {

    private val state = MutableStateFlow(OutingState(phase = OutingPhase.ACTIVE))
    private val controller = mockk<TrackingController>().also {
        every { it.state } returns state
    }
    private lateinit var service: ServiceController<TrackingService>

    @Before
    fun setUp() {
        TrackingService.controllerProvider = { controller }
        service = Robolectric.buildService(TrackingService::class.java).create()
    }

    @After
    fun tearDown() {
        TrackingService.controllerProvider = null
    }

    @Test
    fun `startForeground est appelé avec la notification de sortie et un verrou de veille est pris`() {
        service.startCommand(0, 1)

        val shadow = shadowOf(service.get())
        assertTrue(shadow.isLastForegroundNotificationAttached)
        assertEquals(LiveNotification.FOREGROUND_ID, shadow.lastForegroundNotificationId)
        assertEquals("Sortie en cours", shadow.lastForegroundNotification.extras.getString("android.title"))
        assertFalse(shadow.isStoppedBySelf)

        val lock = ShadowPowerManager.getLatestWakeLock()
        assertTrue(lock.isHeld)
        assertEquals(TrackingService.WAKE_LOCK_TAG, shadowOf(lock).tag)
    }

    @Test
    fun `le service s'arrête quand la sortie quitte les phases active ou pause`() {
        service.startCommand(0, 1)
        state.value = OutingState(phase = OutingPhase.PAUSED)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(shadowOf(service.get()).isStoppedBySelf)

        state.value = OutingState(phase = OutingPhase.IDLE)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(service.get()).isStoppedBySelf)
    }

    @Test
    fun `démarré sans sortie en cours il s'arrête aussitôt`() {
        state.value = OutingState()

        service.startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(service.get()).isStoppedBySelf)
    }

    @Test
    fun `la destruction relâche le verrou et retire la notification`() {
        service.startCommand(0, 1)
        val lock = ShadowPowerManager.getLatestWakeLock()

        service.destroy()

        assertFalse(lock.isHeld)
        assertTrue(shadowOf(service.get()).isForegroundStopped)
    }
}
