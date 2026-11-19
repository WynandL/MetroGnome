package com.example.metrognome.ui.sounds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.audio.drone.DroneBlend
import com.example.metrognome.audio.drone.DroneState
import com.example.metrognome.audio.drone.DroneTimbre
import com.example.metrognome.billing.PurchaseStore
import com.example.metrognome.billing.premiumProductFor

/** Which of the drone's two kinds of voice the stage is showing. */
enum class DroneVoiceTab(val label: String) { TIMBRE("Timbre"), BLEND("Blend") }

/**
 * The sound stage for the tuning drone: the same [SoundStageOverlay] the click voices use,
 * with two groups, TIMBRE (what the tone is made of) and BLEND (which notes sound). Replaced
 * the drone's chip rows and its purchase dialog on 2026-11-19, so a drone voice is browsed,
 * heard and bought the way a click is.
 *
 * Stateless towards the ViewModel, like TunerScreenContent: it takes the drone's state, the
 * store snapshot and callbacks. An audition is the real drone (TunerViewModel.previewDroneVoice):
 * a timbre is heard with the blend in use, and a blend with the timbre in use, so what is
 * heard is exactly what choosing it would give. The footnote says so.
 *
 * Buying selects the voice once Play confirms it, here rather than in a ViewModel because the
 * purchase state arrives as the [store] snapshot (one BillingManager, owned by
 * MetronomeViewModel; see PurchaseStore).
 */
@Composable
fun DroneSoundStage(
    drone: DroneState,
    store: PurchaseStore,
    previewing: Boolean,
    onPreview: (DroneTimbre, DroneBlend) -> Unit,
    onStopPreview: () -> Unit,
    onSetTimbre: (DroneTimbre) -> Unit,
    onSetBlend: (DroneBlend) -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    initialTab: DroneVoiceTab = DroneVoiceTab.TIMBRE,
) {
    var tab by remember { mutableIntStateOf(initialTab.ordinal) }
    var auditioned by remember { mutableStateOf<String?>(null) }
    var awaiting by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { AnalyticsTracker.logSoundStageOpened("tuner") }

    // Select a voice the moment its purchase lands.
    LaunchedEffect(store.purchasedProductIds) {
        val key = awaiting ?: return@LaunchedEffect
        val product = productFor(key) ?: return@LaunchedEffect
        if (store.owns(product)) {
            awaiting = null
            select(key, onSetTimbre, onSetBlend)
        }
    }

    val timbres = DroneTimbre.entries.map { t ->
        val product = premiumProductFor(t)
        StageSound(
            key = keyOf(t), name = t.displayName, tagline = t.caption, glyph = droneGlyphFor(t),
            premium = product != null,
            owned = product == null || store.owns(product),
            price = product?.let { store.priceOf(it) },
            available = product == null || store.isAvailable(product),
        )
    }
    val blends = DroneBlend.entries.map { b ->
        val product = premiumProductFor(b)
        StageSound(
            key = keyOf(b), name = b.displayName, tagline = b.caption, glyph = droneGlyphFor(b),
            premium = product != null,
            owned = product == null || store.owns(product),
            price = product?.let { store.priceOf(it) },
            available = product == null || store.isAvailable(product),
        )
    }
    val onTimbres = tab == DroneVoiceTab.TIMBRE.ordinal

    SoundStageOverlay(
        eyebrow = "DRONE VOICES",
        hint = "Swipe to browse. Tap a voice to hear it on your note.",
        state = SoundStageState(
            sounds = if (onTimbres) timbres else blends,
            selectedKey = if (onTimbres) keyOf(drone.timbre) else keyOf(drone.blend),
            previewingKey = if (previewing) auditioned else null,
            purchasing = store.isPurchasing,
            storeConnecting = store.isConnecting,
            previewReplacesLive = drone.playing,
            footnote = if (onTimbres) "Each timbre plays with your blend, ${drone.blend.displayName}."
                       else "Each blend plays with your timbre, ${drone.timbre.displayName}.",
            tabs = DroneVoiceTab.entries.map { it.label },
            tabIndex = tab,
        ),
        onTab = { onStopPreview(); tab = it },
        onPreview = { key ->
            auditioned = key
            timbreOf(key)?.let { onPreview(it, drone.blend) }
            blendOf(key)?.let { onPreview(drone.timbre, it) }
        },
        onStopPreview = onStopPreview,
        onChoose = { key -> select(key, onSetTimbre, onSetBlend) },
        onBuy = { key ->
            val product = productFor(key) ?: return@SoundStageOverlay
            awaiting = key
            onPurchase(product)
        },
        onRestore = onRestore,
        onClose = {
            onStopPreview()
            onClose()
        },
    )
}

private fun keyOf(t: DroneTimbre) = "timbre_${t.name.lowercase()}"
private fun keyOf(b: DroneBlend) = "blend_${b.name.lowercase()}"
private fun timbreOf(key: String) = DroneTimbre.entries.firstOrNull { keyOf(it) == key }
private fun blendOf(key: String) = DroneBlend.entries.firstOrNull { keyOf(it) == key }
private fun productFor(key: String): String? =
    timbreOf(key)?.let { premiumProductFor(it) } ?: blendOf(key)?.let { premiumProductFor(it) }

private fun select(key: String, onSetTimbre: (DroneTimbre) -> Unit, onSetBlend: (DroneBlend) -> Unit) {
    timbreOf(key)?.let(onSetTimbre)
    blendOf(key)?.let(onSetBlend)
}
