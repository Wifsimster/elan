// Notifications « séance en cours » (100 % locales, aucun réseau).
//
// Deux notifications partagent le canal silencieux `session` :
//   - celle du service GPS de premier plan (`TrackingService`), obligatoire
//     pour qu'Android maintienne le GPS écran éteint pendant une sortie ;
//   - la notification persistante de séance (`showLive`), posée par les écrans
//     de séance (muscu) pour revenir à l'écran en un appui.
//
// Best-effort comme dans l'app d'origine : try/catch silencieux, canal créé
// paresseusement. Ne lève jamais, ne bloque jamais le démarrage / l'arrêt
// d'une séance.
package ovh.battistella.elan.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ovh.battistella.elan.MainActivity
import ovh.battistella.elan.R

/** Quelle séance la notification ramène : route ouverte par `MainActivity` au tap. */
enum class LiveKind(val route: String, val title: String) {
    OUTING("outing", "Sortie en cours"),
    MUSCU("muscu", "Séance muscu en cours"),
}

object LiveNotification {
    const val SESSION_CHANNEL = "session"

    /** Identifiant stable de la notification de séance : ré-appeler met à jour, ne duplique pas. */
    const val LIVE_ID = 3001

    /** Identifiant de la notification du service GPS de premier plan. */
    const val FOREGROUND_ID = 3002

    /** Extra posé sur l'intent de `MainActivity` : route à ouvrir (`LiveKind.route`). */
    const val EXTRA_OPEN_ROUTE = "ovh.battistella.elan.OPEN_ROUTE"

    /** Couleur d'accent des notifications (fond PULSE). */
    private const val ACCENT = "#0A0C10"

    /**
     * Android 13+ : `POST_NOTIFICATIONS` est requise pour que les notifications
     * soient visibles. Sans elle, l'enregistrement continue mais sans indicateur.
     */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Canal « Séance en cours » : importance basse, sans son, vibration ni badge. Idempotent. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(SESSION_CHANNEL, "Séance en cours", NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false)
            setShowBadge(false)
            setSound(null, null)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Notification du service GPS de premier plan : « Sortie en cours » /
     * « Élan enregistre ton tracé GPS. ». Un appui ramène à l'écran de sortie.
     */
    fun buildOutingNotification(context: Context): Notification {
        ensureChannel(context)
        return base(context, LiveKind.OUTING)
            .setContentTitle(LiveKind.OUTING.title)
            .setContentText("Élan enregistre ton tracé GPS.")
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Présente (ou remplace) l'unique notification persistante de séance. Best-effort. */
    fun showLive(context: Context, kind: LiveKind) {
        try {
            if (!hasPermission(context)) return
            ensureChannel(context)
            val notification = base(context, kind)
                .setContentTitle(kind.title)
                .setContentText("Touche pour revenir à ta séance.")
                .build()
            NotificationManagerCompat.from(context).notify(LIVE_ID, notification)
        } catch (_: Exception) {
            // Permission refusée, gestionnaire indisponible… : on continue sans.
        }
    }

    /** Efface la notification persistante de séance. Best-effort, idempotent. */
    fun clear(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(LIVE_ID)
        } catch (_: Exception) {
            // Déjà effacée / jamais présentée : rien à faire.
        }
    }

    private fun base(context: Context, kind: LiveKind): NotificationCompat.Builder {
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_ROUTE, kind.route)
        }
        val pending = PendingIntent.getActivity(
            context,
            kind.ordinal,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, SESSION_CHANNEL)
            .setSmallIcon(R.drawable.mdi_bike)
            .setColor(Color.parseColor(ACCENT))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }
}
