package com.example.metrognome.debug.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.audio.selftest.AudioRouteMonitor
import com.example.metrognome.audio.selftest.MicCalibration
import com.example.metrognome.audio.selftest.SelfTestCalibrationStore
import com.example.metrognome.debug.mic.MicDiagnosticsOverlay
import com.example.metrognome.debug.mic.MicTimingLogOverlay
import com.example.metrognome.debug.profile.ProfileRoundTripOverlay
import com.example.metrognome.debug.tuner.TunerLockLogOverlay
import com.example.metrognome.debug.tuner.TunerReadingLog
import com.example.metrognome.debug.tuner.TunerReadingLogOverlay
import com.example.metrognome.poll.ALL_POLLS
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.ui.components.PollBanner
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.UnlockCondition
import com.example.metrognome.ui.components.metro_items.displayText
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.whatsnew.AppWhatsNew
import androidx.core.content.edit

/**
 * The entire DEV ONLY tooling surface for the Settings screen, extracted into one
 * self-contained, DEBUG/easter-egg-gated component. The caller renders it only when
 * dev mode is active, so the production Settings screen stays free of test code.
 *
 * All dev-local state (preview index, test counters, which dev overlay is open) lives
 * here. Only the two genuinely shared dependencies cross the boundary:
 *  - [micCal]            read for the "Reset (x ms)" label (the production mic-check
 *                        state is owned by the Settings screen).
 *  - [onMicStateChanged] called after a dev force-pass / reset so the screen re-reads
 *                        the mic calibration (it bumps the screen's refresh key).
 *
 * To remove every developer tool: delete this file and its single call site.
 */
