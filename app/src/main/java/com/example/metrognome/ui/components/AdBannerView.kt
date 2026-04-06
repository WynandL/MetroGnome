package com.example.metrognome.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Adaptive banner ad anchored to screen width.
 *
 * TODO: Replace AD_UNIT_ID with your real AdMob banner unit ID before publishing.
 *       Production AdMob App ID goes in AndroidManifest.xml.
 *       Test IDs are safe to use in development only.
 */
private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"  // Google test banner ID

@Composable
fun AdBannerView(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adaptiveBannerSize(ctx))
                adUnitId = AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier
    )
}

private fun adaptiveBannerSize(context: Context): AdSize {
    val displayMetrics = context.resources.displayMetrics
    val adWidthPx = displayMetrics.widthPixels.toFloat()
    val density = displayMetrics.density
    val adWidthDp = (adWidthPx / density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
}
