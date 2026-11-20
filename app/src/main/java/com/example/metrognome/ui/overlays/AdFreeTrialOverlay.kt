package com.example.metrognome.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.example.metrognome.adfree.AdFreeTrialManager
import com.example.metrognome.adfree.AdFreeTrialPolicy
import com.example.metrognome.adfree.TrialCard
import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcon
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.GhostButton
import com.example.metrognome.ui.components.GoldButton
import com.example.metrognome.ui.dialogs.DialogTitle
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay

/** How long the app must stay calm before a trial card lands, so it never interrupts a tap. */
private const val CALM_SETTLE_MS = 1_500L

private val TRIAL_HOURS = (AdFreeTrialPolicy.DURATION_MS / 3_600_000L).toInt()

/**
 * Shows the ad-free trial's cards, app-wide, at a calm moment. Placed once in MetroGnomeApp,
 * over every tab.
 *
 * [calm] is the app's own state (nothing sounding, no session or game, no poll card); this
 * adds that no dialog or overlay is up ([ModalPresence]), the activity is resumed, and
 * [quietNow] (no interstitial in the last few seconds), all held for [CALM_SETTLE_MS]. Once a
 * card is up it stays until answered: the card itself is a modal, so "calm" goes false the
 * moment it shows, and that must not take it away. The latch survives rotation.
 */
@Composable
fun AdFreeTrialHost(
    trial: AdFreeTrialManager,
    calm: Boolean,
    quietNow: () -> Boolean,
    removeAdsPrice: String?,
    onRemoveAds: () -> Unit,
) {
    val due by trial.card.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var showing by rememberSaveable { mutableStateOf<TrialCard?>(null) }
    val noModal = !ModalPresence.anyOpen

    LaunchedEffect(due, calm, resumed, noModal, showing) {
        val card = due
        if (showing != null || card == null || !calm || !resumed || !noModal) return@LaunchedEffect
        // Any change to the keys restarts this wait, so the app must stay calm throughout.
        while (true) {
            delay(CALM_SETTLE_MS)
            if (quietNow()) break
        }
        showing = card
        trial.onCardShown(card)
    }

    // A dev preview (Settings dev tools) shows over everything at once and changes nothing.
    val preview by trial.preview.collectAsStateWithLifecycle()
    when (preview) {
        TrialCard.OFFER -> AdFreeTrialOfferCard(
            lastOffer = false,
            onTry = trial::closePreview,
            onNotNow = trial::closePreview,
        )
        TrialCard.ENDED -> AdFreeTrialEndedCard(
            removeAdsPrice = removeAdsPrice,
            onOk = trial::closePreview,
            onRemoveAds = trial::closePreview,
        )
        null -> Unit
    }
    if (preview != null) return

    when (showing) {
        TrialCard.OFFER -> AdFreeTrialOfferCard(
            lastOffer = trial.isLastOffer(),
            onTry = { trial.accept(); showing = null },
            onNotNow = { trial.decline(); showing = null },
        )
        TrialCard.ENDED -> AdFreeTrialEndedCard(
            removeAdsPrice = removeAdsPrice,
            onOk = { trial.endNoticeSeen(); showing = null },
            onRemoveAds = { trial.endNoticeSeen(); showing = null; onRemoveAds() },
        )
        null -> Unit
    }
}

/**
 * The offer. Says everything up front: what goes, what Metro gains, when the hours start and
 * that they run with the app closed, where the time left is shown, and that it happens once.
 * Back is "Not now".
 */
@Composable
fun AdFreeTrialOfferCard(
    lastOffer: Boolean,
    onTry: () -> Unit,
    onNotNow: () -> Unit,
) {
    CelebrationCard(onBack = onNotNow) {
        OverlayEyebrow("✦  A GIFT FROM METRO  ✦", color = AppColors.gold)
        Spacer(Modifier.height(16.dp))
        Text(
            "$TRIAL_HOURS",
            color = AppColors.gold, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Black,
        )
        Text(
            "HOURS WITHOUT ADS",
            color = AppColors.goldSoft, fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(18.dp))
        DialogTitle("Try MetroGnome ad-free")
        Spacer(Modifier.height(8.dp))
        Text(
            "No banners and no full-screen ads, and Metro gets the full stage, with more room to dance.",
            color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TrialFact(AppIcons.Timer, "The $TRIAL_HOURS hours start when you tap Try it, and keep running whether the app is open or closed.")
            TrialFact(AppIcons.Settings, "See the time left any time in Settings, under Remove Ads.")
            TrialFact(AppIcons.Bell, "This is a one-time trial. When it ends, ads come back and we will let you know.")
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GhostButton("Not now", onNotNow, Modifier.weight(1f))
            GoldButton("Try it", onTry, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (lastOffer) "This is the last time this trial is offered."
            else "Not now saves it: we will offer it once more in a few days.",
            color = AppColors.textDim, fontSize = 11.sp, lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The end. Shown only when ads really are back (never after a purchase, or while the daily
 * Gnotes ad-free days still cover the user). Back is "OK".
 */
@Composable
fun AdFreeTrialEndedCard(
    removeAdsPrice: String?,
    onOk: () -> Unit,
    onRemoveAds: () -> Unit,
) {
    CelebrationCard(onBack = onOk) {
        OverlayEyebrow("✦  TRIAL ENDED  ✦", color = AppColors.goldSoft)
        Spacer(Modifier.height(16.dp))
        DialogTitle("Ads are back")
        Spacer(Modifier.height(8.dp))
        Text(
            "Your $TRIAL_HOURS ad-free hours are over, so banner and full-screen ads have returned.",
            color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Remove Ads keeps Metro on the full stage for good" +
                (if (removeAdsPrice != null) ": a one-time $removeAdsPrice, " else ": one purchase, ") +
                "no subscription. You can also find it in Settings.",
            color = AppColors.textMuted, fontSize = 13.sp, lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GoldButton("Remove ads for good", onRemoveAds, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        GhostButton("OK", onOk, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TrialFact(glyph: AppGlyph, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        AppIcon(glyph, contentDescription = null, tint = AppColors.gold, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
