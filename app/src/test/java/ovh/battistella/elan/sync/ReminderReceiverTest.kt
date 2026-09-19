package ovh.battistella.elan.sync

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.NotificationConfig
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager
        get() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val notifications
        get() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories

    /** Vendredi 5 juin 2026, 12:00 heure locale — l'instant du rappel du vendredi. */
    private val zone: ZoneId = ZoneId.systemDefault()
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 6, 5, 12, 0, 0, 0, zone)

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val scheduler = ReminderScheduler(context, repos.settings, Clock.fixed(now.toInstant(), zone))
        ReminderReceiver.override = scheduler to CoroutineScope(Dispatchers.Unconfined)
        runBlocking { repos.settings.setNotifications(NotificationConfig(enabled = true, hour = 12)) }
    }

    @After
    fun tearDown() {
        ReminderReceiver.override = null
        db.close()
    }

    private fun fire(intent: Intent) {
        ReminderReceiver().onReceive(context, intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `alarme du vendredi - rappel Full-body B posté et alarme réarmée à J+7`() {
        fire(Intent(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_DAY, 4))

        val n = notifications.getNotification(ReminderNotifications.NOTIFICATION_ID)
        assertNotNull(n)
        assertEquals("Aujourd'hui : Full-body B", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getString(Notification.EXTRA_TEXT)!!.startsWith("Au programme : "))

        // Réarmement : les trois jours planifiés, le vendredi désormais dans une semaine.
        val friday = alarms.scheduledAlarms.single { shadowOf(it.operation).requestCode == 104 }
        assertEquals(now.plusWeeks(1).toInstant().toEpochMilli(), friday.triggerAtTime)
        assertEquals(3, alarms.scheduledAlarms.size)
        assertEquals(DayOfWeek.FRIDAY, ReminderNotifications.planIndexToDayOfWeek(4))
    }

    @Test
    fun `alarme d'un jour de repos - rien de posté mais réarmement`() {
        fire(Intent(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_DAY, 2))
        assertEquals(0, notifications.size())
        assertEquals(3, alarms.scheduledAlarms.size)
    }

    @Test
    fun `rappels désactivés entre-temps - rien de posté, aucune alarme`() {
        runBlocking { repos.settings.setNotifications(NotificationConfig(enabled = false, hour = 12)) }
        fire(Intent(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_DAY, 4))
        assertEquals(0, notifications.size())
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `action inconnue ou jour invalide - ignorés`() {
        fire(Intent("autre").putExtra(ReminderReceiver.EXTRA_DAY, 4))
        fire(Intent(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_DAY, 9))
        assertEquals(0, notifications.size())
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `redémarrage - BootReceiver re-planifie`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, alarms.scheduledAlarms.size)
        assertEquals(0, notifications.size())
    }
}
