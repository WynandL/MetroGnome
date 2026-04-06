package com.example.metrognome

import android.app.Application
import com.google.android.gms.ads.MobileAds

class MetroGnomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
    }
}
