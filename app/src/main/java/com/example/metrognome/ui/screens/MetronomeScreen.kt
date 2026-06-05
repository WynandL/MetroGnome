package com.example.metrognome.ui.screens

import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import com.example.metrognome.ui.overlays.PracticeCompleteOverlay
import com.example.metrognome.ui.dialogs.PresetDeleteDialog
import com.example.metrognome.ui.dialogs.SavePresetDialog
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.ui.overlays.WhatsNewOverlayDispatcher
import com.example.metrognome.ui.components.metro_items.MetroItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.metrognome.ui.components.rememberMicPermissionState
import com.example.metrognome.ui.components.SpeedTrainerCountdownHud
import com.example.metrognome.ui.components.SpeedTrainerHud
import com.example.metrognome.ui.dialogs.AppDialog
import com.example.metrognome.ui.dialogs.SpeedTrainerDialog
import com.example.metrognome.ui.overlays.SpeedTrainerResultOverlay
import com.example.metrognome.viewmodel.SpeedTrainerViewModel
import com.example.metrognome.viewmodel.TrainerSessionState
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.metrognome.presets.BpmPreset
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.dialogs.DialogCloseButton
import com.example.metrognome.ui.components.CollapsibleStreakCard
import com.example.metrognome.ui.components.GnomeCanvas
import com.example.metrognome.ui.components.PresetChipsRow
import com.example.metrognome.debug.mic.MicDiagnosticsOverlay
import com.example.metrognome.dev.DevEasterEgg
import androidx.compose.ui.platform.LocalContext
import com.example.metrognome.debug.mic.MicDiagnosticsTrigger
import com.example.metrognome.ui.dialogs.FeatureEnableDialog
import com.example.metrognome.ui.dialogs.ShowcaseFrame
import com.example.metrognome.ui.dialogs.SpeedTrainerRampPreview
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MetronomeScreen(
    vm: MetronomeViewModel,
    trainerVm: SpeedTrainerViewModel,
    onBeforePracticeResultDismiss: (onDone: () -> Unit) -> Unit = { it() },
    onBeforeTrainerResultDismiss: (onDone: () -> Unit) -> Unit = { it() },
) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val currentBeat by vm.currentBeat.collectAsStateWithLifecycle()
    val accentBeat by vm.accentBeat.collectAsStateWithLifecycle()
    val activeItemIds by vm.activeItemIds.collectAsStateWithLifecycle()
    val activeItems = androidx.compose.runtime.remember(activeItemIds) {
        METRO_ITEM_REGISTRY.filter { it.item.id in activeItemIds }.map { it.item }
    }
    val isMuted by vm.isMuted.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val isAdFree by vm.isAdFree.collectAsStateWithLifecycle()
    val isPresetsEnabled by vm.isPresetsEnabled.collectAsStateWithLifecycle()
    val isPracticeEnabled by vm.isPracticeEnabled.collectAsStateWithLifecycle()
    val isSpeedTrainerEnabled by vm.isSpeedTrainerEnabled.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val isPracticeActive by vm.isPracticeActive.collectAsStateWithLifecycle()
    val practiceSecondsRemaining by vm.practiceSecondsRemaining.collectAsStateWithLifecycle()
    val practiceGoalSeconds by vm.practiceGoalSeconds.collectAsStateWithLifecycle()
    val practiceStreak by vm.practiceStreak.collectAsStateWithLifecycle()
    val bestStreak by vm.bestStreak.collectAsStateWithLifecycle()
    val practicedEpochDays by vm.practicedEpochDays.collectAsStateWithLifecycle()
    val streakCardExpanded by vm.streakCardExpanded.collectAsStateWithLifecycle()
    val pendingPracticeResult by vm.pendingPracticeResult.collectAsStateWithLifecycle()

    val gnoteCount by vm.gnoteCount.collectAsStateWithLifecycle()
    var showGnotesInfo by remember { mutableStateOf(false) }

    var tappedItem by remember { mutableStateOf<MetroItem?>(null) }
    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()
    val pendingWhatsNew by vm.pendingWhatsNew.collectAsStateWithLifecycle()

    var tapHintShown by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showEnablePresetsDialog by remember { mutableStateOf(false) }
    var showEnablePracticeDialog by remember { mutableStateOf(false) }
    var showEnableTrainerDialog by remember { mutableStateOf(false) }
    var presetPendingDelete by remember { mutableStateOf<Pair<Int, BpmPreset>?>(null) }
    val presetLongPressHintSeen by vm.presetLongPressHintSeen.collectAsStateWithLifecycle()
    var showPracticeDialog by remember { mutableStateOf(false) }

    val activity = LocalActivity.current

    // ── Speed Trainer ──────────────────────────────────────────────────────────
    val trainerConfig by trainerVm.config.collectAsStateWithLifecycle()
    val trainerState by trainerVm.sessionState.collectAsStateWithLifecycle()
    var showTrainerDialog by remember { mutableStateOf(false) }
    var showCancelTrainerDialog  by remember { mutableStateOf(false) }
    var showCancelPracticeDialog by remember { mutableStateOf(false) }
    var showMicDiagnostics by remember { mutableStateOf(false) }

    val mic = rememberMicPermissionState(onGranted = { trainerVm.updateConfig { copy(micEnabled = true) } })

    // Forward BPM requests from trainer to the metronome engine
    LaunchedEffect(trainerVm) {
        trainerVm.bpmRequest.collectLatest { bpm ->
            vm.setBpm(bpm)
            if (!vm.isPlaying.value) vm.togglePlay()
        }
    }

    // Forward beat events to the trainer for bar counting
    LaunchedEffect(trainerVm) {
        vm.beatEvents.collect { event ->
            trainerVm.onBeat(event.beat)
        }
    }

    // Stop metronome and check for newly-earned items when a training session finishes
    LaunchedEffect(trainerState) {
        if (trainerState is TrainerSessionState.Complete) {
            vm.stopPlayback()
            vm.checkForNewUnlocks()
        }
    }

    // Stop metronome when a practice session completes
    LaunchedEffect(pendingPracticeResult) {
        if (pendingPracticeResult != null) vm.stopPlayback()
    }

    LaunchedEffect(Unit) {
        vm.checkForNewUnlocks()
    }

    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        BeatIndicatorRow(
            timeSig = timeSig,
            currentBeat = currentBeat,
            isPlaying = isPlaying,
            accentBeat = accentBeat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GnomeCanvas(
                bpm = bpm,
                isPlaying = isPlaying,
                beatEvents = vm.beatEvents,
                flashOnBeat = flashOnBeat,
                accentBeat = accentBeat,
                activeItems = activeItems,
                onItemTapped = { tappedItem = it },
                modifier = Modifier.fillMaxSize()
            )

            BpmDisplay(
                bpm = bpm,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )

            if (gnoteCount > 0) {
                com.example.metrognome.ui.components.GoldPill(
                    text        = "$gnoteCount ${com.example.metrognome.points.PointsConfig.CURRENCY_NAME}",
                    leadingIcon = Icons.Filled.Bolt,
                    onClick     = { showGnotesInfo = true },
                    modifier    = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp),
                )
            }
        }

        BpmStepperRow(
            bpm = bpm,
            isPlaying = isPlaying,
            onBpmChange = { vm.setBpm(it) },
            onTogglePlay = { vm.togglePlay() },
            onTapTempo = {
                if (!tapHintShown) {
                    tapHintShown = true
                    Toast.makeText(activity, "Tap again to set the tempo", Toast.LENGTH_SHORT).show()
                }
                vm.tapTempo()
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.background)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        SecondaryControlsRow(
            isMuted = isMuted,
            keepScreenOn = keepScreenOn,
            isOnSavedPreset = isPresetsEnabled && presets.any { it.bpm == bpm },
            isPracticeActive = isPracticeActive,
            isTrainerActive = trainerState is TrainerSessionState.Running || trainerState is TrainerSessionState.Countdown,
            onSavePreset = {
                if (isPresetsEnabled) showSavePresetDialog = true
                else showEnablePresetsDialog = true
            },
            onToggleMute = {
                vm.toggleMute()
                val msg = if (isMuted) "Sound on" else "Muted"
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            },
            onToggleScreenOn = {
                val enabling = !keepScreenOn
                vm.setKeepScreenOn(enabling)
                val msg = if (enabling) "Screen will stay on" else "Screen timeout on"
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            },
            onPractice = {
                val trainerBusy = trainerState is TrainerSessionState.Running ||
                                  trainerState is TrainerSessionState.Countdown
                if (!isPracticeActive) {
                    when {
                        trainerBusy  -> Toast.makeText(activity, "Stop the Speed Trainer first", Toast.LENGTH_SHORT).show()
                        isPracticeEnabled -> showPracticeDialog = true
                        else         -> showEnablePracticeDialog = true
                    }
                }
            },
            onTrainer = {
                val trainerIdle = trainerState !is TrainerSessionState.Running &&
                                  trainerState !is TrainerSessionState.Countdown
                if (trainerIdle) {
                    when {
                        isPracticeActive      -> Toast.makeText(activity, "Stop the Practice session first", Toast.LENGTH_SHORT).show()
                        isSpeedTrainerEnabled -> showTrainerDialog = true
                        else                  -> showEnableTrainerDialog = true
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.background)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        val trainerCounting = trainerState as? TrainerSessionState.Countdown
        val trainerRunning = trainerState as? TrainerSessionState.Running
        if (trainerCounting != null || trainerRunning != null) {
            // Fixed-height container so the countdown and session HUDs occupy
            // identical vertical space — no layout jump on transition.
            // heightIn(min) lets it grow with large-font accessibility settings.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .heightIn(min = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (trainerCounting != null) {
                    SpeedTrainerCountdownHud(
                        state = trainerCounting,
                        onCancel = { showCancelTrainerDialog = true },
                    )
                } else if (trainerRunning != null) {
                    SpeedTrainerHud(
                        state = trainerRunning,
                        onSkip = { trainerVm.skipStep() },
                        onRetreat = { trainerVm.retreatStep() },
                        onCancel = { showCancelTrainerDialog = true },
                    )
                }
            }
        } else if (isPracticeActive) {
            PracticeProgressRow(
                practiceSecondsRemaining = practiceSecondsRemaining,
                practiceGoalSeconds = practiceGoalSeconds,
                onCancelPractice = { showCancelPracticeDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
            )
        } else if (isPracticeEnabled || (isSpeedTrainerEnabled && practiceStreak > 0)) {
            CollapsibleStreakCard(
                streak             = practiceStreak,
                bestStreak         = bestStreak,
                practicedEpochDays = practicedEpochDays,
                expanded           = streakCardExpanded,
                onToggle           = { vm.toggleStreakCard() },
                modifier           = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
        }

        if (isPresetsEnabled && presets.isNotEmpty()) {
            PresetChipsRow(
                presets = presets,
                currentBpm = bpm,
                showLongPressHint = !presetLongPressHintSeen,
                onPresetTap = { preset -> vm.selectPreset(preset) },
                onPresetLongPress = { index, preset ->
                    vm.markPresetLongPressHintSeen()
                    presetPendingDelete = Pair(index, preset)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
            )
        }

        if (!isAdFree) {
            AdBannerView(modifier = Modifier.fillMaxWidth())
        }
    }

    tappedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { tappedItem = null },
            title = {
                Text(
                    item.displayName,
                    color = AppColors.gold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(item.earnedMessage, color = AppColors.textPrimary)
            },
            confirmButton = {
                TextButton(onClick = { tappedItem = null }) {
                    Text("Nice!", color = AppColors.gold, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AppColors.surface,
            tonalElevation = 0.dp
        )
    }

    if (showCancelTrainerDialog) {
        StopTrainerDialog(
            onConfirm = {
                showCancelTrainerDialog = false
                trainerVm.cancelSession()
                vm.stopPlayback()
            },
            onDismiss = { showCancelTrainerDialog = false },
        )
    }

    if (showCancelPracticeDialog) {
        StopTrainerDialog(
            onConfirm = {
                showCancelPracticeDialog = false
                vm.cancelPractice()
                vm.stopPlayback()
            },
            onDismiss = { showCancelPracticeDialog = false },
        )
    }

    if (showTrainerDialog) {
        SpeedTrainerDialog(
            config = trainerConfig,
            hasMicPermission = mic.isGranted,
            isMicPermanentlyDenied = mic.isPermanentlyDenied,
            onRequestMicPermission = { mic.request() },
            onConfigChange = { trainerVm.updateConfig(it) },
            onBeginTraining = {
                showTrainerDialog = false
                if (trainerConfig.micEnabled && !mic.isGranted) mic.request()
                trainerVm.beginSession(timeSig)
            },
            onDismiss = { showTrainerDialog = false },
        )
    }

    val trainerComplete = trainerState as? TrainerSessionState.Complete
    if (trainerComplete != null) {
        SpeedTrainerResultOverlay(
            state = trainerComplete,
            onDismiss = { onBeforeTrainerResultDismiss { trainerVm.dismissResult() } },
        )
    }

    if (showSavePresetDialog) {
        SavePresetDialog(
            bpm = bpm,
            existingNames = presets.map { it.name }.toSet(),
            onSave = { name ->
                vm.savePreset(name, bpm)
                showSavePresetDialog = false
            },
            onDismiss = { showSavePresetDialog = false }
        )
    }

    if (showEnablePresetsDialog) {
        EnablePresetsDialog(
            onEnable = {
                vm.enablePresets()
                showEnablePresetsDialog = false
                showSavePresetDialog = true
            },
            onDismiss = { showEnablePresetsDialog = false },
        )
    }

    if (showEnablePracticeDialog) {
        EnablePracticeDialog(
            onEnable = {
                vm.enablePractice()
                showEnablePracticeDialog = false
                showPracticeDialog = true
            },
            onDismiss = { showEnablePracticeDialog = false },
        )
    }

    if (showEnableTrainerDialog) {
        EnableSpeedTrainerDialog(
            onEnable = {
                vm.enableSpeedTrainer()
                showEnableTrainerDialog = false
                showTrainerDialog = true
            },
            onDismiss = { showEnableTrainerDialog = false },
        )
    }

    presetPendingDelete?.let { (index, preset) ->
        PresetDeleteDialog(
            preset = preset,
            onConfirmDelete = {
                vm.deletePreset(index)
                presetPendingDelete = null
            },
            onDismiss = { presetPendingDelete = null }
        )
    }

    if (showPracticeDialog) {
        PracticeDurationDialog(
            onStart = { minutes ->
                showPracticeDialog = false
                vm.startPractice(minutes)
            },
            onDismiss = { showPracticeDialog = false }
        )
    }

    pendingPracticeResult?.let { result ->
        PracticeCompleteOverlay(
            result = result,
            onDismiss = { onBeforePracticeResultDismiss { vm.dismissPracticeResult() } }
        )
    } ?: pendingWhatsNew?.let { key ->
        WhatsNewOverlayDispatcher(
            versionKey = key,
            onDismiss = { vm.markWhatsNewShown(key) },
        )
    } ?: unlockQueue.firstOrNull()?.let { entry ->
        UnlockCelebrationOverlay(
            entry = entry,
            onDismiss = { vm.markCelebrated(entry.item.id) },
        )
    }
    // Dev-mode: mic diagnostics trigger + overlay (debug builds + easter egg dev mode)
    if (DevEasterEgg.isDevModeActive(LocalContext.current)) {
        val micSessionActive = trainerConfig.micEnabled &&
            (trainerState is TrainerSessionState.Running || trainerState is TrainerSessionState.Countdown)
        MicDiagnosticsTrigger(
            visible = micSessionActive,
            overlayOpen = showMicDiagnostics,
            onToggle = { showMicDiagnostics = !showMicDiagnostics },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 10.dp),
        )
        if (showMicDiagnostics) {
            MicDiagnosticsOverlay(onDismiss = { showMicDiagnostics = false })
        }
    }

    if (showGnotesInfo) {
        com.example.metrognome.ui.dialogs.GnotesInfoDialog(
            gnoteCount = gnoteCount,
            onDismiss  = { showGnotesInfo = false },
        )
    }

    } // close outer Box
}

// ── Bottom controls ───────────────────────────────────────────────────────────

@Composable
private fun BpmStepperRow(
    bpm: Int,
    isPlaying: Boolean,
    onBpmChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onTapTempo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BpmButton("-5", Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm - 5) }
        BpmButton("−",  Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm - 1) }
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
        BpmButton("+",  Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm + 1) }
        BpmButton("+5", Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm + 5) }
        Surface(
            onClick = onTapTempo,
            shape = RoundedCornerShape(12.dp),
            color = AppColors.primaryPurple,
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("TAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SecondaryControlsRow(
    isMuted: Boolean,
    keepScreenOn: Boolean,
    isOnSavedPreset: Boolean,
    isPracticeActive: Boolean,
    isTrainerActive: Boolean,
    onSavePreset: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleScreenOn: () -> Unit,
    onPractice: () -> Unit,
    onTrainer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactIconChip(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            active = isMuted,
            onClick = onToggleMute,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = if (keepScreenOn) Icons.Filled.LightMode else Icons.Filled.ModeNight,
            contentDescription = "Keep screen on",
            active = keepScreenOn,
            onClick = onToggleScreenOn,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = Icons.Filled.Favorite,
            contentDescription = "Save BPM preset",
            active = isOnSavedPreset,
            accentColor = AppColors.gold,
            onClick = onSavePreset,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = Icons.Filled.Timer,
            contentDescription = "Practice session",
            active = isPracticeActive,
            accentColor = AppColors.primaryPurple,
            onClick = onPractice,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = Icons.Filled.Bolt,
            contentDescription = "Speed Trainer",
            active = isTrainerActive,
            accentColor = AppColors.primaryPurple,
            onClick = onTrainer,
            modifier = Modifier.weight(1f).height(44.dp)
        )
    }
}

