package com.example.metrognome

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
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.example.metrognome.ui.dialogs.NotificationOptInDialog
import com.example.metrognome.ui.screens.MetronomeScreen
import com.example.metrognome.ui.screens.RhythmGameScreen
import com.example.metrognome.ui.screens.SettingsScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Route the hardware volume keys to the media stream so they adjust the click /
        // mic-check playback the user actually hears (not the ring stream), even when
        // nothing is playing yet - e.g. while the mic-check dialog nudges "turn it up".
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            MetroGnomeTheme {
                MetroGnomeApp()
            }
        }
    }
}

@Composable
fun MetroGnomeApp() {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.GNOME) }

    val metronomeVm: MetronomeViewModel = viewModel()
    val rhythmVm: RhythmGameViewModel = viewModel()
    val tunerVm: TunerViewModel = viewModel()
    val speedTrainerVm: SpeedTrainerViewModel = viewModel()
    val isAdFree by metronomeVm.isAdFree.collectAsStateWithLifecycle()
    val isPlaying by metronomeVm.isPlaying.collectAsStateWithLifecycle()

    val visibleTabs = AppTab.entries

    val context = LocalContext.current
    val activity = LocalActivity.current
    val adManager     = remember { AdManager(context).also { it.preload() } }
    val reviewManager = remember { AppReviewManager(context) }
    val hapticEngine  = remember { HapticEngine(context) }

    LaunchedEffect(Unit) { AnalyticsTracker.updateUserTier(adManager.userTier()) }

    // Re-query Play Store for owned purchases every time the app returns to foreground.
    // This handles: switching devices, purchasing on one device then opening another,
    // and any case where the local cache drifts from Play's source of truth. Also count
    // this as a distinct day opened (drives loyalty, which gates review eligibility).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        metronomeVm.reconcilePurchases()
        metronomeVm.refreshReward()
        metronomeVm.recordUsageDay()
    }

    // The Play review prompt is requested only on navigation to the Tuner/Settings tabs
    // (see the tab onClick below): ad-free surfaces, guarded against recent ads, so a
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

    // The one strategic notification-permission ask: fired from the first item-unlock
    // celebration on ANY tab (Gnome, Rhythm, Settings all funnel through the same
    // MetroItemTracker.markCelebrated -> celebrationDismissed signal). Shown at most
    // once ever - see NotificationOptInTracker and NotificationOptInDialog.
    val notificationPermission = rememberNotificationPermissionState()
    val notificationOptInTracker = remember { NotificationOptInTracker(context) }
    var showNotificationAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        MetroItemTracker.celebrationDismissed.collect {
            if (!notificationOptInTracker.hasShownContextualAsk && !notificationPermission.granted) {
                notificationOptInTracker.hasShownContextualAsk = true
                showNotificationAsk = true
            }
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
                    onClick = {
                        if (currentTab == AppTab.RHYTHM && tab != AppTab.RHYTHM) {
                            rhythmVm.stopGame()
                        }
                        // Ask for a review when the user lands on a calm, ad-free tab
                        // (Tuner/Settings) from elsewhere, never if an ad showed recently.
                        val landingOnCalmTab =
                            tab != currentTab && (tab == AppTab.TUNER || tab == AppTab.SETTINGS)
                        currentTab = tab
                        if (landingOnCalmTab && !adManager.recentlyShowedAd()) {
                            activity?.let { reviewManager.maybeRequestReview(it) }
                        }
                    }
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
