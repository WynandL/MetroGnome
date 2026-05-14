package com.example.metrognome

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metrognome.ui.screens.MetronomeScreen
import com.example.metrognome.ui.screens.RhythmGameScreen
import com.example.metrognome.ui.screens.SettingsScreen
import com.example.metrognome.ui.theme.MetroGnomeTheme
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.viewmodel.RhythmGameViewModel
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.metrognome.ads.InterstitialAdManager

enum class AppTab(val label: String, val icon: ImageVector) {
    GNOME("Gnome", Icons.Filled.MusicNote),
    RHYTHM("Rhythm", Icons.Filled.Stars),
    SETTINGS("Settings", Icons.Filled.Settings),
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
    val isAdFree by metronomeVm.isAdFree.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = LocalActivity.current
    val interstitialManager = remember { InterstitialAdManager(context).also { it.preload() } }

    // Re-query Play Store for owned purchases every time the app returns to foreground.
    // This handles: switching devices, purchasing on one device then opening another,
    // and any case where the local cache drifts from Play's source of truth.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        metronomeVm.reconcilePurchases()
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppTab.entries.forEach { tab ->
                item(
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
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
                isMetronomePlaying = metronomeVm.isPlaying.collectAsState().value,
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

            AppTab.SETTINGS -> SettingsScreen(vm = metronomeVm)
        }
    }
}
