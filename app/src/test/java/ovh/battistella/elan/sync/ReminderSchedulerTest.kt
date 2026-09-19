package ovh.battistella.elan.sync

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.NotificationConfig
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager
        get() = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories

    /** Mercredi 3 juin 2026, 09:30 heure locale. */
    private val zone: ZoneId = ZoneId.systemDefault()
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 6, 3, 9, 30, 0, 0, zone)
    private val clock: Clock = Clock.fixed(now.toInstant(), zone)

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() = db.close()

    private fun scheduler() = ReminderScheduler(context, repos.settings, clock)

    private fun triggers(): List<Long> = alarms.scheduledAlarms.map { it.triggerAtTime }.sorted()

    private fun at(day: DayOfWeek, hour: Int, weeksAhead: Long = 0): Long {
        var date = now.toLocalDate()
        while (date.dayOfWeek != day) date = date.plusDays(1)
        return date.plusWeeks(weeksAhead).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    }

    @Test
    fun `désactivé - aucune alarme`() = runTest {
        scheduler().apply()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `plan par défaut à 12h - une alarme RTC_WAKEUP par jour non-repos, prochaine occurrence`() = runTest {
        repos.settings.setNotifications(NotificationConfig(enabled = true, hour = 12))
        scheduler().apply()

        // Défaut : lundi vélo, mardi Full-body A, vendredi Full-body B.
        // Mercredi 09:30 → vendredi (cette semaine), lundi et mardi (semaine prochaine).
        assertEquals(
            listOf(at(DayOfWeek.FRIDAY, 12), at(DayOfWeek.MONDAY, 12), at(DayOfWeek.TUESDAY, 12)),
            triggers(),
        )
        alarms.scheduledAlarms.forEach {
            assertEquals(AlarmManager.RTC_WAKEUP, it.type)
            assertTrue(it.allowWhileIdle)
            assertEquals(0L, it.interval)
        }
    }

    @Test
    fun `l'heure du jour même déjà passée bascule à la semaine suivante`() = runTest {
        repos.settings.setNotifications(NotificationConfig(enabled = true, hour = 8))
        repos.settings.setCustomWeekPlan(
            listOf(
                PlannedSession.Repos,
                PlannedSession.Repos,
                PlannedSession.Outing("course", "Course"), // mercredi : 08:00 déjà passé
                PlannedSession.Muscu("Full-body B", TemplateId.FULLBODY_B),
                PlannedSession.Repos,
                PlannedSession.Repos,
                PlannedSession.Repos,
            ),
        )
        scheduler().apply()

        assertEquals(listOf(at(DayOfWeek.THURSDAY, 8), at(DayOfWeek.WEDNESDAY, 8, weeksAhead = 1)), triggers())
    }

    @Test
    fun `idempotent - un second apply ne double pas les alarmes`() = runTest {
        repos.settings.setNotifications(NotificationConfig(enabled = true, hour = 12))
        val s = scheduler()
        s.apply()
        s.apply()
        assertEquals(3, alarms.scheduledAlarms.size)
    }

    @Test
    fun `désactivation après activation - tout est annulé`() = runTest {
        repos.settings.setNotifications(NotificationConfig(enabled = true, hour = 12))
        val s = scheduler()
        s.apply()
        assertEquals(3, alarms.scheduledAlarms.size)

        repos.settings.setNotifications(NotificationConfig(enabled = false, hour = 12))
        s.apply()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `codes de requête 100 à 106 - un PendingIntent distinct par jour`() = runTest {
        repos.settings.setNotifications(NotificationConfig(enabled = true, hour = 12))
        scheduler().apply()
        val codes = alarms.scheduledAlarms.map { shadowOf(it.operation).requestCode }.sorted()
        assertEquals(listOf(100, 101, 104), codes)
        alarms.scheduledAlarms.forEach {
            val intent = shadowOf(it.operation).savedIntent
            assertEquals(ReminderReceiver.ACTION_FIRE, intent.action)
            assertEquals(shadowOf(it.operation).requestCode - 100, intent.getIntExtra(ReminderReceiver.EXTRA_DAY, -1))
        }
    }

    @Test
    fun `nextOccurrence - aujourd'hui si l'heure n'est pas passée, sinon dans une semaine`() {
        val today = ReminderScheduler.nextOccurrence(now, 2, 12) // mercredi 12:00
        assertEquals(now.toLocalDate().atTime(12, 0).atZone(zone), today)
        val nextWeek = ReminderScheduler.nextOccurrence(now, 2, 9)
        assertEquals(now.toLocalDate().plusWeeks(1).atTime(9, 0).atZone(zone), nextWeek)
        val exact = ReminderScheduler.nextOccurrence(now.withHour(9).withMinute(0), 2, 9)
        assertEquals(now.toLocalDate().plusWeeks(1).atTime(9, 0).atZone(zone), exact)
    }
}