@Composable
private fun CompactIconChip(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = AppColors.gold,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(if (active) AppColors.darkPurple else AppColors.surface)
            .border(1.dp, if (active) accentColor else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) accentColor else Color(0x80FFFFFF),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Practice ──────────────────────────────────────────────────────────────────

/**
 * The Pulse Bar — practice timer with mounting urgency. Heart icon beats faster,
 * fill warms from purple → gold → coral, and digits flash red in the final 10s.
 */
@Composable
private fun PracticeProgressRow(
    practiceSecondsRemaining: Int,
    practiceGoalSeconds: Int,
    onCancelPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val total = practiceGoalSeconds.coerceAtLeast(1)
    val elapsed = total - practiceSecondsRemaining
    val progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)

    // Urgency curve: 0 (chill) → 1 (panic). Stays at 0 until past the midpoint,
    // then climbs slowly, then steeply in the final stretch.
    val urgency = when {
        progress < 0.5f  -> 0f
        progress < 0.8f  -> (progress - 0.5f) / 0.3f * 0.45f          // 0 → 0.45
        progress < 0.95f -> 0.45f + (progress - 0.8f) / 0.15f * 0.40f // 0.45 → 0.85
        else             -> 0.85f + (progress - 0.95f) / 0.05f * 0.15f // 0.85 → 1.0
    }.coerceIn(0f, 1f)

    val isCritical = practiceSecondsRemaining in 1..10

    // Heartbeat cadence — slow at start (~1.5s/beat = 40bpm), fast at end (~430ms/beat = ~140bpm)
    val pulsePeriodMs = (1500f - urgency * 1070f).toInt().coerceAtLeast(380)
    val transition = rememberInfiniteTransition(label = "practicePulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulsePeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val beat = heartbeatCurve(pulse)

    // Color zones — purple at calm, gold mid-urgency, coral at the brink.
    val fillStart = if (urgency < 0.5f)
        lerp(AppColors.deepPurple, AppColors.primaryPurple, urgency * 2f)
    else
        lerp(AppColors.primaryPurple, AppColors.practiceTimerAmber, (urgency - 0.5f) * 2f)
    val fillEnd = if (urgency < 0.5f)
        lerp(AppColors.mediumPurple, AppColors.gold, urgency * 2f)
    else
        lerp(AppColors.gold, AppColors.practiceTimerCritical, (urgency - 0.5f) * 2f)
    val edgeColor = lerp(fillEnd, Color.White, 0.45f)

    val animatedBorder by animateColorAsState(
        targetValue = lerp(
            AppColors.primaryPurple.copy(alpha = 0.55f),
            AppColors.practiceTimerCritical.copy(alpha = 0.75f),
            urgency
        ),
        animationSpec = tween(400),
        label = "border"
    )

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(AppColors.surfaceDim)
            .border(1.dp, animatedBorder, shape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Quarter ticks on the empty track
            for (i in 1..3) {
                val x = w * (i / 4f)
                if (x > w * progress) {
                    drawLine(
                        color = AppColors.textDim.copy(alpha = 0.22f),
                        start = Offset(x, h * 0.32f),
                        end = Offset(x, h * 0.68f),
                        strokeWidth = 1f
                    )
                }
            }

            val fillW = w * progress
            if (fillW > 0f) {
                // Main gradient fill
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(fillStart, fillEnd),
                        startX = 0f,
                        endX = w
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillW, h)
                )

                // Glassy top highlight
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.18f),
                        0.55f to Color.Transparent
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillW, h)
                )
                // Bottom shade
                drawRect(
                    brush = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.18f)
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillW, h)
                )

                // Shimmer band sweeping across the fill
                val bandHalf = 36f
                val bandX = shimmer * (fillW + bandHalf * 2) - bandHalf
                if (bandX in -bandHalf..(fillW + bandHalf)) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f + urgency * 0.06f),
                                Color.Transparent
                            ),
                            startX = bandX - bandHalf,
                            endX = bandX + bandHalf
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fillW, h)
                    )
                }

                // Leading wavefront — soft glow trailing back into the fill
                val glowR = 20f + urgency * 28f + beat * 18f
                val glowStart = (fillW - glowR).coerceAtLeast(0f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            edgeColor.copy(alpha = 0f),
                            edgeColor.copy(alpha = 0.30f + urgency * 0.30f + beat * 0.20f)
                        ),
                        startX = glowStart,
                        endX = fillW
                    ),
                    topLeft = Offset(glowStart, 0f),
                    size = Size(fillW - glowStart, h)
                )
                // Bright leading line
                if (fillW < w - 0.5f) {
                    drawLine(
                        color = edgeColor,
                        start = Offset(fillW, h * 0.05f),
                        end = Offset(fillW, h * 0.95f),
                        strokeWidth = 1.8f + beat * 1.6f
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing heart — calm at the start, racing at the end
            val heartScale = 1f + beat * (0.18f + urgency * 0.22f)
            val heartColor = lerp(
                AppColors.gold.copy(alpha = 0.75f),
                AppColors.practiceTimerCritical,
                urgency
            )
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = heartColor,
                modifier = Modifier
                    .size(13.dp)
                    .scale(heartScale)
            )
            Spacer(Modifier.width(9.dp))
            val digitColor = if (isCritical)
                lerp(Color.White, AppColors.practiceTimerCritical, 0.35f + beat * 0.55f)
            else
                Color.White
            Text(
                text = formatPracticeTime(practiceSecondsRemaining),
                color = digitColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel practice",
                tint = AppColors.textMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onCancelPractice)
            )
        }
    }
}

