package com.example.metrognome

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metrognome.ads.AdManager
import com.example.metrognome.ads.AdPlacement
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.haptics.HapticEngine
import com.example.metrognome.haptics.LocalHaptics
import androidx.compose.runtime.CompositionLocalProvider
import com.example.metrognome.review.AppReviewManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.metrognome.ui.components.AdBreakBanner
import com.example.metrognome.ui.components.LoyaltyMilestoneBanner
import com.example.metrognome.ui.components.PointsEarnedBanner
import com.example.metrognome.ui.components.RhythmPulseIcon
import com.example.metrognome.ui.components.TunerNeedleIcon
import com.example.metrognome.ui.dialogs.NotificationOptInDialog
import com.example.metrognome.ui.screens.MetronomeScreen
import com.example.metrognome.ui.screens.RhythmGameScreen
import com.example.metrognome.ui.screens.SettingsScreen
import android.widget.Toast
import com.example.metrognome.billing.PurchaseStore
import com.example.metrognome.ui.screens.TunerScreen
import com.example.metrognome.ui.theme.MetroGnomeTheme
import com.example.metrognome.notifications.NotificationOptInTracker
import com.example.metrognome.notifications.rememberNotificationPermissionState
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.viewmodel.RhythmGameViewModel
import com.example.metrognome.viewmodel.SpeedTrainerViewModel
import com.example.metrognome.viewmodel.TunerViewModel

enum class AppTab(val label: String) {
    GNOME("Gnome"),
    TUNER("Tuner"),
    RHYTHM("Rhythm"),
    SETTINGS("Settings"),
}

class MainActivity : ComponentActivity() {

    // Deep-link target from a tapped FCM notification (see EXTRA_OPEN_TAB / MetroFcmService).
    // A plain Compose state field on the Activity, not inside MetroGnomeApp: onNewIntent is an
    // Activity callback, fired before Compose recomposes, so it needs somewhere to land that
    // both onCreate/onNewIntent and the composable can see. MetroGnomeApp consumes it (resets
    // to null) once applied, so re-tapping a notification for the same tab while already on
    // it still re-triggers - see the LaunchedEffect(openTab) there.
    private var pendingOpenTab by mutableStateOf<AppTab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route the hardware volume keys to the media stream so they adjust the click /
        // mic-check playback the user actually hears (not the ring stream), even when
        // nothing is playing yet - e.g. while the mic-check dialog nudges "turn it up".
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        pendingOpenTab = tabFromIntent(intent)
        setContent {
            MetroGnomeTheme {
                MetroGnomeApp(
                    openTab = pendingOpenTab,
                    onOpenTabConsumed = { pendingOpenTab = null },
                )
            }
        }
    }

    // launchMode="singleTop" (AndroidManifest.xml) means a tapped notification while the app
    // is already running/backgrounded reuses this instance via onNewIntent instead of a fresh
    // onCreate - which matters here because a fresh onCreate would tear down and recreate every
    // ViewModel, killing an in-progress metronome/tuner/rhythm session just to open a tab.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenTab = tabFromIntent(intent)
    }

    private fun tabFromIntent(intent: Intent?): AppTab? {
        val raw = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: return null
        return AppTab.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    companion object {
        /** Intent extra key for deep-linking to a tab; value is an [AppTab] name, e.g. "rhythm". */
        const val EXTRA_OPEN_TAB = "openTab"
    }
}

