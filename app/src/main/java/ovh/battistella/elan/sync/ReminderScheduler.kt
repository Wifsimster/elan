// Planification des rappels de séance sur AlarmManager (port de
// `applyNotifications` dans lib/notifications.ts). Une alarme par jour planifié
// non-repos, à `hour:00` heure locale, prochaine occurrence ; chaque alarme
// se réarme pour la semaine suivante en se déclenchant (`ReminderReceiver`),
// et le redémarrage de l'appareil rejoue la planification (`BootReceiver`).
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

    /**
     * Re-planifie tous les rappels selon la config et le planning effectif. À
     * appeler après tout changement de config ou de planning, au démarrage et
     * après un redémarrage. Idempotent : annule les 7 alarmes possibles avant
     * de re-créer celles des jours planifiés. Best-effort.
     */
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
            // Alarmes refusées, planning corrompu… : pas de rappel = défaut acceptable.
            Log.w(TAG, "planification des rappels ignorée", e)
        }
    }

    /**
     * Déclenchement de l'alarme du jour [day] : poste le rappel recalculé
     * depuis le planning EFFECTIF au moment du déclenchement (un planning
     * modifié entre-temps ne rejoue pas l'ancien contenu), puis réarme la
     * semaine suivante.
     */
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

        /** Codes de requête des 7 alarmes : 100 (lundi) … 106 (dimanche). */
        const val REQUEST_CODE_BASE = 100

        /**
         * Prochaine occurrence STRICTEMENT future du jour de planning [day] à
         * `hour:00` local : aujourd'hui si l'heure n'est pas passée, sinon la
         * semaine d'après.
         */
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

/** Accès Hilt depuis les récepteurs (pas d'injection de champ : testables sans graphe). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun scheduler(): ReminderScheduler
    @ApplicationScope fun scope(): CoroutineScope
}

/** Récepteur des alarmes de rappel : poste la notification du jour et réarme. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val day = intent.getIntExtra(EXTRA_DAY, -1)
        if (day !in 0..6) return
        val (scheduler, scope) = resolve(context) ?: return
        // `goAsync()` rend null quand le récepteur n'est pas livré par le système (tests).
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

        /** Substitut de test : le graphe Hilt n'existe pas sous Robolectric. */
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

/** Les alarmes ne survivent pas au redémarrage : on les re-planifie. */
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