// Lub-dub heartbeat shape over t∈[0,1): sharp systolic peak (~t=0.05–0.18)
// followed by a smaller diastolic bump (~t=0.22–0.36), then rest.
private fun heartbeatCurve(t: Float): Float {
    val tt = t % 1f
    val lub = when {
        tt < 0.08f -> tt / 0.08f
        tt < 0.18f -> 1f - (tt - 0.08f) / 0.10f
        else       -> 0f
    }
    val dub = when {
        tt in 0.22f..0.36f -> {
            val x = (tt - 0.22f) / 0.14f
            (kotlin.math.sin(x * Math.PI).toFloat()) * 0.55f
        }
        else -> 0f
    }
    return (lub + dub).coerceIn(0f, 1f)
}


@Composable
private fun PracticeDurationDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(15) }

    AppDialog(onDismiss = onDismiss) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    DialogCloseButton(onClick = onDismiss)
                }

                Text(
                    text = "Practice Session",
                    color = AppColors.gold,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "$selected min",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )

                Spacer(Modifier.height(8.dp))

                Slider(
                    value = selected.toFloat(),
                    onValueChange = { selected = it.roundToInt() },
                    valueRange = 5f..30f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.gold,
                        activeTrackColor = AppColors.mediumPurple,
                        inactiveTrackColor = AppColors.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Time spent practicing unlocks new gnome items",
                    color = AppColors.gold.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                Surface(
                    onClick = { onStart(selected) },
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.primaryPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "START PRACTICE",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp,
                            )
                        }
                    }
                }
    }
}

