package com.example.metrognome.ui.screens

import android.widget.Toast
import com.example.metrognome.ui.theme.AppRadius
import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.AppIcon
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
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import com.example.metrognome.audio.selftest.SelfTestCalibrationStore
import com.example.metrognome.ui.overlays.MicCheckOverlay
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.cloud.PollReporter
import com.example.metrognome.poll.PollManager
import com.example.metrognome.ui.components.PollBanner
import com.example.metrognome.ui.components.HudIconAction
import com.example.metrognome.ui.overlays.PracticeCompleteOverlay
import com.example.metrognome.ui.dialogs.PresetDeleteDialog
import com.example.metrognome.ui.dialogs.SavePresetDialog
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.ui.overlays.WhatsNewOverlayDispatcher
import com.example.metrognome.ui.components.metro_items.MetroItem
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.metrognome.ui.components.MicTimingNudge
import com.example.metrognome.ui.components.rememberMicPermissionRecovery
import com.example.metrognome.ui.components.SpeedTrainerCountdownHud
import com.example.metrognome.ui.components.SpeedTrainerHud
import com.example.metrognome.ui.dialogs.AppDialog
import com.example.metrognome.ui.dialogs.ConfirmDestructiveDialog
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
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.components.GoldSlider
import com.example.metrognome.ui.dialogs.DialogTitle
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MetronomeScreen(
    vm: MetronomeViewModel,
    trainerVm: SpeedTrainerViewModel,
    onBeforePracticeResultDismiss: (onDone: () -> Unit) -> Unit = { it() },
    onBeforeTrainerResultDismiss: (onDone: () -> Unit) -> Unit = { it() },
    /** Fired when the user manually taps the stop button (not on practice/trainer auto-stop).
     *  Fire-and-forget: nothing waits for this to complete. */
    onBeforeManualStop: () -> Unit = {},
    onWatchRewardedAd: (onDone: () -> Unit) -> Unit = { it() },
) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val currentBeat by vm.currentBeat.collectAsStateWithLifecycle()
    val accentBeats by vm.accentBeats.collectAsStateWithLifecycle()
    val activeItemIds by vm.activeItemIds.collectAsStateWithLifecycle()
    val activeItems = androidx.compose.runtime.remember(activeItemIds) {
        METRO_ITEM_REGISTRY.filter { it.item.id in activeItemIds }.map { it.item }
    }
    val isMuted by vm.isMuted.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val isAdFree by vm.isAdFree.collectAsStateWithLifecycle()
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
    val rewardedAdLoaded by vm.rewardedAdLoaded.collectAsStateWithLifecycle()
    var showGnotesInfo by remember { mutableStateOf(false) }
    var showSoundStage by remember { mutableStateOf(false) }
    val soundType by vm.soundType.collectAsStateWithLifecycle()
    val classicForSession by vm.classicForSession.collectAsStateWithLifecycle()

    var tappedItem by remember { mutableStateOf<MetroItem?>(null) }
    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()
    val pendingWhatsNew by vm.pendingWhatsNew.collectAsStateWithLifecycle()

    var tapHintShown by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetPendingDelete by remember { mutableStateOf<Pair<Int, BpmPreset>?>(null) }
    val presetLongPressHintSeen by vm.presetLongPressHintSeen.collectAsStateWithLifecycle()
    var showPracticeDialog by remember { mutableStateOf(false) }
    // Groove Check launched in-place from the Practice / Speed Trainer setup nudge (onboarding).
    var showMicCheck by remember { mutableStateOf(false) }
    // Bumped when a check resolves so the open setup dialog's nudge re-reads its state in place.
    var micCheckRefresh by remember { mutableIntStateOf(0) }

    val activity = LocalActivity.current
    val context = LocalContext.current

    // ── Speed Trainer ──────────────────────────────────────────────────────────
    val trainerConfig by trainerVm.config.collectAsStateWithLifecycle()
    val trainerState by trainerVm.sessionState.collectAsStateWithLifecycle()
    val practiceRoomNoisy by vm.roomNoisy.collectAsStateWithLifecycle()
    val trainerRoomNoisy by trainerVm.roomNoisy.collectAsStateWithLifecycle()
    var showTrainerDialog by remember { mutableStateOf(false) }
    var showCancelTrainerDialog  by remember { mutableStateOf(false) }
    var showCancelPracticeDialog by remember { mutableStateOf(false) }

    // Mic mode is a single app-wide toggle in Settings; features read the calibration
    // result and run the mic automatically. BUT the toggle can be on (isActive) while the
    // OS permission is actually missing (e.g. an uninstall/reinstall restores the opt-in
    // flag via SharedPreferences backup without restoring the RECORD_AUDIO grant) - so
    // starting Practice/Speed Trainer routes through micRecovery.startWithMicPermissionCheck
    // below, which re-requests permission right at the moment of starting rather than
    // leaving the session to silently run without mic scoring. See MicPermissionRecovery.
    val micRecovery = rememberMicPermissionRecovery()

    // Forward BPM requests from trainer to the metronome engine
    LaunchedEffect(trainerVm) {
        trainerVm.bpmRequest.collectLatest { bpm ->
            // Force the classic click for the mic detector before playback starts (no-op if mic
            // off). stopPlayback on session end/cancel lifts it.
            vm.setMicSoundOverride(true)
            vm.setBpm(bpm)
            if (!vm.isPlaying.value) vm.togglePlay()
        }
    }

    // Forward beat events to the trainer for bar counting
    LaunchedEffect(trainerVm) {
        vm.beatEvents.collect { event ->
            trainerVm.onBeat(event.beat, event.presentedMs)
        }
    }

    // The trainer's session is open from its countdown until it completes or is cancelled, paused
    // or not; the metronome keeps Classic for Groove Check across a pause for that whole span.
    val trainerSessionOpen = trainerState is TrainerSessionState.Running ||
        trainerState is TrainerSessionState.Countdown
    LaunchedEffect(trainerSessionOpen) { vm.setTrainerSessionActive(trainerSessionOpen) }

    // Once per session, a short note under the sound pill says why it now reads Classic; the
    // sound stage's footnote says the same, but only to someone who opens it. A pause and
    // resume inside the session does not bring it back.
    var showClassicHint by remember { mutableStateOf(false) }
    var classicHintShown by remember { mutableStateOf(false) }
    val anySessionOpen = isPracticeActive || trainerSessionOpen
    LaunchedEffect(anySessionOpen) {
        if (!anySessionOpen) { classicHintShown = false; showClassicHint = false }
    }
    LaunchedEffect(classicForSession) {
        if (classicForSession && !classicHintShown) {
            classicHintShown = true
            showClassicHint = true
            kotlinx.coroutines.delay(CLASSIC_HINT_MS)
            showClassicHint = false
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

    // In-app poll. Lives here, on the front door of the app, rather than on the Rhythm
    // tab where it sat from 06-17 to 09-14 and reached only the minority who open the
    // game. Picked once per tab entry after gnoteCount settles; a poll that has been
    // shown out is reported "ignored" exactly once before the next one is considered.
    val pollManager = remember { PollManager(context) }
    val activePoll by vm.activePoll.collectAsStateWithLifecycle()
    val pollDone by vm.pollDone.collectAsStateWithLifecycle()
    LaunchedEffect(gnoteCount) {
        if (!pollDone && activePoll == null && gnoteCount > 0) {
            pollManager.retireIgnored().forEach { id ->
                PollReporter.submit(id, "ignored", gnoteCount)
                AnalyticsTracker.logPollAnswered(id, "ignored")
            }
            vm.setActivePoll(pollManager.pendingPoll(gnoteCount))
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
            accentBeats = accentBeats,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 4.dp)
        )

        // The two pills share the strip either side of the BPM, never reaching under it: on a
        // 360 dp phone with large fonts the Gnote pill used to overlap the BPM box, and a long
        // sound name ("Crystal Bowl") would have done the same on the right.
        var sceneWidthPx by remember { mutableIntStateOf(0) }
        var bpmWidthPx by remember { mutableIntStateOf(0) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pillMaxWidth = if (sceneWidthPx > 0 && bpmWidthPx > 0) with(density) {
            ((sceneWidthPx - bpmWidthPx) / 2f).toDp() - 12.dp - 6.dp
        }.coerceAtLeast(56.dp) else androidx.compose.ui.unit.Dp.Unspecified
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { sceneWidthPx = it.width }
        ) {
            GnomeCanvas(
                bpm = bpm,
                isPlaying = isPlaying,
                beatEvents = vm.beatEvents,
                flashOnBeat = flashOnBeat,
                accentBeats = accentBeats,
                activeItems = activeItems,
                onItemTapped = { tappedItem = it },
                // Fireworks on a very accurate clap, from whichever mic session is live (only one
                // is ever active). Practice -> vm; Speed Trainer -> trainerVm.
                greatHitSignal = remember(vm, trainerVm) {
                    kotlinx.coroutines.flow.merge(vm.greatHit, trainerVm.greatHit)
                },
                modifier = Modifier.fillMaxSize()
            )

            BpmDisplay(
                bpm = bpm,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .onSizeChanged { bpmWidthPx = it.width }
            )

            if (gnoteCount > 0) {
                com.example.metrognome.ui.components.GnoteTickFlair(
                    count    = gnoteCount,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp),
                ) {
                    com.example.metrognome.ui.components.GoldPill(
                        // The mark says Gnotes; the word ellipsized the count once it reached
                        // the thousands, even on a wide phone.
                        text        = "$gnoteCount",
                        leadingIcon = AppIcons.Gnote,
                        onClick     = { showGnotesInfo = true },
                        modifier    = Modifier
                            .widthIn(max = pillMaxWidth)
                            .semantics { contentDescription = "$gnoteCount ${com.example.metrognome.points.PointsConfig.CURRENCY_NAME}" },
                    )
                }
            }

            // The click in use, by name and mark, opening the sound stage: the Gnote pill's
            // twin on the right, so it costs Metro no height. Naming the current voice is the
            // invitation ("Classic" says there are others); the sounds used to be reachable
            // only from a Settings card, and 92% of users never changed sound.
            // During a Groove Check session the pill names the click actually sounding
            // (Classic), not the saved voice, which returns when the session ends.
            val sound = com.example.metrognome.billing.clickSoundFor(if (classicForSession) 0 else soundType)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp)
                    .widthIn(max = pillMaxWidth),
                horizontalAlignment = Alignment.End,
            ) {
                com.example.metrognome.ui.components.GoldPill(
                    text        = sound.displayName,
                    leadingIcon = com.example.metrognome.ui.sounds.soundGlyphFor(sound.slug),
                    onClick     = { showSoundStage = true },
                    modifier    = Modifier
                        .semantics { contentDescription = "Click sound: ${sound.displayName}. Browse sounds" },
                )
                SessionClassicHint(
                    visible = showClassicHint,
                    onClick = { showClassicHint = false; showSoundStage = true },
                )
            }
        }

        BpmStepperRow(
            bpm = bpm,
            isPlaying = isPlaying,
            onBpmChange = { vm.setBpm(it) },
            onTogglePlay = {
                if (isPlaying) onBeforeManualStop()
                vm.togglePlay()
            },
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
            isOnSavedPreset = presets.any { it.bpm == bpm },
            isPracticeActive = isPracticeActive,
            isTrainerActive = trainerState is TrainerSessionState.Running || trainerState is TrainerSessionState.Countdown,
            onSavePreset = { showSavePresetDialog = true },
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
                    if (trainerBusy) Toast.makeText(activity, "Stop the Speed Trainer first", Toast.LENGTH_SHORT).show()
                    else showPracticeDialog = true
                }
            },
            onTrainer = {
                val trainerIdle = trainerState !is TrainerSessionState.Running &&
                                  trainerState !is TrainerSessionState.Countdown
                if (trainerIdle) {
                    if (isPracticeActive) Toast.makeText(activity, "Stop the Practice session first", Toast.LENGTH_SHORT).show()
                    else showTrainerDialog = true
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
        } else if (bestStreak > 0 || practicedEpochDays.isNotEmpty()) {
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

        if (presets.isNotEmpty()) {
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
        AppDialog(onDismiss = { tappedItem = null }) {
            DialogTitle(item.displayName)
            Spacer(Modifier.height(8.dp))
            Text(
                item.earnedMessage,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Nice!", { tappedItem = null }, Modifier.fillMaxWidth())
        }
    }

    if (showCancelTrainerDialog) {
        ConfirmDestructiveDialog(
            title        = "Stop session?",
            body         = "Your progress this session will be lost.",
            dismissLabel = "Keep going",
            confirmLabel = "Stop",
            onConfirm = {
                showCancelTrainerDialog = false
                trainerVm.cancelSession()
                vm.stopPlayback()
            },
            onDismiss = { showCancelTrainerDialog = false },
        )
    }

    if (showCancelPracticeDialog) {
        ConfirmDestructiveDialog(
            title        = "Stop session?",
            body         = "Your progress this session will be lost.",
            dismissLabel = "Keep going",
            confirmLabel = "Stop",
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
            timeSig = timeSig,
            onConfigChange = { trainerVm.updateConfig(it) },
            onBeginTraining = {
                micRecovery.startWithMicPermissionCheck {
                    showTrainerDialog = false
                    trainerVm.beginSession(timeSig)
                }
            },
            onDismiss = { showTrainerDialog = false },
            onStartMicCheck = { showMicCheck = true },
            onFixMicPermission = micRecovery.fixPermission,
            micCheckRefresh = micCheckRefresh + micRecovery.refreshKey,
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
            existingNames = presets.map { it.name }.filter { it.isNotBlank() }.toSet(),
            onSave = { name ->
                vm.savePreset(name, bpm)
                showSavePresetDialog = false
            },
            onDismiss = { showSavePresetDialog = false }
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
                micRecovery.startWithMicPermissionCheck {
                    showPracticeDialog = false
                    vm.startPractice(minutes)
                }
            },
            onDismiss = { showPracticeDialog = false },
            onStartMicCheck = { showMicCheck = true },
            onFixMicPermission = micRecovery.fixPermission,
            micCheckRefresh = micCheckRefresh + micRecovery.refreshKey,
        )
    }

    if (showMicCheck) {
        // Same flow Settings runs: a passing calibration enables mic timing; any resolved
        // run credits the Groove Check item unlock and queues its celebration.
        MicCheckOverlay(
            onDismiss = {
                showMicCheck = false
                val store = SelfTestCalibrationStore(context)
                if (store.isCalibrated) store.micModeEnabled = true
                micCheckRefresh++   // reflect newly-active state on the open dialog
            },
            onRunCompleted = {
                vm.itemTracker.recordMicCheckCompleted()
                vm.checkForNewUnlocks()
                micCheckRefresh++   // a resolved run clears the CTA even on a fail
            },
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
    // Mic self-test now lives in Settings (dev tools) as the single canonical launcher.

    if (showSoundStage) {
        com.example.metrognome.ui.sounds.MetronomeSoundStage(
            vm = vm,
            source = "home",
            onClose = { showSoundStage = false },
        )
    }

    if (showGnotesInfo) {
        val pointsSnapshot = remember(gnoteCount) {
            com.example.metrognome.points.PointsManager(context, vm.rewardedAdManager).getSnapshot()
        }
        com.example.metrognome.ui.dialogs.GnotesInfoDialog(
            snapshot          = pointsSnapshot,
            canWatchAdToday   = remember(gnoteCount) { vm.rewardedAdManager.canWatch() },
            adReady           = rewardedAdLoaded,
            remainingToday    = remember(gnoteCount) { vm.rewardedAdManager.remainingToday() },
            onWatchRewardedAd = onWatchRewardedAd,
            onDismiss         = { showGnotesInfo = false },
        )
    }

    // The poll card only appears at a genuine pause: nothing sounding, no session running,
    // and no overlay pending, so it never stacks on a result, a What's New or an unlock.
    // Starting the metronome hides it (nothing recorded); stopping brings it back.
    val poll = activePoll
    if (poll != null) {
        val pollVisible = !pollDone &&
            !isPlaying && !isPracticeActive &&
            trainerState is TrainerSessionState.Idle &&
            pendingPracticeResult == null && pendingWhatsNew == null &&
            unlockQueue.isEmpty() && !showMicCheck
        // One appearance per session. Once the card has been on screen, anything that takes
        // it away (starting the metronome, even mid "Thank you", an overlay, leaving the
        // tab) spends it until the next launch. Before this it came back on every stop and
        // every return to the tab, and a card hidden during the 1.8 s thank-you was rebuilt
        // as a fresh question that could be answered a second time.
        LaunchedEffect(pollVisible) {
            vm.setPollOnScreen(pollVisible)
            if (pollVisible && !vm.pollShowLogged) {
                vm.pollShowLogged = true
                pollManager.recordShown(poll.id)
                AnalyticsTracker.logPollShown(poll.id, pollManager.shownCount(poll.id))
            } else if (!pollVisible && vm.pollShowLogged) {
                vm.markPollDone()
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                vm.setPollOnScreen(false)
                if (vm.pollShowLogged) vm.markPollDone()
            }
        }
        PollBanner(
            visible = pollVisible,
            poll    = poll,
            onResponse = { response ->
                if (!vm.claimPollResponse()) return@PollBanner
                pollManager.recordAnswered(poll.id)
                PollReporter.submit(poll.id, response, gnoteCount)
                AnalyticsTracker.logPollAnswered(poll.id, response)
            },
            onDismiss = { vm.markPollDone() },
            modifier  = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Live room-noise indicator while a mic session runs (Practice or Speed Trainer): a small amber
    // glyph in the top corner, tappable for an explanation. The live complement to the enable-time
    // mic self-test. Purely informational; never affects scoring and never covers the gnome.
    val micSessionActive = isPracticeActive ||
        trainerState is TrainerSessionState.Running ||
        trainerState is TrainerSessionState.Countdown
    com.example.metrognome.ui.components.RoomNoiseIndicator(
        visible = micSessionActive && (practiceRoomNoisy || trainerRoomNoisy),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 8.dp, end = 12.dp),
    )

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
        BpmButton("-5", "Tempo down 5", Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm - 5) }
        BpmButton("−",  "Tempo down 1", Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm - 1) }
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
        BpmButton("+",  "Tempo up 1", Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm + 1) }
        BpmButton("+5", "Tempo up 5", Modifier.weight(1f).height(44.dp)) { onBpmChange(bpm + 5) }
        RaisedControl(
            onClick = onTapTempo,
            shape = AppRadius.Inset,
            tint = AppColors.primaryPurple,
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Text("TAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
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
            icon = if (isMuted) AppIcons.VolumeOff else AppIcons.VolumeUp,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            active = isMuted,
            onClick = onToggleMute,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = if (keepScreenOn) AppIcons.Sun else AppIcons.Moon,
            contentDescription = "Keep screen on",
            active = keepScreenOn,
            onClick = onToggleScreenOn,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = AppIcons.Heart,
            contentDescription = "Save BPM preset",
            active = isOnSavedPreset,
            accentColor = AppColors.gold,
            onClick = onSavePreset,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = AppIcons.Timer,
            contentDescription = "Practice session",
            active = isPracticeActive,
            accentColor = AppColors.primaryPurple,
            onClick = onPractice,
            modifier = Modifier.weight(1f).height(44.dp)
        )
        CompactIconChip(
            icon = AppIcons.Bolt,
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
    icon: AppGlyph,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = AppColors.gold,
) {
    // The same raised key as the BPM row above it: a neutral key at rest, the purple key when
    // on (its icon takes the accent, or white where the accent is itself purple).
    RaisedControl(
        onClick = onClick,
        shape = AppRadius.Inset,
        tint = if (active) AppColors.primaryPurple else AppColors.surfaceVariant,
        modifier = modifier.semantics { stateDescription = if (active) "On" else "Off" },
    ) {
        AppIcon(
            glyph = icon,
            contentDescription = contentDescription,
            tint = when {
                !active -> Color(0x99FFFFFF)
                accentColor == AppColors.primaryPurple -> Color.White
                else -> accentColor
            },
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
    val shape = AppRadius.Chip
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
            .background(AppColors.card)
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
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing heart — calm at the start, racing at the end
            val heartScale = 1f + beat * (0.18f + urgency * 0.22f)
            val heartColor = lerp(
                AppColors.gold.copy(alpha = 0.75f),
                AppColors.practiceTimerCritical,
                urgency
            )
            AppIcon(
                glyph = AppIcons.Heart,
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
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            HudIconAction(
                AppIcons.Close,
                contentDescription = "Cancel practice",
                tint = AppColors.textMuted,
                onClick = onCancelPractice,
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
private fun PracticeDurationDialog(
    onStart: (Int) -> Unit,
    onDismiss: () -> Unit,
    onStartMicCheck: () -> Unit = {},
    onFixMicPermission: () -> Unit = {},
    micCheckRefresh: Int = 0,
) {
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

                GoldSlider(
                    value = selected.toFloat(),
                    onValueChange = { selected = it.roundToInt() },
                    valueRange = 5f..30f,
                    steps = 24,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Time spent practicing unlocks new gnome items",
                    color = AppColors.goldSoft,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                )

                MicTimingNudge(
                    onFixPermission = onFixMicPermission,
                    onStartCheck = onStartMicCheck,
                    refreshKey = micCheckRefresh,
                )

                Spacer(Modifier.height(20.dp))

                // The shared raised purple key, like every other dialog's one action; this was a
                // flat 52 dp copy with its own type (UI audit U12).
                PrimaryButton(
                    "START PRACTICE",
                    { onStart(selected) },
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Timer,
                )
    }
}

private fun formatPracticeTime(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

// ── Beat indicator ────────────────────────────────────────────────────────────

@Composable
private fun BeatIndicatorRow(
    timeSig: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    accentBeats: Set<Int>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until timeSig) {
            val isActive = isPlaying && i == currentBeat
            val isAccent = i in accentBeats

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
                listOf(AppColors.amberLight, AppColors.gold)
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
    bpm < 96  -> "Andante"
    bpm < 110 -> "Moderato"
    bpm < 120 -> "Allegretto"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
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
        shape = AppRadius.Inset
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
                lineHeight = 15.sp,
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
private fun BpmButton(label: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    RaisedControl(
        onClick = onClick,
        shape = AppRadius.Inset,
        tint = MaterialTheme.colorScheme.surfaceVariant,
        // "-5" alone reads aloud as "minus five"; say what it does.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick { onClick(); true }
        }
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) AppColors.danger else AppColors.primaryPurple,
        label = "playButtonColor"
    )
    RaisedControl(
        onClick = onClick,
        shape = CircleShape,
        tint = bgColor,
        modifier = Modifier.size(64.dp)
    ) {
        AppIcon(
            glyph = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
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
    BeatIndicatorRow(timeSig = 4, currentBeat = 1, isPlaying = true, accentBeats = setOf(0))
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

private const val CLASSIC_HINT_MS = 6_000L

/**
 * The one-time note under the Home sound pill as a Groove Check session takes the click over:
 * the pill has just changed to "Classic" by itself, and this says why. Tapping it opens the
 * sound stage, whose footnote carries the full sentence.
 */
@Composable
private fun SessionClassicHint(visible: Boolean, onClick: () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
    ) {
        com.example.metrognome.ui.components.AppInset(
            highlighted = true,
            onClick = onClick,
            shape = AppRadius.Chip,
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics { contentDescription = "Classic click while Groove Check listens. Learn more" },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AppIcon(
                    AppIcons.Mic, contentDescription = null,
                    tint = AppColors.goldSoft, modifier = Modifier.size(11.dp),
                )
                Text(
                    text = "For Groove Check",
                    color = AppColors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
