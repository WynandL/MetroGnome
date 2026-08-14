package com.example.metrognome.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.metrognome.MainActivity
import com.example.metrognome.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives messages sent from the Firebase Console or a Firestore-triggered Cloud
 * Function to the [NotificationTopics.ALL_USERS] topic (or, for a notification-only
 * payload with the app backgrounded, the system posts it directly using the
 * `default_notification_*` meta-data in AndroidManifest.xml and this method is
 * never called - that path only reaches [onMessageReceived] for data payloads or a
 * foregrounded app).
 *
 * Deep link: an optional `openTab` data key ("gnome", "tuner", "rhythm", or "settings",
 * case-insensitive) opens straight to that tab on tap - see [MainActivity.EXTRA_OPEN_TAB].
 * Only wired up here for the [onMessageReceived] path; when the system posts the
 * notification directly instead (the case above), FCM's SDK automatically copies every
 * data-payload key, `openTab` included, onto the launch Intent's extras itself, so no
 * separate handling is needed for that path.
 *
 * No token handling: broadcasting to everyone is done via topic subscription
 * ([NotificationTopics]), not per-device tokens, so there is nothing to upload
 * or store.
 */
class MetroFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"]
        showNotification(title, body, message.data["openTab"])
    }

    private fun showNotification(title: String, body: String?, openTab: String?) {
        NotificationChannels.ensureCreated(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (openTab != null) putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.GENERAL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(body?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        // A single fixed id: a new broadcast replaces the previous one in the tray
        // rather than stacking, which matches how the console is used (announce
        // the latest thing).
        private const val NOTIFICATION_ID = 1001
    }
}
