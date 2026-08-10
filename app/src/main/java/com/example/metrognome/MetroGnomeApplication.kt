package com.example.metrognome

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.metrognome.notifications.NotificationChannels
import com.example.metrognome.notifications.NotificationTopics
import com.google.android.gms.ads.MobileAds

class MetroGnomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)

        NotificationChannels.ensureCreated(this)
        // Re-subscribe on every start, not just first grant: cheap and idempotent, and it
        // is what keeps a device subscribed after e.g. a Play Store re-install where the
        // permission (and therefore eligibility) may have changed since last launch.
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (notificationsGranted) NotificationTopics.subscribeToAllUsers()
    }
}
