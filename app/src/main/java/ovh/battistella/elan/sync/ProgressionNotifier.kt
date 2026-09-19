// Notification « programme régénéré » (port de `notifyProgression` dans
// `lib/notifications.ts`). Canal `progression` (« Progression du programme »,
// importance par défaut). N'annonce QUE si la permission est déjà accordée :
// on ne la réclame pas ici, la bannière d'accueil reste le canal garanti.
// 100 % local, échec silencieux.
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

/** Point d'entrée injectable de la notification (les tests substituent un enregistreur). */
fun interface ProgressionNotify {
    fun notify(title: String, body: String)
}

object ProgressionNotifier {
    const val CHANNEL = "progression"

    /** Identifiant stable : une nouvelle annonce remplace la précédente. */
    const val NOTIFICATION_ID = 3101

    /** Route ouverte par `MainActivity` au tap (extra [LiveNotification.EXTRA_OPEN_ROUTE]). */
    const val ROUTE = "progression"

    private const val ACCENT = "#0A0C10"

    /** Canal « Progression du programme », importance par défaut. Idempotent. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Progression du programme", NotificationManager.IMPORTANCE_DEFAULT)
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Poste l'annonce si la permission est déjà accordée ; sinon ne fait rien. Best-effort. */
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
            // Permission révoquée, gestionnaire indisponible… : on continue sans.
        }
    }
}