@Composable
fun MetroGnomeApp(
    openTab: AppTab? = null,
    onOpenTabConsumed: () -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.GNOME) }

    val metronomeVm: MetronomeViewModel = viewModel()
    val rhythmVm: RhythmGameViewModel = viewModel()
    val tunerVm: TunerViewModel = viewModel()
    val speedTrainerVm: SpeedTrainerViewModel = viewModel()
    val isAdFree by metronomeVm.isAdFree.collectAsStateWithLifecycle()
    val isPlaying by metronomeVm.isPlaying.collectAsStateWithLifecycle()

    // Purchase state is gathered once, here, because MetronomeViewModel owns the single
    // BillingManager and two screens now sell things: Settings (clicks, items, ad removal)
    // and the Tuner (drone voices). Settings reads the ViewModel directly; the Tuner gets
    // this snapshot, so there is still exactly one billing connection in the process.
    val purchasedSoundIds by metronomeVm.purchasedSoundIds.collectAsStateWithLifecycle()
    val soundPrices by metronomeVm.soundPrices.collectAsStateWithLifecycle()
    val availableSoundProductIds by metronomeVm.availableSoundProductIds.collectAsStateWithLifecycle()
    val isPurchasing by metronomeVm.isPurchasing.collectAsStateWithLifecycle()
    val isBillingConnecting by metronomeVm.isBillingConnecting.collectAsStateWithLifecycle()
    val purchaseStore = PurchaseStore(
        purchasedProductIds = purchasedSoundIds,
        prices = soundPrices,
        availableProductIds = availableSoundProductIds,
        isPurchasing = isPurchasing,
        isConnecting = isBillingConnecting,
    )

    // The drone enforces its own entitlements (a refund can take a voice away mid-note), so
    // the tuner is told what is owned rather than reaching for a billing client of its own.
    LaunchedEffect(purchasedSoundIds) { tunerVm.setOwnedProducts(purchasedSoundIds) }

    val visibleTabs = AppTab.entries

    val context = LocalContext.current
    val activity = LocalActivity.current
    val adManager     = remember { AdManager(context).also { it.preload() } }
    val reviewManager = remember { AppReviewManager(context) }
    val hapticEngine  = remember { HapticEngine(context) }

    LaunchedEffect(Unit) { AnalyticsTracker.updateUserTier(adManager.userTier()) }

    // A failed purchase is reported app-wide rather than by the screen that started it.
    // It used to live in SettingsScreen, which was fine while Settings was the only place
    // you could buy anything; with the Tuner selling drone voices, a failure there would
    // have gone unseen and then surfaced as a stale toast the next time Settings opened.
    val purchaseError by metronomeVm.purchaseError.collectAsStateWithLifecycle()
    LaunchedEffect(purchaseError) {
        val message = purchaseError ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        metronomeVm.clearPurchaseError()
    }

    // Single path for changing tabs, used by both the nav bar's onClick below AND a
    // notification deep link (LaunchedEffect(openTab) further down) - extracted so a
    // deep link can never skip the RHYTHM-leave guard or the review-prompt trigger the
    // way a direct `currentTab = tab` assignment would.
    fun switchTab(tab: AppTab) {
        if (currentTab == AppTab.RHYTHM && tab != AppTab.RHYTHM) {
            rhythmVm.stopGame()
        }
        // Ask for a review when the user lands on a calm, ad-free tab (Tuner/Settings)
        // from elsewhere, never if an ad showed recently.
        val landingOnCalmTab = tab != currentTab && (tab == AppTab.TUNER || tab == AppTab.SETTINGS)
        currentTab = tab
        if (landingOnCalmTab && !adManager.recentlyShowedAd()) {
            activity?.let { reviewManager.maybeRequestReview(it) }
        }
    }

    // Apply a notification deep link once it arrives (see MainActivity.pendingOpenTab), then
    // consume it. Consuming (resetting the source back to null) rather than just reading it once
    // is what lets a second notification tap for the same tab re-apply, since the LaunchedEffect
    // key only changes on a null -> value transition.
    LaunchedEffect(openTab) {
        if (openTab != null) {
            switchTab(openTab)
            onOpenTabConsumed()
        }
    }

    // The one strategic notification-permission ask: fired the first time the user opens
    // the app on a 2nd distinct calendar day (see UsageDayTracker, the same "distinct days
    // opened" counter LoyaltyDays items use - reused directly rather than adding a second,
    // possibly-diverging day-boundary calculation). Chosen over the old first-item-unlock
    // trigger 2026-08-14: that one only fired for users who engaged with the cosmetic-item
    // system at all, so plain-metronome users and users who already owned every item before
    // this feature shipped could never be asked. "Came back at all" is a faster, universal
    // signal that doesn't depend on unrelated item-unlock pacing. Shown at most once ever -
    // see NotificationOptInTracker and NotificationOptInDialog.
    val notificationPermission = rememberNotificationPermissionState()
    val notificationOptInTracker = remember { NotificationOptInTracker(context) }
    val usageDayTracker = remember { com.example.metrognome.points.UsageDayTracker(context) }
    var showNotificationAsk by remember { mutableStateOf(false) }

    // Re-query Play Store for owned purchases every time the app returns to foreground.
    // This handles: switching devices, purchasing on one device then opening another,
    // and any case where the local cache drifts from Play's source of truth. Also count
    // this as a distinct day opened (drives loyalty, which gates review eligibility, and
    // now also the notification ask above).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        metronomeVm.reconcilePurchases()
        metronomeVm.refreshReward()
        metronomeVm.recordUsageDay()
        if (!notificationOptInTracker.hasShownContextualAsk &&
            !notificationPermission.granted &&
            usageDayTracker.distinctDaysCount() >= 2
        ) {
            notificationOptInTracker.hasShownContextualAsk = true
            showNotificationAsk = true
        }
    }

    // The Play review prompt is requested only on navigation to the Tuner/Settings tabs
    // (see switchTab above): ad-free surfaces, guarded against recent ads, so a
    // rating sheet can never stack with an interstitial. Eligibility (loyalty >= 3 days,
    // once per day) lives inside AppReviewManager.

    // Notify the user when they earn the daily-maximum Beats reward.
    LaunchedEffect(metronomeVm) {
        metronomeVm.rewardGranted.collect {
            android.widget.Toast.makeText(
                context,
                "🎉 ${com.example.metrognome.points.PointsConfig.CURRENCY_NAME} maxed! Ad-free for ${com.example.metrognome.points.rewards.RewardConfig.AD_FREE_DAYS} days - you've earned it.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    CompositionLocalProvider(LocalHaptics provides hapticEngine) {
    Box(modifier = Modifier.fillMaxSize()) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            visibleTabs.forEach { tab ->
                item(
                    icon = {
                        when (tab) {
                            AppTab.GNOME    -> Icon(Icons.Filled.MusicNote, contentDescription = null)
                            AppTab.TUNER    -> TunerNeedleIcon()
                            AppTab.RHYTHM   -> RhythmPulseIcon()
                            AppTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
                        }
                    },
                    label = { Text(tab.label) },
                    selected = tab == currentTab,
                    onClick = { switchTab(tab) }
                )
            }
        }
    ) {
        when (currentTab) {
            AppTab.GNOME -> MetronomeScreen(
                vm = metronomeVm,
                trainerVm = speedTrainerVm,
                onBeforePracticeResultDismiss = { onDone ->
                    if (isAdFree) onDone()
                    else {
                        val sessionS = (metronomeVm.pendingPracticeResult.value?.durationMinutes ?: 0) * 60
                        activity?.let { adManager.maybeShow(AdPlacement.PRACTICE_COMPLETE, it, false, sessionS, onDone) } ?: onDone()
                    }
                },
                onBeforeTrainerResultDismiss = { onDone ->
                    if (isAdFree) onDone()
                    else {
                        val sessionS = speedTrainerVm.lastSessionDurationSeconds
                        activity?.let { adManager.maybeShow(AdPlacement.SPEED_TRAINER_RESULT, it, false, sessionS, onDone) } ?: onDone()
                    }
                },
                onBeforeManualStop = {
                    if (!isAdFree) {
                        val sessionS = metronomeVm.lastSessionDurationSeconds
                        activity?.let { adManager.maybeShow(AdPlacement.METRONOME_STOP, it, false, sessionS) {} }
                    }
                },
                onWatchRewardedAd = { onDone ->
                    activity?.let { metronomeVm.rewardedAdManager.show(it, onDone) } ?: onDone()
                },
            )
            AppTab.RHYTHM -> RhythmGameScreen(
                vm = rhythmVm,
                metronomeVm = metronomeVm,
                isMetronomePlaying = isPlaying,
                onStopMetronome = { metronomeVm.stopPlayback() },
                isAdFree = isAdFree,
                onBeforeResultDismiss = { onDone ->
                    if (isAdFree) onDone()
                    else activity?.let { adManager.maybeShow(AdPlacement.RHYTHM_RESULT, it, false, 0, onDone) } ?: onDone()
                },
                onWatchRewardedAd = { onDone ->
                    activity?.let { metronomeVm.rewardedAdManager.show(it, onDone) } ?: onDone()
                },
            )

            AppTab.TUNER -> TunerScreen(
                vm = tunerVm,
                keepScreenOn = metronomeVm.keepScreenOn.collectAsState().value,
                onSetKeepScreenOn = metronomeVm::setKeepScreenOn,
                isAdFree = isAdFree,
                store = purchaseStore,
                onPurchase = { productId ->
                    activity?.let { metronomeVm.purchaseSound(it, productId) }
                },
                onRestore = metronomeVm::restorePurchases,
            )

            AppTab.SETTINGS -> SettingsScreen(
                vm = metronomeVm,
                onTriggerFeedback = tunerVm::debugTriggerFeedback,
                onSimulateTuner = tunerVm::debugCycleSimulatedReading,
                onStopTunerSimulation = tunerVm::debugStopSimulation,
                notificationPermission = notificationPermission,
            )
        }
    }
    PointsEarnedBanner(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
    LoyaltyMilestoneBanner(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
    AdBreakBanner(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())

    if (showNotificationAsk) {
        NotificationOptInDialog(
            onEnable = {
                showNotificationAsk = false
                notificationPermission.request()
            },
            onDismiss = { showNotificationAsk = false },
        )
    }
    } // end Box
    } // end CompositionLocalProvider
}
