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
import ovh.battistella.elan.tracking.LiveNotification

fun interface ProgressionNotify {
    fun notify(title: String, body: String)
}

object ProgressionNotifier {
    const val CHANNEL = "progression"

    const val NOTIFICATION_ID = 3101

    const val ROUTE = "progression"

    private const val ACCENT = "#0D0E0B"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Progression du programme", NotificationManager.IMPORTANCE_DEFAULT)
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun notify(context: Context, title: String, body: String) {
        try {
            if (!LiveNotification.hasPermission(context)) return
            ensureChannel(context)
            val open = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LiveNotification.EXTRA_OPEN_ROUTE, ROUTE)
            }
            val pending = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.mdi_trending_up)
                .setColor(Color.parseColor(ACCENT))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }
}