private fun formatPracticeTime(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun StopTrainerDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(onDismiss = onDismiss, minWidth = 260.dp, maxWidth = 340.dp) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(AppColors.stopRed.copy(alpha = 0.15f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = AppColors.stopRed,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Stop session?",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Your progress this session will be lost.",
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Keep going", color = AppColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                onClick = onConfirm,
                shape = RoundedCornerShape(14.dp),
                color = AppColors.stopRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AppColors.stopRedBorder),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Stop", color = AppColors.stopRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EnablePresetsDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    FeatureEnableDialog(
        title       = "BPM Presets",
        description = "Stop dialing in the same tempos over and over. Save them once, recall them instantly.",
        onEnable    = onEnable,
        onDismiss   = onDismiss,
        highlights  = listOf(
            "Save up to 10 song tempos",
            "Switch tempo in a single tap",
            "Yours forever, no strings attached",
        ),
        previewContent = {
            ShowcaseFrame(caption = "ONE-TAP TEMPO SWITCHING") {
                PresetChipsRow(
                    presets = remember {
                        listOf(
                            BpmPreset("♩ Ballad", 72),
                            BpmPreset("Verse", 110),
                            BpmPreset("Chorus", 140),
                            BpmPreset("Guitar Solo", 168),
                        )
                    },
                    currentBpm        = 110,
                    showLongPressHint = false,
                    onPresetTap       = {},
                    onPresetLongPress = { _, _ -> },
                )
            }
        },
    )
}

