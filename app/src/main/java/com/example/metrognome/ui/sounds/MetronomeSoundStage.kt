package com.example.metrognome.ui.sounds

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.audio.selftest.MicCalibration
import com.example.metrognome.billing.CLICK_SOUNDS
import com.example.metrognome.billing.clickSoundFor
import com.example.metrognome.ui.components.instruments.InstrumentAffinity
import com.example.metrognome.viewmodel.MetronomeViewModel

/**
 * The sound stage for the metronome's click voices: the one place that knows how
 * [MetronomeViewModel] and the click list ([CLICK_SOUNDS]) map onto [SoundStageOverlay].
 * Opened from the Home pill ([source] "home") and from a premium chip in Settings
 * ("settings", focused on that chip's sound): one way to browse, hear and buy a click, so
 * the two places cannot drift apart. Settings' old click paywall dialog was removed for it.
 *
 * Rules it inherits rather than restates: an audition replaces a running click and never
 * layers over it (MetronomeEngine.playPreview), and is refused during a mic-scored session
 * (soundPreviewBlocked). Buying goes through [MetronomeViewModel.buySound], which switches
 * to the voice once the purchase lands.
 */
@Composable
fun MetronomeSoundStage(
    vm: MetronomeViewModel,
    source: String,
    onClose: () -> Unit,
    focusSoundIndex: Int? = null,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val soundType by vm.soundType.collectAsStateWithLifecycle()
    val owned by vm.purchasedSoundIds.collectAsStateWithLifecycle()
    val prices by vm.soundPrices.collectAsStateWithLifecycle()
    val available by vm.availableSoundProductIds.collectAsStateWithLifecycle()
    val previewing by vm.soundPreviewing.collectAsStateWithLifecycle()
    val previewBlocked by vm.soundPreviewBlocked.collectAsStateWithLifecycle()
    val purchasing by vm.isPurchasing.collectAsStateWithLifecycle()
    val connecting by vm.isBillingConnecting.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    var auditioned by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { AnalyticsTracker.logSoundStageOpened(source) }

    // Groove Check swaps every voice for Classic during Practice and the Speed Trainer, so
    // the clap detector hears the click it is tuned for. Said up front, on the screen where
    // a voice is bought, so nobody buys one and then wonders why a session plays Classic.
    val grooveCheckOn = remember {
        MicCalibration.read(context).isUsable &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    val sounds = remember(owned, prices, available) {
        CLICK_SOUNDS.map { s ->
            StageSound(
                key = s.slug,
                name = s.displayName,
                tagline = s.tagline,
                glyph = soundGlyphFor(s.slug),
                instruments = InstrumentAffinity.instrumentsFor(s.index),
                premium = s.isPremium,
                owned = s.productId == null || s.productId in owned,
                price = s.productId?.let { prices[it] },
                available = s.productId == null || s.productId in available,
            )
        }
    }
    fun bySlug(key: String) = CLICK_SOUNDS.first { it.slug == key }

    val close = {
        vm.stopSoundPreview()
        onClose()
    }

    SoundStageOverlay(
        eyebrow = "CLICK SOUNDS",
        hint = "Swipe to browse. Tap a sound to hear it.",
        state = SoundStageState(
            sounds = sounds,
            selectedKey = clickSoundFor(soundType).slug,
            previewingKey = if (previewing) auditioned else null,
            previewBlocked = previewBlocked,
            purchasing = purchasing,
            storeConnecting = connecting,
            previewReplacesLive = playing,
            footnote = if (grooveCheckOn)
                "With Groove Check on, Practice and the Speed Trainer play Classic so your claps are heard."
            else null,
        ),
        onPreview = { key ->
            val s = bySlug(key)
            auditioned = key
            vm.previewSound(s.index)
            AnalyticsTracker.logSoundPreviewed(s.slug, s.productId, source)
        },
        onStopPreview = { vm.stopSoundPreview() },
        onChoose = { key -> vm.setSoundType(bySlug(key).index, "stage") },
        onBuy = { key -> activity?.let { vm.buySound(it, bySlug(key).index) } },
        onRestore = { vm.restorePurchases() },
        onClose = close,
        beats = vm.beatEvents,
        initialKey = focusSoundIndex?.let { clickSoundFor(it).slug },
    )
}
