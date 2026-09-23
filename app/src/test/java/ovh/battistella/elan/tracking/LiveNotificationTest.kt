package ovh.battistella.elan.tracking

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
class LiveNotificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val realManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val manager: ShadowNotificationManager
        get() = shadowOf(realManager)

    @Before
    fun grant() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `le canal session est silencieux et d'importance basse`() {
        LiveNotification.ensureChannel(context)
        LiveNotification.ensureChannel(context) // idempotent

        val channel = realManager.getNotificationChannel(LiveNotification.SESSION_CHANNEL)
        assertNotNull(channel)
        assertEquals("Séance en cours", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.canShowBadge())
        assertNull(channel.sound)
        assertEquals(1, manager.notificationChannels.size)
    }

    @Test
    fun `showLive pose une notification persistante puis clear l'efface`() {
        LiveNotification.showLive(context, LiveKind.MUSCU)

        val n = manager.getNotification(LiveNotification.LIVE_ID)
        assertNotNull(n)
        assertEquals("Séance muscu en cours", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Touche pour revenir à ta séance.", n.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(LiveNotification.SESSION_CHANNEL, n.channelId)
        assertNotNull(n.contentIntent)
        val target = shadowOf(n.contentIntent).savedIntent
        assertEquals("ovh.battistella.elan.MainActivity", target.component?.className)
        assertEquals("muscu", target.getStringExtra(LiveNotification.EXTRA_OPEN_ROUTE))

        // Ré-appeler met à jour sans dupliquer.
        LiveNotification.showLive(context, LiveKind.OUTING)
        assertEquals(1, manager.size())
        assertEquals("Sortie en cours", manager.getNotification(LiveNotification.LIVE_ID).extras.getString(Notification.EXTRA_TITLE))

        LiveNotification.clear(context)
        assertNull(manager.getNotification(LiveNotification.LIVE_ID))
        LiveNotification.clear(context) // idempotent
    }

    @Test
    fun `sans permission de notification showLive ne pose rien`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        LiveNotification.showLive(context, LiveKind.MUSCU)

        assertEquals(0, manager.size())
        assertFalse(LiveNotification.hasPermission(context))
    }

    @Test
    @Config(sdk = [30])
    fun `avant Android 13 la permission est implicite`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(LiveNotification.hasPermission(context))
    }

    @Test
    fun `la notification du service GPS porte le titre et le texte attendus`() {
        val n = LiveNotification.buildOutingNotification(context)

        assertEquals("Sortie en cours", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Élan enregistre ton tracé GPS.", n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(0xFF0D0E0B.toInt(), n.color)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("outing", shadowOf(n.contentIntent).savedIntent.getStringExtra(LiveNotification.EXTRA_OPEN_ROUTE))
        assertNotNull(realManager.getNotificationChannel(LiveNotification.SESSION_CHANNEL))
    }
}
