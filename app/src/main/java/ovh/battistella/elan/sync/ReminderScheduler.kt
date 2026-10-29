package ovh.battistella.elan.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.di.ApplicationScope
import ovh.battistella.elan.domain.PlannedSession
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun apply() {
        try {
            for (day in 0..6) alarmManager.cancel(pendingIntent(day))
            val snapshot = settings.snapshot()
            val cfg = snapshot.notifications
            if (!cfg.enabled) return
            ReminderNotifications.ensureChannel(context)
            val plan = snapshot.weekPlan
            val now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.systemDefault())
            for (day in 0..6) {
                val planned = plan.getOrNull(day)
                if (ReminderNotifications.describeToday(planned) == null) continue
                val at = nextOccurrence(now, day, cfg.hour).toInstant().toEpochMilli()
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(day))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "planification des rappels ignorée", e)
        }
    }

    suspend fun onAlarm(day: Int) {
        try {
            val snapshot = settings.snapshot()
            if (snapshot.notifications.enabled) {
                val planned: PlannedSession? = snapshot.weekPlan.getOrNull(day)
                ReminderNotifications.describeToday(planned)?.let { (title, body) ->
                    ReminderNotifications.post(context, title, body)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "rappel du jour ignoré", e)
        }
        apply()
    }

    private fun pendingIntent(day: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_BASE + day,
        Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_DAY, day)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "ReminderScheduler"

        const val REQUEST_CODE_BASE = 100

        fun nextOccurrence(now: ZonedDateTime, day: Int, hour: Int): ZonedDateTime {
            val target = ReminderNotifications.planIndexToDayOfWeek(day)
            val daysAhead = ((target.value - now.dayOfWeek.value) + 7) % 7
            var candidate = now.toLocalDate().plusDays(daysAhead.toLong())
                .atTime(hour.coerceIn(0, 23), 0)
                .atZone(now.zone)
            if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
            return candidate
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun scheduler(): ReminderScheduler
    @ApplicationScope fun scope(): CoroutineScope
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val day = intent.getIntExtra(EXTRA_DAY, -1)
        if (day !in 0..6) return
        val (scheduler, scope) = resolve(context) ?: return
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                scheduler.onAlarm(day)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "ovh.battistella.elan.action.REMINDER_FIRE"
        const val EXTRA_DAY = "day"

        @VisibleForTesting
        internal var override: Pair<ReminderScheduler, CoroutineScope>? = null

        internal fun resolve(context: Context): Pair<ReminderScheduler, CoroutineScope>? {
            override?.let { return it }
            return try {
                val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
                entry.scheduler() to entry.scope()
            } catch (e: Exception) {
                Log.w("ReminderReceiver", "graphe indisponible", e)
                null
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val (scheduler, scope) = ReminderReceiver.resolve(context) ?: return
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                scheduler.apply()
            } finally {
                pending?.finish()
            }
        }
    }
}
