package com.example.metrognome.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * The single notification channel used for messages sent from the Firebase
 * Console / Firestore (see [MetroFcmService]). One channel is enough since
 * these are all the same kind of thing - app news and updates - and a single
 * channel keeps the user's per-channel system settings simple to reason about.
 *
 * Must match `default_notification_channel_id` in AndroidManifest.xml.
 */
object NotificationChannels {
    const val GENERAL_ID = "general"

    /** Idempotent - safe to call on every app start ([android.app.Application.onCreate]). */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                GENERAL_ID,
                "Metro updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New features, sounds, and app news"
            }
        )
    }
}
