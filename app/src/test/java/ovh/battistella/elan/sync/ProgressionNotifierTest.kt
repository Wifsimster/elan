package ovh.battistella.elan.sync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import ovh.battistella.elan.tracking.LiveNotification

@RunWith(RobolectricTestRunner::class)
class ProgressionNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val realManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val manager: ShadowNotificationManager
        get() = shadowOf(realManager)

    @Test
    fun `le canal progression est d'importance par défaut`() {
        ProgressionNotifier.ensureChannel(context)
        ProgressionNotifier.ensureChannel(context) // idempotent

        val channel = realManager.getNotificationChannel(ProgressionNotifier.CHANNEL)
        assertNotNull(channel)
        assertEquals("Progression du programme", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(1, manager.notificationChannels.size)
    }

    @Test
    fun `avec la permission - annonce postée, tap vers la progression`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ProgressionNotifier.notify(context, "Ton programme monte d’un cran", "Goblet squat 20 → 22,5 kg.")

        val n = manager.getNotification(ProgressionNotifier.NOTIFICATION_ID)
        assertNotNull(n)
        assertEquals("Ton programme monte d’un cran", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Goblet squat 20 → 22,5 kg.", n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(ProgressionNotifier.CHANNEL, n.channelId)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        val target = shadowOf(n.contentIntent).savedIntent
        assertEquals("ovh.battistella.elan.MainActivity", target.component?.className)
        assertEquals("progression", target.getStringExtra(LiveNotification.EXTRA_OPEN_ROUTE))

        // Une nouvelle annonce remplace la précédente.
        ProgressionNotifier.notify(context, "On lève le pied cette semaine", "Gainage planche 30 → 25 s.")
        assertEquals(1, manager.size())
    }

    @Test
    fun `sans permission déjà accordée - rien n'est posté ni réclamé`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ProgressionNotifier.notify(context, "Titre", "Corps")

        assertNull(manager.getNotification(ProgressionNotifier.NOTIFICATION_ID))
        assertEquals(0, manager.size())
    }
}