@Composable
fun DevToolsSection(
    vm: MetronomeViewModel,
    micCal: MicCalibration,
    onTriggerFeedback: () -> Unit,
    onSimulateTuner: () -> Unit,
    onStopTunerSimulation: () -> Unit,
    onMicStateChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val cheatModeEnabled by vm.cheatModeEnabled.collectAsStateWithLifecycle()
    val purchasedSoundIds by vm.purchasedSoundIds.collectAsStateWithLifecycle()
    val purchasedItemProductIds by vm.purchasedItemProductIds.collectAsStateWithLifecycle()
    val isPresetsEnabled by vm.isPresetsEnabled.collectAsStateWithLifecycle()
    val isPracticeEnabled by vm.isPracticeEnabled.collectAsStateWithLifecycle()
    val isSpeedTrainerEnabled by vm.isSpeedTrainerEnabled.collectAsStateWithLifecycle()
    val practiceStreak by vm.practiceStreak.collectAsStateWithLifecycle()
    val isAdFree by vm.isAdFree.collectAsStateWithLifecycle()

    var previewIndex by remember { mutableIntStateOf(0) }
    var testBannerCount by remember { mutableIntStateOf(1) }
    var showPollPreview by remember { mutableStateOf(false) }
    var pollResetKey by remember { mutableIntStateOf(0) }
    val pollAnswered = remember(pollResetKey) {
        ALL_POLLS.firstOrNull()?.let { poll ->
            context.getSharedPreferences("poll_state", Context.MODE_PRIVATE)
                .getBoolean("answered_${poll.id}", false)
        } ?: false
    }
    var showUnlockRules by remember { mutableStateOf(false) }
    var showAdPolicy by remember { mutableStateOf(false) }
    var showMicSelfTest by remember { mutableStateOf(false) }
    var showMicTimingLog by remember { mutableStateOf(false) }
    var showTunerLockLog by remember { mutableStateOf(false) }
    var showTunerReadingLog by remember { mutableStateOf(false) }
    var showProfileRoundTrip by remember { mutableStateOf(false) }
    var recordReadings by remember { mutableStateOf(TunerReadingLog.recording) }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── DEV ONLY ──────────────────────────────────────────────────────────
        OutlinedButton(
            onClick = { vm.toggleCheatMode() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (cheatModeEnabled) AppColors.gold else AppColors.devGrey
            ),
            border = BorderStroke(
                1.dp,
                if (cheatModeEnabled) AppColors.gold else AppColors.devDarkBorder
            )
        ) {
            Text(
                if (cheatModeEnabled) "All Items ON" else "All Items OFF",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { vm.previewUnlockCelebration(previewIndex) },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.mediumPurple),
                border = BorderStroke(1.dp, AppColors.deepPurple)
            ) {
                Text("Preview Popup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { if (METRO_ITEM_REGISTRY.isNotEmpty()) previewIndex = (previewIndex + 1) % METRO_ITEM_REGISTRY.size },
                modifier = Modifier.padding(start = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                border = BorderStroke(1.dp, AppColors.surfaceVariant)
            ) {
                Text(
                    "#${previewIndex + 1}/${METRO_ITEM_REGISTRY.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { showUnlockRules = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text("Show Unlock Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { showAdPolicy = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text("Show Ad Policy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.resetAllProgress() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text("Reset All Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugClearAdFree() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text(
                if (isAdFree) "Clear Ad-Free State" else "Ad-Free Already Cleared",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugClearSoundPurchases() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text(
                if (purchasedSoundIds.isNotEmpty()) "Clear Sound Purchases" else "No Sound Purchases to Clear",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugClearItemPurchases() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text(
                if (purchasedItemProductIds.isNotEmpty()) "Clear Item Purchases" else "No Item Purchases to Clear",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugClearPresets() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text(
                if (isPresetsEnabled) "Clear Presets + Data" else "Presets Not Enabled",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugClearPracticeMode() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text(
                if (isPracticeEnabled) "Clear Practice + Streak" else "Practice Not Enabled",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isPracticeEnabled) {
            Spacer(Modifier.height(6.dp))
            StreakSimulator(
                currentStreak = practiceStreak,
                onApply       = { vm.debugSimulateStreak(it) },
                onReset       = { vm.debugClearStreakSim() },
            )
        }
        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugClearSpeedTrainer() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
            border = BorderStroke(1.dp, AppColors.devRedBorder)
        ) {
            Text(
                if (isSpeedTrainerEnabled) "Clear Speed Trainer Unlock" else "Speed Trainer Not Enabled",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugResetWhatsNew() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text("Show ${AppWhatsNew.ALL.last()} What's New Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = { vm.debugResetReview() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text("Reset Review Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = onTriggerFeedback,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text("Trigger Tuner Feedback Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = { showPollPreview = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Preview Poll Banner", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    context.getSharedPreferences("poll_state", Context.MODE_PRIVATE)
                        .edit { clear() }
                    pollResetKey++
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (pollAnswered) AppColors.devRed else AppColors.devGrey
                ),
                border = BorderStroke(
                    1.dp,
                    if (pollAnswered) AppColors.devRedBorder else AppColors.devDarkBorder
                )
            ) {
                Text(
                    if (pollAnswered) "Reset Poll (answered)" else "Reset Poll (open)",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
        }

        if (showPollPreview) {
            ALL_POLLS.firstOrNull()?.let { poll ->
                Spacer(Modifier.height(6.dp))
                PollBanner(
                    visible    = true,
                    poll       = poll,
                    onResponse = { /* no Firestore write in dev preview */ },
                    onDismiss  = { showPollPreview = false },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onSimulateTuner,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Simulate Tuner Note (cycles)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onStopTunerSimulation,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = {
                val limit = 3
                PointsBannerQueue.postActivity(
                    "Rhythm Game",
                    1,
                    testBannerCount,
                    limit,
                )
                testBannerCount = if (testBannerCount >= limit + 1) 1 else testBannerCount + 1
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text(
                "Test Gnotes Banner  ($testBannerCount / 3)",
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }

        val milestoneDays = listOf(7, 30, 60, 100, 365)
        var testMilestoneIndex by remember { mutableIntStateOf(0) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                PointsBannerQueue.postMilestone(
                    milestoneDays[testMilestoneIndex]
                )
                testMilestoneIndex = (testMilestoneIndex + 1) % milestoneDays.size
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
            border = BorderStroke(1.dp, AppColors.devBlueBorder)
        ) {
            Text(
                "Test Loyalty Banner  (${milestoneDays[testMilestoneIndex]} days)",
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        // Non-destructive profile capture/restore round-trip: proves every progress field
        // survives a write+read with no loss. Shows a per-field PASS/FAIL diff.
        OutlinedButton(
            onClick = { showProfileRoundTrip = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.gold),
            border = BorderStroke(1.dp, AppColors.gold)
        ) {
            Text("Profile Round-Trip", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }

        Spacer(Modifier.height(6.dp))

        // Mic acoustic-loopback self-test launcher + calibration reset, side by side.
        // The self-test is the single canonical launcher for the engineering report.
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showMicSelfTest = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.gold),
                border = BorderStroke(1.dp, AppColors.gold)
            ) {
                Text("Mic Self-Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    SelfTestCalibrationStore(context).clear()
                    onMicStateChanged()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
                border = BorderStroke(1.dp, AppColors.devRedBorder)
            ) {
                Text(
                    when {
                        micCal.isCalibrated  -> "Reset (${micCal.latencyMs.toInt()} ms)"
                        micCal.isUnsupported -> "Reset (failed)"
                        else                 -> "Reset (not run)"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Force a passing calibration (arbitrary latency bias + mic mode on) so the whole mic
        // system can be exercised on a device/emulator that keeps failing the self-test. Two
        // variants kept separate on purpose:
        //   "real"     - uses the actual mic, so a bad mic / no claps genuinely yields 0 bonus.
        //   "simulate" - also synthesizes plausible timing so the bonus + result UI appear even
        //                with no real input (the bonus is fake; do not read it as a measurement).
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val store = SelfTestCalibrationStore(context)
                    val route = AudioRouteMonitor(context).currentRoute()
                    store.devForcePass((45..95).random().toFloat(), route)
                    store.devSimulateTiming = false
                    onMicStateChanged()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Real mic", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    val store = SelfTestCalibrationStore(context)
                    val route = AudioRouteMonitor(context).currentRoute()
                    store.devForcePass((45..95).random().toFloat(), route)
                    store.devSimulateTiming = true
                    onMicStateChanged()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Simulate", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        Text(
            "Both force a calibration pass and turn mic mode on. \"Real mic\" uses your actual mic, " +
                "so a bad mic earns 0 bonus. \"Simulate\" fakes plausible timing so a bonus appears " +
                "with no real input. A genuine saved calibration is never overwritten.",
            color = AppColors.textMuted,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            modifier = Modifier.padding(top = 5.dp)
        )

        Spacer(Modifier.height(6.dp))

        // Read-only viewer for the last real-mic session's onsets (MicDiagnosticsBuffer).
        // Run a Speed Trainer session with mic on, then open this to see exactly what the
        // mic heard and why a Timing Bonus did or did not pay out.
        OutlinedButton(
            onClick = { showMicTimingLog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.gold),
            border = BorderStroke(1.dp, AppColors.gold)
        ) {
            Text("Mic Timing Log", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }

        Spacer(Modifier.height(6.dp))

        // Tuner noise-robustness diagnostics. The suppression *strength* is now a user-facing
        // control on the Tuner page (Ambient Suppression: Off/Low/High), so it is not duplicated
        // here. These two viewers read the lock quality and the known-truth accuracy run.
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showTunerLockLog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.gold),
                border = BorderStroke(1.dp, AppColors.gold)
            ) {
                Text("Tuner Lock Log", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = { showTunerReadingLog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.gold),
                border = BorderStroke(1.dp, AppColors.gold)
            ) {
                Text("Tuner Reading Log", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Known-truth accuracy test: record every settled reading while the test-tone file
        // plays into the mic, then open the Reading Log (mean¢ per note ≈ calibration bias).
        OutlinedButton(
            onClick = {
                recordReadings = !recordReadings
                if (recordReadings) TunerReadingLog.start() else TunerReadingLog.stop()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (recordReadings) AppColors.devRed else AppColors.devGrey
            ),
            border = BorderStroke(1.dp, if (recordReadings) AppColors.devRed else AppColors.devDarkBorder)
        ) {
            Text(
                if (recordReadings) "● Recording Tuner Readings" else "Record Tuner Readings",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
        }

        if (showMicSelfTest) {
            MicDiagnosticsOverlay(onDismiss = { showMicSelfTest = false })
        }

        if (showMicTimingLog) {
            MicTimingLogOverlay(onDismiss = { showMicTimingLog = false })
        }

        if (showTunerLockLog) {
            TunerLockLogOverlay(onDismiss = { showTunerLockLog = false })
        }

        if (showTunerReadingLog) {
            TunerReadingLogOverlay(onDismiss = { showTunerReadingLog = false })
        }

        if (showProfileRoundTrip) {
            ProfileRoundTripOverlay(onDismiss = { showProfileRoundTrip = false })
        }
    }

    if (showAdPolicy) {
        AlertDialog(
            onDismissRequest = { showAdPolicy = false },
            containerColor   = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor  = AppColors.textSecondary,
            title = { Text("Ad Policy", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = com.example.metrognome.ads.buildAdPolicySummary(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdPolicy = false }) {
                    Text("OK", color = AppColors.textAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showUnlockRules) {
        AlertDialog(
            onDismissRequest = { showUnlockRules = false },
            containerColor = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor = AppColors.textSecondary,
            title = { Text("Unlock Rules", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())) {
                    METRO_ITEM_REGISTRY.sortedBy { entry ->
                        when (val c = entry.condition) {
                            is UnlockCondition.MetronomeSeconds          -> c.required.toDouble()
                            is UnlockCondition.TunerSeconds              -> c.required.toDouble()
                            is UnlockCondition.RhythmGamesCompleted      -> c.required * 300.0
                            is UnlockCondition.DaysSinceFirstLaunch      -> c.required * 86_400.0
                            is UnlockCondition.LoyaltyDays               -> c.required * 86_400.0
                            is UnlockCondition.PracticeSessionsCompleted -> c.required * 1_200.0
                            is UnlockCondition.TunerFeedbackGiven              -> c.required * 60.0
                            is UnlockCondition.SpeedTrainingSessionsCompleted  -> c.required * 900.0
                            UnlockCondition.Always                             -> -1.0
                        }
                    }.forEach { entry ->
                        Text(
                            text = entry.item.displayName,
                            color = AppColors.textAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = entry.condition.displayText(),
                            fontSize = 12.sp,
                            color = AppColors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnlockRules = false }) {
                    Text("OK", color = AppColors.textAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun StreakSimulator(
    currentStreak: Int,
    onApply: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var days by remember(currentStreak) { mutableIntStateOf(currentStreak) }
    val shape = RoundedCornerShape(10.dp)
    val btnPadding = PaddingValues(0.dp)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text       = "Simulate streak",
                color      = AppColors.devGrey,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
            )
            OutlinedButton(
                onClick        = { if (days > 0) days-- },
                modifier       = Modifier.size(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                border         = BorderStroke(1.dp, AppColors.devDarkBorder),
                contentPadding = btnPadding,
            ) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Text(
                text       = "$days",
                color      = AppColors.gold,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.width(30.dp),
                textAlign  = TextAlign.Center,
            )

            OutlinedButton(
                onClick        = { days++ },
                modifier       = Modifier.size(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                border         = BorderStroke(1.dp, AppColors.devDarkBorder),
                contentPadding = btnPadding,
            ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick        = { onApply(days) },
                modifier       = Modifier.height(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border         = BorderStroke(1.dp, AppColors.devBlueBorder),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) { Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick        = onReset,
                modifier       = Modifier.height(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                border         = BorderStroke(1.dp, AppColors.devDarkBorder),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) { Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