@Composable
private fun EnablePracticeDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    FeatureEnableDialog(
        title       = "Practice Sessions",
        description = "Turn loose noodling into real, measurable progress - one focused session at a time.",
        onEnable    = onEnable,
        onDismiss   = onDismiss,
        highlights  = listOf(
            "Set a daily practice goal",
            "Track your day-by-day streak",
            "Every finished session celebrated",
            "Practice unlocks exclusive gnome items",
        ),
        previewContent = {
            ShowcaseFrame(caption = "YOUR LIVE PRACTICE TIMER") {
                PracticeProgressRow(
                    practiceSecondsRemaining = 150,
                    practiceGoalSeconds      = 600,
                    onCancelPractice         = {},
                    modifier                 = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun EnableSpeedTrainerDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    FeatureEnableDialog(
        title       = "Speed Trainer",
        description = "Systematically build tempo from wherever you are to wherever you want to go.",
        onEnable    = onEnable,
        onDismiss   = onDismiss,
        highlights  = listOf(
            "Ramp any tempo to your target, automatically",
            "Configure step size, bars per step, and repeats",
            "Optional mic-powered accuracy bonus when playing in time",
            "Tracks your improvement session to session",
        ),
        previewContent = {
            ShowcaseFrame(caption = "YOUR TEMPO RAMP") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SpeedTrainerRampPreview(modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("60 BPM", color = AppColors.textMutedBlue, fontSize = 10.sp)
                        Text("120 BPM", color = AppColors.gold, fontSize = 10.sp)
                    }
                }
            }
        },
    )
}

// ── Beat indicator ────────────────────────────────────────────────────────────

@Composable
private fun BeatIndicatorRow(
    timeSig: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    accentBeat: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until timeSig) {
            val isActive = isPlaying && i == currentBeat
            val isAccent = accentBeat > 0 && i == accentBeat - 1

            val dotSize by animateDpAsState(
                targetValue   = if (isActive) 16.dp else 12.dp,
                animationSpec = tween(120),
                label         = "dotSize$i",
            )
            val glowAlpha by animateFloatAsState(
                targetValue   = if (isActive) 1f else 0f,
                animationSpec = tween(150),
                label         = "glow$i",
            )
            val flatColor by animateColorAsState(
                targetValue   = if (isAccent) Color(0x66FFD700) else Color(0x33FFFFFF),
                animationSpec = tween(80),
                label         = "flat$i",
            )

            val glowColor      = if (isAccent) AppColors.gold else AppColors.textAccent
            val gradientColors = if (isAccent)
                listOf(Color(0xFFFDE68A), AppColors.gold)
            else
                listOf(Color(0xFFE0C8F8), AppColors.textAccent)

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .drawBehind {
                        if (glowAlpha > 0f) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = 8.dp.toPx() * 1.4f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowColor.copy(alpha = 0.35f * glowAlpha), Color.Transparent),
                                    center = c,
                                    radius = r,
                                ),
                                radius = r,
                                center = c,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = if (isActive)
                        Modifier
                            .size(dotSize)
                            .background(Brush.radialGradient(gradientColors), CircleShape)
                    else
                        Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(flatColor),
                )
            }

            if (i < timeSig - 1) Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

