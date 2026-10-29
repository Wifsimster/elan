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

    const val NOTIFICATION_ID = 3201

    private const val ACCENT = "#0D0E0B"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rappels de séance", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true)
            setShowBadge(false)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun planIndexToDayOfWeek(index: Int): DayOfWeek {
        require(index in 0..6) { "index de planning hors 0..6 : $index" }
        return DayOfWeek.MONDAY.plus(index.toLong())
    }

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
            val exercises = templateById(plan.templateId.key)?.exercises.orEmpty()
            val body = if (exercises.isNotEmpty()) {
                "Au programme : ${exercises.joinToString(", ") { it.name }}."
            } else {
                "Pense à sortir les haltères."
            }
            "Aujourd'hui : ${plan.label}" to body
        }
    }

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
        }
    }
}
