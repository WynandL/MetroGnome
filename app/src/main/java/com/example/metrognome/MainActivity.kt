package com.example.metrognome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
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
import com.example.metrognome.ads.InterstitialAdManager
import com.example.metrognome.review.AppReviewManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.metrognome.ui.components.LoyaltyMilestoneBanner
import com.example.metrognome.ui.components.PointsEarnedBanner
import com.example.metrognome.ui.components.TunerNeedleIcon
import com.example.metrognome.ui.screens.MetronomeScreen
import com.example.metrognome.ui.screens.RhythmGameScreen
import com.example.metrognome.ui.screens.SettingsScreen
import com.example.metrognome.ui.screens.TunerScreen
import com.example.metrognome.ui.theme.MetroGnomeTheme
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
    val interstitialManager = remember { InterstitialAdManager(context).also { it.preload() } }
    val reviewManager = remember { AppReviewManager(context) }

    // Re-query Play Store for owned purchases every time the app returns to foreground.
    // This handles: switching devices, purchasing on one device then opening another,
    // and any case where the local cache drifts from Play's source of truth.
    // Also count this as a distinct day opened (review eligibility), but do NOT
    // prompt here: a rating sheet on cold resume is jarring and depresses ratings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        metronomeVm.reconcilePurchases()
        metronomeVm.refreshReward()
        metronomeVm.recordUsageDay()
        reviewManager.recordAppOpen()
    }

    // Ask for the Play Store review at a natural, earned moment: when the user
    // stops the metronome after playing it. The day-3 once-only gate inside
    // AppReviewManager still applies, so this is a no-op until eligible.
    var wasMetronomePlaying by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (wasMetronomePlaying && !isPlaying) {
            activity?.let { reviewManager.maybeRequestReview(it) }
        }
        wasMetronomePlaying = isPlaying
    }

    // Second review trigger: fires when the user has accumulated enough real tuner
    // usage (locked pitch detection time, same counter as item unlocks). Collected
    // from TunerViewModel so we reuse the existing tracking rather than wall-clock
    // tab time, which would count idle screen time rather than actual tuning.
    val tunerReviewReady by tunerVm.tunerReviewReady.collectAsStateWithLifecycle()
    LaunchedEffect(tunerReviewReady) {
        if (tunerReviewReady) activity?.let { reviewManager.maybeRequestReview(it) }
    }

    // Third review trigger: the 30-day loyalty milestone. A user who has opened the
    // app on 30 distinct days is the definition of an established user — the best
    // moment to ask. The banner fires first (a positive moment), then the review prompt
    // appears naturally as a follow-up. Other milestone days are ignored here.
    LaunchedEffect(Unit) {
        com.example.metrognome.points.PointsBannerQueue.milestones.collect { days ->
            if (days == 30) activity?.let { reviewManager.maybeRequestReview(it) }
        }
    }

    // Show one interstitial ad after the 2nd practice session of the day.
    // The overlay has already been dismissed at this point so the ad is the natural break.
    LaunchedEffect(metronomeVm) {
        metronomeVm.practiceAdTrigger.collect {
            activity?.let { act -> interstitialManager.showIfReady(act) {} }
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            visibleTabs.forEach { tab ->
                item(
                    icon = {
                        when (tab) {
                            AppTab.GNOME    -> Icon(Icons.Filled.MusicNote, contentDescription = null)
                            AppTab.TUNER    -> TunerNeedleIcon()
                            AppTab.RHYTHM   -> Icon(Icons.Filled.Stars, contentDescription = null)
                            AppTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
                        }
                    },
                    label = { Text(tab.label) },
                    selected = tab == currentTab,
                    onClick = {
                        if (currentTab == AppTab.RHYTHM && tab != AppTab.RHYTHM) {
                            rhythmVm.stopGame()
                        }
                        currentTab = tab
                    }
                )
            }
        }
    ) {
        when (currentTab) {
            AppTab.GNOME -> MetronomeScreen(vm = metronomeVm, trainerVm = speedTrainerVm)
            AppTab.RHYTHM -> RhythmGameScreen(
                vm = rhythmVm,
                isMetronomePlaying = isPlaying,
                onStopMetronome = { metronomeVm.stopPlayback() },
                isAdFree = isAdFree,
                onBeforeResultDismiss = { onDone ->
                    if (isAdFree) {
                        onDone()
                    } else {
                        activity?.let { interstitialManager.showIfReady(it, onDone) } ?: onDone()
                    }
                }
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
            )
        }
    }
    PointsEarnedBanner(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
    LoyaltyMilestoneBanner(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
    } // end Box
}