internal fun tempoLabel(bpm: Int): String = when {
    bpm < 40  -> "Grave"
    bpm < 60  -> "Largo"
    bpm < 66  -> "Larghetto"
    bpm < 76  -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegretto"
    bpm < 176 -> "Allegro"
    bpm < 200 -> "Vivace"
    bpm < 240 -> "Presto"
    else      -> "Prestissimo"
}

@Composable
private fun BpmDisplay(bpm: Int, modifier: Modifier = Modifier) {
    val scalePulse = remember { Animatable(1f) }
    val glowPulse  = remember { Animatable(0f) }

    LaunchedEffect(bpm) {
        launch {
            scalePulse.snapTo(1.065f)
            scalePulse.animateTo(
                1f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
        launch {
            glowPulse.snapTo(1f)
            glowPulse.animateTo(0f, tween(450))
        }
    }

    Surface(
        modifier = modifier.scale(scalePulse.value),
        color = Color(0x99000000),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Text(
                text = bpm.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = lerp(Color.White, AppColors.gold, glowPulse.value),
                textAlign = TextAlign.Center
            )
            Text(
                text = tempoLabel(bpm),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.gold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-6).dp).padding(bottom = 2.dp)
            )
        }
    }
}


@Composable
private fun BpmButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) AppColors.danger else AppColors.primaryPurple,
        label = "playButtonColor"
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF1A1040)
@Composable
private fun BpmDisplayPreview() {
    BpmDisplay(bpm = 120)
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1040)
@Composable
private fun BeatIndicatorRowPreview() {
    BeatIndicatorRow(timeSig = 4, currentBeat = 1, isPlaying = true, accentBeat = 1)
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1040)
@Composable
private fun PlayPauseButtonsPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlayPauseButton(isPlaying = false, onClick = {})
        PlayPauseButton(isPlaying = true, onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1040, widthDp = 360)
@Composable
private fun BpmStepperRowPreview() {
    BpmStepperRow(bpm = 120, isPlaying = false, onBpmChange = {}, onTogglePlay = {}, onTapTempo = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1040, widthDp = 360)
@Composable
private fun SecondaryControlsRowPreview() {
    SecondaryControlsRow(
        isMuted = false,
        keepScreenOn = true,
        isOnSavedPreset = false,
        isPracticeActive = false,
        isTrainerActive = false,
        onSavePreset = {},
        onToggleMute = {},
        onToggleScreenOn = {},
        onPractice = {},
        onTrainer = {},
    )
}

