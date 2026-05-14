package com.example.metrognome.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.metrognome.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val AD_UNIT_ID_TEST = "ca-app-pub-3940256099942544/6300978111"
private const val AD_UNIT_ID_PROD = "ca-app-pub-8485854692249613/1225603325"

@Composable
fun AdBannerView(modifier: Modifier = Modifier) {
    val unitId = if (BuildConfig.DEBUG) AD_UNIT_ID_TEST else AD_UNIT_ID_PROD
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adaptiveBannerSize(ctx))
                adUnitId = unitId
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier
    )
}

private fun adaptiveBannerSize(context: Context): AdSize {
    val displayMetrics = context.resources.displayMetrics
    val adWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
    return AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp)
}
