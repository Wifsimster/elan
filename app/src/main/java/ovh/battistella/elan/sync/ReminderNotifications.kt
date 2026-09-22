// Rappels de séance (opt-in) : une notification locale le jour même d'une
// séance planifiée, à l'heure choisie (midi par défaut). Port des fonctions
// pures de lib/notifications.ts (`describeToday`, `planIndexToWeekday`) et de
// la mise en forme de la notification. La planification est 100 % locale
// (AlarmManager, voir `ReminderScheduler`) : aucun service push.
package ovh.battistella.elan.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ovh.battistella.elan.MainActivity
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.tracking.LiveNotification
import java.time.DayOfWeek

object ReminderNotifications {
    const val CHANNEL = "routine"

    /** Identifiant stable : le rappel du jour remplace celui de la veille. */
    const val NOTIFICATION_ID = 3201

    private const val ACCENT = "#0D0E0B"

    /** Canal « Rappels de séance », importance par défaut, vibration. Idempotent. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rappels de séance", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true)
            setShowBadge(false)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Index du planning (0 = lundi … 6 = dimanche) → jour de la semaine. */
    fun planIndexToDayOfWeek(index: Int): DayOfWeek {
        require(index in 0..6) { "index de planning hors 0..6 : $index" }
        return DayOfWeek.MONDAY.plus(index.toLong())
    }

    /**
     * Contenu du rappel pour une séance planifiée : (titre, corps), ou `null`
     * les jours de repos (et pour un plan absent). Fonction pure.
     */
    fun describeToday(plan: PlannedSession?): Pair<String, String>? = when (plan) {
        null, is PlannedSession.Repos -> null
        is PlannedSession.Outing -> {
            val body = if (plan.kind == "velo") {
                "Pense à préparer le vélo et la ceinture."
            } else {
                "Pense à préparer les chaussures et la ceinture."
            }
            "Aujourd'hui : ${plan.label}" to body
        }
        is PlannedSession.Muscu -> {
            // On liste les exercices du programme du jour, pour rappeler
            // précisément quoi faire. Repli générique si le template est
            // introuvable (plan corrompu).
            val exercises = templateById(plan.templateId.key)?.exercises.orEmpty()
            val body = if (exercises.isNotEmpty()) {
                "Au programme : ${exercises.joinToString(", ") { it.name }}."
            } else {
                "Pense à sortir les haltères."
            }
            "Aujourd'hui : ${plan.label}" to body
        }
    }

    /** Poste le rappel si la permission est accordée ; sinon ne fait rien. Best-effort. */
    fun post(context: Context, title: String, body: String) {
        try {
            if (!LiveNotification.hasPermission(context)) return
            ensureChannel(context)
            val open = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.mdi_calendar_check)
                .setColor(Color.parseColor(ACCENT))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Permission révoquée, gestionnaire indisponible… : on continue sans.
        }
    }
}
