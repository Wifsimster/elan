// Port de __tests__/lib/notifications.test.ts (fonctions pures) + canal et
// notification postée.
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
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.templateById
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class ReminderNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val realManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `planIndexToDayOfWeek - 0 = lundi, 6 = dimanche, sans doublon`() {
        assertEquals(DayOfWeek.MONDAY, ReminderNotifications.planIndexToDayOfWeek(0))
        assertEquals(DayOfWeek.FRIDAY, ReminderNotifications.planIndexToDayOfWeek(4))
        assertEquals(DayOfWeek.SATURDAY, ReminderNotifications.planIndexToDayOfWeek(5))
        assertEquals(DayOfWeek.SUNDAY, ReminderNotifications.planIndexToDayOfWeek(6))
        assertEquals(DayOfWeek.entries.toSet(), (0..6).map(ReminderNotifications::planIndexToDayOfWeek).toSet())
    }

    @Test
    fun `describeToday - null les jours de repos et sans plan`() {
        assertNull(ReminderNotifications.describeToday(PlannedSession.Repos))
        assertNull(ReminderNotifications.describeToday(null))
    }

    @Test
    fun `describeToday - vélo`() {
        val (title, body) = ReminderNotifications.describeToday(PlannedSession.Outing("velo", "Vélo 1h"))!!
        assertEquals("Aujourd'hui : Vélo 1h", title)
        assertEquals("Pense à préparer le vélo et la ceinture.", body)
    }

    @Test
    fun `describeToday - course et marche = chaussures`() {
        assertEquals(
            "Pense à préparer les chaussures et la ceinture.",
            ReminderNotifications.describeToday(PlannedSession.Outing("course", "Course 30 min"))!!.second,
        )
        assertEquals(
            "Aujourd'hui : Marche" to "Pense à préparer les chaussures et la ceinture.",
            ReminderNotifications.describeToday(PlannedSession.Outing("marche", "Marche")),
        )
    }

    @Test
    fun `describeToday - muscu liste les exercices du template`() {
        val (title, body) = ReminderNotifications.describeToday(PlannedSession.Muscu("Full-body A", TemplateId.FULLBODY_A))!!
        assertEquals("Aujourd'hui : Full-body A", title)
        assertTrue(body.startsWith("Au programme : "))
        assertTrue(body.endsWith("."))
        val names = templateById(TemplateId.FULLBODY_A).exercises.map { it.name }
        assertTrue(names.isNotEmpty())
        names.forEach { assertTrue("$it manquant", body.contains(it)) }
        assertEquals("Au programme : ${names.joinToString(", ")}.", body)
    }

    @Test
    fun `le canal routine est d'importance par défaut avec vibration`() {
        ReminderNotifications.ensureChannel(context)
        ReminderNotifications.ensureChannel(context) // idempotent

        val channel = realManager.getNotificationChannel(ReminderNotifications.CHANNEL)
        assertNotNull(channel)
        assertEquals("Rappels de séance", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertEquals(1, shadowOf(realManager).notificationChannels.size)
    }

    @Test
    fun `post - avec permission, notification postée vers MainActivity`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ReminderNotifications.post(context, "Aujourd'hui : Vélo 1h", "Pense à préparer le vélo et la ceinture.")

        val n = shadowOf(realManager).getNotification(ReminderNotifications.NOTIFICATION_ID)
        assertNotNull(n)
        assertEquals("Aujourd'hui : Vélo 1h", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Pense à préparer le vélo et la ceinture.", n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(ReminderNotifications.CHANNEL, n.channelId)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals("ovh.battistella.elan.MainActivity", shadowOf(n.contentIntent).savedIntent.component?.className)
    }

    @Test
    fun `post - sans permission, rien`() {
        ReminderNotifications.post(context, "t", "b")
        assertEquals(0, shadowOf(realManager).size())
    }
}
