package com.example.metrognome.ads

import android.app.Activity
import android.content.Context
import com.example.metrognome.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

// TODO: Create an interstitial unit in AdMob console and replace the prod ID below.
private const val AD_UNIT_ID_TEST = "ca-app-pub-3940256099942544/1033173712"
private const val AD_UNIT_ID_PROD = "ca-app-pub-8485854692249613/2040537682"

class InterstitialAdManager(private val context: Context) {

    private var loadedAd: InterstitialAd? = null
    private var isLoading = false
    private var gamesPlayed = 0
    private var lastShownMs = 0L

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        val unitId = if (BuildConfig.DEBUG) AD_UNIT_ID_TEST else AD_UNIT_ID_PROD
        InterstitialAd.load(context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadedAd = null
                    isLoading = false
                }
            })
    }

    // Shows the ad every 3rd game and at most once per 5 minutes.
    fun showIfReady(activity: Activity, onDone: () -> Unit) {
        gamesPlayed++
        val shouldShow = gamesPlayed % 3 == 0 &&
                System.currentTimeMillis() - lastShownMs >= 5 * 60_000L

        val ad = loadedAd
        if (!shouldShow || ad == null) {
            if (ad == null) preload()
            onDone()
            return
        }
        lastShownMs = System.currentTimeMillis()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadedAd = null
                preload()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadedAd = null
                preload()
                onDone()
            }
        }
        loadedAd = null
        ad.show(activity)
    }
}
