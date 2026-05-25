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
import com.example.metrognome.ui.components.TunerNeedleIcon
import com.example.metrognome.ui.screens.MetronomeScreen
import com.example.metrognome.ui.screens.RhythmGameScreen
import com.example.metrognome.ui.screens.SettingsScreen
import com.example.metrognome.ui.screens.TunerScreen
import com.example.metrognome.ui.theme.MetroGnomeTheme
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.viewmodel.RhythmGameViewModel
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
            AppTab.GNOME -> MetronomeScreen(vm = metronomeVm)
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
}
