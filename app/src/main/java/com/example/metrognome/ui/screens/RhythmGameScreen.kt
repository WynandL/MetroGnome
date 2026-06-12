package com.example.metrognome.ui.screens

import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.points.PointsManager
import com.example.metrognome.points.PointsSnapshot
import com.example.metrognome.points.UsageDayTracker
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.MicTimingNudge
import com.example.metrognome.ui.components.LOYALTY_MILESTONES
import com.example.metrognome.ui.components.LoyaltyMilestonePath
import com.example.metrognome.ui.components.StreakWeekCard
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.dialogs.EarnRulesDialog
import com.example.metrognome.ui.dialogs.ItemCatalogDialog
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.viewmodel.GamePhase
import com.example.metrognome.viewmodel.HitQuality
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.viewmodel.NoteState
import com.example.metrognome.viewmodel.RenderNote
import com.example.metrognome.viewmodel.RhythmGameViewModel
import com.example.metrognome.viewmodel.rhythmStars
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

// ── Difficulty definitions ─────────────────────────────────────────────────────

private data class Difficulty(val name: String, val short: String, val bpm: Int, val beats: Int, val desc: String)

private val difficulties = listOf(
    Difficulty("Beginner", "BEG",  60,  16, "60 BPM · 16 beats · slow & steady"),
    Difficulty("Easy",     "EASY", 80,  24, "80 BPM · 24 beats · getting into the groove"),
    Difficulty("Medium",   "MED",  100, 32, "100 BPM · 32 beats · the classic challenge"),
    Difficulty("Hard",     "HARD", 130, 32, "130 BPM · 32 beats · quick reflexes needed"),
    Difficulty("Expert",   "EXP",  160, 48, "160 BPM · 48 beats · for seasoned rhythmists"),
)

// ── Root screen ────────────────────────────────────────────────────────────────

@Composable
fun RhythmGameScreen(
    vm: RhythmGameViewModel,
    metronomeVm: MetronomeViewModel,
    isMetronomePlaying: Boolean = false,
    onStopMetronome: () -> Unit = {},
    isAdFree: Boolean = false,
    onBeforeResultDismiss: (() -> Unit) -> Unit = { it() },
    onWatchRewardedAd: (onDone: () -> Unit) -> Unit = { it() },
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val score by vm.score.collectAsStateWithLifecycle()
    val combo by vm.combo.collectAsStateWithLifecycle()
    val countDown by vm.countDown.collectAsStateWithLifecycle()
    val currentBeat by vm.currentBeat.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val lastQuality by vm.lastQuality.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val lastHitOffset by vm.lastHitOffset.collectAsStateWithLifecycle()
    val beatsRemaining by vm.beatsRemaining.collectAsStateWithLifecycle()
    val highScores by vm.highScores.collectAsStateWithLifecycle()
    val visibleNotes by vm.visibleNotes.collectAsStateWithLifecycle()

    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()

    // Purge stale queue entries and pick up day-based unlocks on every tab entry
    LaunchedEffect(Unit) {
        vm.checkForNewUnlocks()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier
        .fillMaxSize()
        .background(AppColors.background)
        .statusBarsPadding()) {
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            when (phase) {
                GamePhase.IDLE -> RhythmDashboard(
                    rhythmVm = vm,
                    metronomeVm = metronomeVm,
                    isMetronomePlaying = isMetronomePlaying,
                    onStopMetronome = onStopMetronome,
                    onWatchRewardedAd = onWatchRewardedAd,
                    highScores = highScores,
                )

                GamePhase.COUNTDOWN -> CountdownPanel(countDown)
                GamePhase.PLAYING -> PlayingPanel(
                    vm = vm,
                    score = score,
                    combo = combo,
                    currentBeat = currentBeat,
                    timeSig = timeSig,
                    lastQuality = lastQuality,
                    lastHitOffset = lastHitOffset,
                    beatsRemaining = beatsRemaining,
                    visibleNotes = visibleNotes
                )

                GamePhase.RESULT -> ResultPanel(
                    result = result,
                    // Restart at the current difficulty (reset() keeps bpm/beats/name) through the
                    // same ad gate as dismissing.
                    onPlayAgain = { onBeforeResultDismiss { vm.startGame() } },
                    onDismiss = { onBeforeResultDismiss { vm.dismissResult() } },
                )
            }
        }
        if (!isAdFree) {
            AdBannerView(modifier = Modifier.fillMaxWidth())
        }
    }

    unlockQueue.firstOrNull()?.let { entry ->
        UnlockCelebrationOverlay(
            entry = entry,
            onDismiss = { vm.markCelebrated(entry.item.id) },
        )
    }

    // Mic self-test now lives in Settings (dev tools) as the single canonical launcher.

    } // close outer Box
}

// ── Rhythm dashboard — stats + game card ──────────────────────────────────────

@Composable
private fun RhythmDashboard(
    rhythmVm: RhythmGameViewModel,
    metronomeVm: MetronomeViewModel,
    isMetronomePlaying: Boolean,
    onStopMetronome: () -> Unit,
    onWatchRewardedAd: (onDone: () -> Unit) -> Unit,
    highScores: Map<String, Int> = emptyMap(),
) {
    val context = LocalContext.current

    val gnoteCount      by metronomeVm.gnoteCount.collectAsStateWithLifecycle()
    val adLoaded        by metronomeVm.rewardedAdLoaded.collectAsStateWithLifecycle()
    val practiceStreak  by metronomeVm.practiceStreak.collectAsStateWithLifecycle()
    val bestStreak      by metronomeVm.bestStreak.collectAsStateWithLifecycle()
    val practicedEpochDays by metronomeVm.practicedEpochDays.collectAsStateWithLifecycle()
    val activeItemIds   by metronomeVm.activeItemIds.collectAsStateWithLifecycle()

    val pointsSnapshot = remember(gnoteCount) { PointsManager(context, metronomeVm.rewardedAdManager).getSnapshot() }
    val loyaltyDays    = remember { UsageDayTracker(context).distinctDaysCount() }

    var showEarnRules    by remember { mutableStateOf(false) }
    var showWatchAdDialog by remember { mutableStateOf(false) }
    var showItemCatalog  by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        PointsCard(
            snapshot       = pointsSnapshot,
            onInfoClick    = { showEarnRules = true },
            canWatchToday  = remember(gnoteCount) { metronomeVm.rewardedAdManager.canWatch() },
            adReady        = adLoaded,
            remainingToday = remember(gnoteCount) { metronomeVm.rewardedAdManager.remainingToday() },
            onWatchAdClick = { showWatchAdDialog = true },
        )

        Spacer(Modifier.height(6.dp))

        LoyaltyCard(
            currentDays = loyaltyDays,
            modifier    = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(6.dp))

        // Always show the streak on the Rhythm page, even at Day 0 - it reads "Start your streak"
        // and nudges the user to begin one. (The home page keeps the conditional gating so Metro
        // gets maximum space there.)
        PracticeStreakCard(
            streak             = practiceStreak,
            bestStreak         = bestStreak,
            practicedEpochDays = practicedEpochDays,
            modifier           = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))

        CollectionCard(
            activeItemIds = activeItemIds,
            onClick       = { showItemCatalog = true },
        )

        Spacer(Modifier.height(10.dp))

        val dailyTarget = remember(gnoteCount) {
            PointsManager(context, metronomeVm.rewardedAdManager).rhythmDailyTarget()
        }

        GameCard(
            vm = rhythmVm,
            isMetronomePlaying = isMetronomePlaying,
            onStopMetronome = onStopMetronome,
            highScores = highScores,
            dailyEarned = dailyTarget.first,
            dailyCap = dailyTarget.second,
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showEarnRules) {
        EarnRulesDialog(onDismiss = { showEarnRules = false })
    }

    if (showWatchAdDialog) {
        val remaining = metronomeVm.rewardedAdManager.remainingToday()
        val earn = minOf(PointsConfig.REWARDED_GNOTES_PER_WATCH, remaining)
        AlertDialog(
            onDismissRequest  = { showWatchAdDialog = false },
            containerColor    = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor  = AppColors.textSecondary,
            title = { Text("Metro's Daily Bonus", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    text = "Watch a short clip and Metro rewards you with $earn ${PointsConfig.CURRENCY_NAME}. " +
                           "Three clips per day. Come back tomorrow for more.",
                    fontSize   = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWatchAdDialog = false
                    onWatchRewardedAd {}
                }) {
                    Text("Claim Reward", color = AppColors.primaryPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatchAdDialog = false }) {
                    Text("Not now", color = AppColors.textMuted)
                }
            },
        )
    }

    if (showItemCatalog) {
        ItemCatalogDialog(
            activeItemIds = activeItemIds,
            tracker       = metronomeVm.itemTracker,
            onDismiss     = { showItemCatalog = false },
        )
    }
}

// ── Game card — compact difficulty chips + mic toggle ─────────────────────────

@Composable
private fun GameCard(
    vm: RhythmGameViewModel,
    isMetronomePlaying: Boolean,
    onStopMetronome: () -> Unit,
    highScores: Map<String, Int> = emptyMap(),
    dailyEarned: Int = 0,
    dailyCap: Int = 0,
) {
    val pendingStart = remember { mutableStateOf<(() -> Unit)?>(null) }

    if (pendingStart.value != null) {
        val cardScale = remember { Animatable(0.2f) }
        LaunchedEffect(Unit) {
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        Dialog(
            onDismissRequest = { pendingStart.value = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppColors.surfaceDeep,
                shadowElevation = 24.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(min = 280.dp, max = 360.dp)
                    .graphicsLayer {
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                        alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Metronome is running",
                        color = AppColors.gold,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Stop it before starting the game, or let it keep playing in the background?",
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { pendingStart.value?.invoke(); pendingStart.value = null },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Keep Playing", color = AppColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            onClick = { onStopMetronome(); pendingStart.value?.invoke(); pendingStart.value = null },
                            shape = RoundedCornerShape(14.dp),
                            color = AppColors.gold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, AppColors.gold),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Stop & Play", color = AppColors.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    Surface(
        color = AppColors.surfaceDeep,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Stars,
                        contentDescription = null,
                        tint = AppColors.gold,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("RHYTHM", color = AppColors.gold, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(" GAME", color = AppColors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
                Text("notes fall · tap the line", color = AppColors.textSubtle, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            // ── Difficulty chips ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                difficulties.forEach { d ->
                    val best = highScores[d.name] ?: 0
                    val played = best > 0
                    Surface(
                        onClick = {
                            val start = { vm.setDifficulty(d.bpm, d.beats, d.name); vm.startGame() }
                            if (isMetronomePlaying) pendingStart.value = start else start()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (played) AppColors.gold.copy(alpha = 0.10f) else AppColors.surface,
                        border = BorderStroke(1.dp, if (played) AppColors.gold.copy(alpha = 0.35f) else AppColors.surfaceVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = d.short,
                                color = if (played) AppColors.gold else AppColors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (played) "$best" else "—",
                                color = if (played) AppColors.gold else AppColors.textDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            val stars = rhythmStars(best, d.beats)
                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                repeat(3) { i ->
                                    Text(
                                        if (i < stars) "★" else "☆",
                                        fontSize = 9.sp,
                                        color = if (i < stars) AppColors.gold
                                                else AppColors.gold.copy(alpha = 0.20f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            DailyTargetMeter(earned = dailyEarned, cap = dailyCap)

            MicTimingNudge()

        }
    }
}

// ── Daily target meter ────────────────────────────────────────────────────────
//
// Shows today's Rhythm-game Gnotes against the daily cap, reading from the points
// single source of truth (PointsManager.rhythmDailyTarget). Gives the card a clear
// "how close am I to maxing today" goal without inventing a new challenge system.

@Composable
private fun DailyTargetMeter(earned: Int, cap: Int) {
    val reached  = cap > 0 && earned >= cap
    val fraction = if (cap > 0) (earned.toFloat() / cap).coerceIn(0f, 1f) else 0f

    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = AppColors.surface,
        border   = BorderStroke(
            1.dp,
            if (reached) AppColors.gold.copy(alpha = 0.40f) else AppColors.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.TrackChanges,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (reached) "Daily target reached" else "Daily ${PointsConfig.CURRENCY_NAME}",
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$earned / $cap",
                        color = AppColors.gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AppColors.gold),
                    )
                }
            }
        }
    }
}

// ── Countdown ─────────────────────────────────────────────────────────────────

@Composable
private fun CountdownPanel(countDown: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Get ready!", color = AppColors.textMuted, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = countDown,
                transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                label = "countdown"
            ) { count ->
                Text(
                    count.toString(), fontSize = 140.sp, fontWeight = FontWeight.Black,
                    color = AppColors.gold, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Tap when the note hits the line", color = AppColors.primaryPurple, fontSize = 14.sp)
        }
    }
}

// ── Playing ───────────────────────────────────────────────────────────────────

@Composable
private fun PlayingPanel(
    vm: RhythmGameViewModel,
    score: Int,
    combo: Int,
    currentBeat: Int,
    timeSig: Int,
    lastQuality: HitQuality,
    lastHitOffset: Long,
    beatsRemaining: Int,
    visibleNotes: List<RenderNote>
) {
    val scope = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }
    val useMic by vm.useMic.collectAsStateWithLifecycle()
    val micAmplitude by vm.micAmplitude.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Score bar ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreBadge("SCORE", score.toString(), AppColors.gold)
                ScoreBadge("BEATS LEFT", beatsRemaining.toString(), AppColors.textMuted)
                ScoreBadge("COMBO", "×$combo", AppColors.textAccent)
            }

            Spacer(Modifier.height(10.dp))

            // Beat position dots
            BeatDotsRow(currentBeat = currentBeat, timeSig = timeSig)

            // Mic equaliser — only visible when microphone mode is active
            if (useMic) {
                Spacer(Modifier.height(8.dp))
                MicEqualizer(
                    amplitude = micAmplitude,
                    lastQuality = lastQuality,
                    micDetected = vm.micDetected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Note highway — driven by ViewModel's pre-computed render list
            NoteHighway(
                visibleNotes = visibleNotes,
                lastQuality = lastQuality,
                micDetected = vm.micDetected,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Quality feedback
            QualityFeedback(lastQuality = lastQuality, lastHitOffset = lastHitOffset)

            Spacer(Modifier.height(4.dp))

            // TAP button
            Surface(
                onClick = {
                    vm.onScreenTap()
                    scope.launch {
                        tapScale.animateTo(0.84f, tween(50))
                        tapScale.animateTo(1f, tween(90))
                    }
                },
                modifier = Modifier
                    .size(110.dp)
                    .scale(tapScale.value),
                shape = CircleShape,
                color = AppColors.primaryPurple,
                border = BorderStroke(2.dp, AppColors.mediumPurple),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        "TAP",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                onClick = { vm.stopGame() },
                shape = RoundedCornerShape(23.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, AppColors.stopRedBorder),
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(46.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.StopCircle,
                        contentDescription = "Stop game",
                        tint = AppColors.stopRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "STOP GAME",
                        color = AppColors.stopRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Beat position dots ────────────────────────────────────────────────────────

@Composable
private fun BeatDotsRow(currentBeat: Int, timeSig: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until timeSig) {
            val isActive = i == currentBeat
            val isAccent = i == 0
            val dotSize = if (isAccent) 14.dp else 10.dp
            val dotColor = when {
                isActive && isAccent -> AppColors.gold
                isActive -> GameColors.beatDotAccent
                isAccent -> GameColors.beatDotDim
                else -> GameColors.beatDotInactive
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (isActive) Modifier.border(
                            1.5.dp,
                            dotColor.copy(alpha = 0.5f),
                            CircleShape
                        ) else Modifier
                    )
            )
        }
    }
}

// ── Mic Equaliser ─────────────────────────────────────────────────────────────
//
// Scrolling bar graph driven by the live microphone RMS amplitude.
// Acts like a mini audio spectrum analyzer — bars shift left each frame.
// When a hit is detected (lastQuality changes to non-NONE), the bars flash
// briefly in the quality color so the developer/player can see the trigger.

private const val EQ_BARS = 30

@Composable
private fun MicEqualizer(
    amplitude: Float,
    lastQuality: HitQuality,
    micDetected: SharedFlow<Unit>,  // fires on every mic trigger, scored or not
    modifier: Modifier = Modifier
) {
    // Ring buffer of recent amplitude readings
    val history = remember {
        mutableStateListOf<Float>().also { list -> repeat(EQ_BARS) { list.add(0f) } }
    }

    // Push the latest amplitude sample into the scrolling buffer
    LaunchedEffect(amplitude) {
        history.removeAt(0)
        history.add(amplitude)
    }

    // Quality flash — fires when a detection was SCORED (gold/green/blue/red)
    val qualityFlashColor = when (lastQuality) {
        HitQuality.PERFECT -> AppColors.gold
        HitQuality.GOOD -> GameColors.good
        HitQuality.ALMOST -> GameColors.almost
        HitQuality.MISS -> GameColors.miss
        HitQuality.NONE -> Color.Transparent
    }
    val qualityAlpha = remember { Animatable(0f) }
    LaunchedEffect(lastQuality) {
        if (lastQuality != HitQuality.NONE) {
            qualityAlpha.snapTo(0.95f)
            qualityAlpha.animateTo(0f, tween(500))
        }
    }

    // Raw-detection flash — fires on every mic trigger (white), including out-of-time ones.
    // Shows the user that the mic DID hear something even if timing was wrong.
    val rawAlpha = remember { Animatable(0f) }
    LaunchedEffect(micDetected) {
        micDetected.collect {
            rawAlpha.snapTo(0.6f)
            rawAlpha.animateTo(0f, tween(250))
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Canvas(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()) {
            val totalGap = (EQ_BARS - 1) * 2.dp.toPx()
            val barW = (size.width - totalGap) / EQ_BARS
            val maxH = size.height

            // Determine the active overlay color:
            // Quality flash wins over raw flash when both are active.
            val qA = qualityAlpha.value
            val rA = rawAlpha.value
            val activeFlashColor = when {
                qA > 0.01f -> qualityFlashColor
                rA > 0.01f -> Color.White
                else -> Color.Transparent
            }
            val activeFlashAlpha = if (qA > 0.01f) qA else rA

            history.forEachIndexed { i, amp ->
                // Amplify so quiet claps still show; clamp at max
                val barH = (amp * maxH * 8f).coerceAtMost(maxH).coerceAtLeast(2.dp.toPx())
                val x = i * (barW + 2.dp.toPx())

                // Base color: dark purple (silence) → purple → gold (loud)
                val baseColor = when {
                    amp > 0.20f -> AppColors.gold
                    amp > 0.06f -> AppColors.textAccent
                    else -> GameColors.eqQuiet
                }

                val barColor =
                    if (activeFlashAlpha > 0.01f && activeFlashColor != Color.Transparent)
                        Color(
                            red = baseColor.red * (1f - activeFlashAlpha) + activeFlashColor.red * activeFlashAlpha,
                            green = baseColor.green * (1f - activeFlashAlpha) + activeFlashColor.green * activeFlashAlpha,
                            blue = baseColor.blue * (1f - activeFlashAlpha) + activeFlashColor.blue * activeFlashAlpha,
                            alpha = 1f
                        )
                    else baseColor

                drawRect(
                    color = barColor,
                    topLeft = Offset(x, maxH - barH),
                    size = Size(barW, barH)
                )
            }
        }

        // Label
        Text(
            "MIC",
            color = GameColors.hitLineIdle,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
    }
}

// ── Note Highway ──────────────────────────────────────────────────────────────
//
// Guitar Hero-style falling note lane.
//
// Notes are driven entirely by the ViewModel's [visibleNotes] list, which
// contains pre-computed progress values derived from the global clock:
//
//   progress = (songTimeMs - spawnTimeMs) / NOTE_TRAVEL_MS
//   0.0 → just spawned (top of lane)
//   1.0 → at hit line — tap for PERFECT
//   >1.0 → past hit line (missed, shown red briefly)
//
// Position is render-only; hit detection is time-based in the ViewModel.

@Composable
private fun NoteHighway(
    visibleNotes: List<RenderNote>,
    lastQuality: HitQuality,
    micDetected: SharedFlow<Unit>,
    modifier: Modifier = Modifier
) {
    // Quality glow at the hit line
    val hitLineColor = when (lastQuality) {
        HitQuality.PERFECT -> AppColors.gold
        HitQuality.GOOD -> GameColors.good
        HitQuality.ALMOST -> GameColors.almost
        HitQuality.MISS -> GameColors.miss
        HitQuality.NONE -> GameColors.hitLineIdle
    }

    // Every detected clap pulses the hit line, so a heard-but-unscored clap is acknowledged
    // in the lane the player is watching - distinct from the quality glow (scored hits only).
    val clapFlash = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        micDetected.collect {
            clapFlash.snapTo(1f)
            clapFlash.animateTo(0f, tween(220))
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val laneW = size.width
            val laneH = size.height
            val cx = laneW / 2f
            val hitY = laneH * 0.88f
            val noteR = 26.dp.toPx()
            val lineW = 3.dp.toPx()

            val railX1 = cx - noteR * 1.6f
            val railX2 = cx + noteR * 1.6f

            // Lane guidelines
            drawLine(AppColors.surfaceDim, Offset(railX1, 0f), Offset(railX1, laneH), lineW * 0.5f)
            drawLine(AppColors.surfaceDim, Offset(railX2, 0f), Offset(railX2, laneH), lineW * 0.5f)

            // Hit zone glow
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, hitLineColor.copy(alpha = 0.08f)),
                    startY = hitY - noteR * 2,
                    endY = hitY + noteR
                ),
                topLeft = Offset(railX1 - noteR, hitY - noteR * 2),
                size = Size(railX2 - railX1 + noteR * 2, noteR * 3)
            )

            // Hit line
            drawLine(
                color = hitLineColor,
                start = Offset(railX1 - noteR * 0.5f, hitY),
                end = Offset(railX2 + noteR * 0.5f, hitY),
                strokeWidth = lineW,
                cap = StrokeCap.Round
            )
            drawCircle(
                hitLineColor,
                radius = lineW * 1.2f,
                center = Offset(railX1 - noteR * 0.5f, hitY)
            )
            drawCircle(
                hitLineColor,
                radius = lineW * 1.2f,
                center = Offset(railX2 + noteR * 0.5f, hitY)
            )

            // Clap-heard pulse: a bright bar flashing over the hit line on every detection.
            if (clapFlash.value > 0f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.75f * clapFlash.value),
                    start = Offset(railX1 - noteR * 0.5f, hitY),
                    end = Offset(railX2 + noteR * 0.5f, hitY),
                    strokeWidth = lineW * 3f,
                    cap = StrokeCap.Round,
                )
            }

            // Draw notes — position derived from pre-computed progress
            for (note in visibleNotes) {
                val y = note.progress * hitY
                if (y > laneH + noteR) continue   // fully exited lane

                val inHitWindow = note.progress in 0.87f..1.13f
                val isPast = note.state == NoteState.MISSED || note.progress > 1.13f

                val noteColor = when {
                    isPast -> GameColors.miss   // missed — red
                    inHitWindow -> AppColors.gold   // in window — gold: TAP NOW
                    note.progress > 0.65f -> GameColors.noteAmber  // approaching — amber
                    else -> GameColors.notePurple   // far — purple
                }
                // Within the hit window, grow and brighten toward the line so the visual peak is
                // exactly AT the line (progress 1.0) — the moment to clap — instead of luring an
                // early clap the instant the note first turns gold.
                val proximity = if (inHitWindow)
                    (1f - kotlin.math.abs(note.progress - 1f) / 0.13f).coerceIn(0f, 1f) else 0f
                val r = noteR * (1f + 0.30f * proximity)
                val glowAlpha = if (inHitWindow) 0.18f + 0.40f * proximity else 0.15f
                val glowR = noteR * (1.8f + 1.0f * proximity)

                drawCircle(noteColor.copy(alpha = glowAlpha), radius = glowR, center = Offset(cx, y))
                drawCircle(noteColor, radius = r, center = Offset(cx, y))
                drawCircle(
                    Color.White.copy(alpha = 0.18f), radius = r * 0.45f,
                    center = Offset(cx - r * 0.2f, y - r * 0.25f)
                )
            }
        }
    }
}

// ── Quality feedback row ──────────────────────────────────────────────────────

@Composable
private fun QualityFeedback(lastQuality: HitQuality, lastHitOffset: Long) {
    val (mainText, mainColor) = when (lastQuality) {
        HitQuality.PERFECT -> "PERFECT!" to AppColors.gold
        HitQuality.GOOD -> "GOOD" to GameColors.good
        HitQuality.ALMOST -> "ALMOST" to GameColors.almost
        HitQuality.MISS -> "MISS" to GameColors.miss
        HitQuality.NONE -> "" to Color.Transparent
    }
    val hint = when {
        lastQuality == HitQuality.NONE || lastQuality == HitQuality.PERFECT -> ""
        lastQuality == HitQuality.MISS -> "didn't tap in time"
        lastHitOffset > 80L -> "a bit late"
        lastHitOffset < -80L -> "a bit early"
        else -> ""
    }

    Column(
        modifier = Modifier.height(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = lastQuality != HitQuality.NONE,
            enter = fadeIn(tween(60)), exit = fadeOut(tween(300))
        ) {
            Text(
                mainText,
                color = mainColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        if (hint.isNotEmpty()) {
            Text(hint, color = GameColors.rangeBlue, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScoreBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = AppColors.textSubtle, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

// ── Result ────────────────────────────────────────────────────────────────────

@Composable
private fun ResultPanel(
    result: com.example.metrognome.viewmodel.GameResult?,
    onPlayAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    if (result == null) return
    // Single source of truth (shared with the Rhythm-page difficulty chips) so the
    // stars earned for a run match the stars shown next to that difficulty's best score.
    val stars = rhythmStars(
        result.score,
        result.perfects + result.goods + result.almosts + result.misses,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "RESULT",
            color = AppColors.gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
        if (result.isNewHighScore) {
            Spacer(Modifier.height(6.dp))
            Surface(color = AppColors.gold, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "★  NEW BEST  ★", color = AppColors.background, fontSize = 13.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            repeat(3) { i ->
                Text(
                    if (i < stars) "★" else "☆", fontSize = 44.sp,
                    color = if (i < stars) AppColors.gold else Color(0x33FFFFFF)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${result.score}",
            color = Color.White,
            fontSize = 60.sp,
            fontWeight = FontWeight.Black
        )
        Text("points", color = AppColors.textMuted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Surface(
            color = AppColors.surfaceDim,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppColors.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ResultRow("Perfect", "${result.perfects}", AppColors.gold)
                ResultRow("Good", "${result.goods}", GameColors.good)
                ResultRow("Almost", "${result.almosts}", GameColors.almost)
                ResultRow("Miss", "${result.misses}", GameColors.miss)
                ResultRow("Max Combo", "×${result.maxCombo}", AppColors.textAccent)
            }
        }
        Spacer(Modifier.height(22.dp))
        Surface(
            onClick = onPlayAgain,
            shape = RoundedCornerShape(16.dp),
            color = AppColors.primaryPurple,
            border = BorderStroke(1.dp, AppColors.mediumPurple),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("PLAY AGAIN", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, AppColors.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Done", color = AppColors.textMuted, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppColors.textSecondary)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

// ── Moved from SettingsScreen: Gnotes, Loyalty, Streak, Collection ────────────

// Persists for the app session (resets on process death) — intentional.
// The count-up animation plays once per session, then on a 90-second cooldown.
private var gnotesBannerLastPlayedMs = 0L

@Composable
private fun PointsCard(
    snapshot: PointsSnapshot,
    onInfoClick: () -> Unit,
    canWatchToday: Boolean = false,
    adReady: Boolean = false,
    remainingToday: Int = 0,
    onWatchAdClick: () -> Unit = {},
) {
    val animatedCount = remember { Animatable(snapshot.total.toFloat()) }
    val scale         = remember { Animatable(1f) }
    var expanded      by remember { mutableStateOf(false) }
    val chevronAngle  by animateFloatAsState(
        targetValue   = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label         = "gnotes_chevron",
    )

    LaunchedEffect(snapshot.total) {
        val now = System.currentTimeMillis()
        if (now - gnotesBannerLastPlayedMs >= 90_000L) {
            gnotesBannerLastPlayedMs = now
            animatedCount.snapTo(0f)
            animatedCount.animateTo(
                targetValue   = snapshot.total.toFloat(),
                animationSpec = tween(durationMillis = 1600, easing = LinearOutSlowInEasing),
            )
            scale.animateTo(1.08f, tween(110))
            scale.animateTo(
                targetValue   = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
            )
        } else {
            animatedCount.snapTo(snapshot.total.toFloat())
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.gold.copy(alpha = 0.25f), shape)
            .clip(shape)
            .background(AppColors.surfaceDeep)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Tappable header: number + GNOTES + chevron ───────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                ) { expanded = !expanded },
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text          = String.format(LocalConfiguration.current.locales[0], "%,d", animatedCount.value.toInt()),
                    color         = AppColors.gold,
                    fontSize      = 44.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    lineHeight    = 44.sp,
                    letterSpacing = (-1.5).sp,
                )
                Text(
                    text          = PointsConfig.CURRENCY_NAME.uppercase(),
                    color         = AppColors.gold.copy(alpha = 0.65f),
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    modifier      = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector        = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint               = AppColors.textSubtle,
                modifier           = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.dp)
                    .rotate(chevronAngle),
            )
        }

        // ── Daily bonus teaser ────────────────────────────────────────────────
        val canTap = canWatchToday && adReady
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = AppColors.surfaceVariant.copy(alpha = 0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canTap) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = onWatchAdClick,
                    ) else Modifier
                )
                .padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint               = if (canTap) AppColors.mediumPurple.copy(alpha = 0.75f) else AppColors.textSubtle,
                    modifier           = Modifier.size(12.dp),
                )
                Text(
                    text  = "Metro's daily bonus",
                    color = if (canTap) AppColors.textSecondary else AppColors.textSubtle,
                    fontSize = 12.sp,
                )
            }
            val earn = minOf(PointsConfig.REWARDED_GNOTES_PER_WATCH, remainingToday)
            Text(
                text       = when {
                    canTap         -> "+$earn  →"
                    canWatchToday  -> "+$earn"
                    else           -> "✓  Today"
                },
                color      = if (canTap) AppColors.mediumPurple.copy(alpha = 0.8f) else AppColors.textSubtle,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ── Expandable body: contributions + How to earn ─────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically() + fadeIn(tween(180)),
            exit    = shrinkVertically() + fadeOut(tween(180)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (snapshot.contributions.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text      = "Start playing to earn your first ${PointsConfig.CURRENCY_NAME_SINGULAR}.",
                        color     = AppColors.textMuted,
                        fontSize  = 12.sp,
                        fontStyle = FontStyle.Italic,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = AppColors.surfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    snapshot.contributions.forEach { c ->
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text     = "${c.label}  ·  ${c.rawValue} ${c.rawUnit}",
                                color    = AppColors.textMuted,
                                fontSize = 12.sp,
                            )
                            Text(
                                text       = "+${c.points}",
                                color      = AppColors.textSecondary,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = AppColors.surfaceVariant.copy(alpha = 0.6f))
                Text(
                    text       = "How to earn  →",
                    color      = AppColors.gold.copy(alpha = 0.55f),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier
                        .clickable(onClick = onInfoClick)
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LoyaltyCard(
    currentDays: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val nextMilestone = LOYALTY_MILESTONES.firstOrNull { currentDays < it.days }

    Column(
        modifier = modifier
            .border(1.dp, AppColors.gold.copy(alpha = 0.20f), shape)
            .clip(shape)
            .background(AppColors.surfaceDeep)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint               = AppColors.gold,
                modifier           = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text       = "Day $currentDays",
                color      = AppColors.gold,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
            )
            Text(
                text     = " loyalty",
                color    = AppColors.textMuted,
                fontSize = 13.sp,
            )
            if (nextMilestone != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text     = "${nextMilestone.days - currentDays}d to ${nextMilestone.name}",
                    color    = AppColors.textSubtle,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LoyaltyMilestonePath(
            currentDays = currentDays,
            modifier    = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PracticeStreakCard(
    streak: Int,
    bestStreak: Int,
    practicedEpochDays: Set<Long>,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .border(1.dp, AppColors.gold.copy(alpha = 0.20f), shape)
            .clip(shape)
            .background(AppColors.surfaceDeep)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        StreakWeekCard(
            streak             = streak,
            bestStreak         = bestStreak,
            practicedEpochDays = practicedEpochDays,
            modifier           = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = AppColors.surfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text          = "How streaks work",
                color         = AppColors.textSubtle,
                fontSize      = 11.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier      = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                    ) { showInfo = !showInfo }
                    .padding(4.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Info,
                    contentDescription = "How streaks work",
                    tint               = if (showInfo) AppColors.textSecondary
                                         else AppColors.textDim.copy(alpha = 0.55f),
                    modifier           = Modifier.size(13.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = showInfo,
            enter   = expandVertically() + fadeIn(tween(180)),
            exit    = shrinkVertically() + fadeOut(tween(180)),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Finish a Practice session or a Speed Trainer session to count today.",
                ).forEach { rule ->
                    Text(
                        text       = rule,
                        color      = AppColors.textMuted,
                        fontSize   = 12.sp,
                        lineHeight = 17.sp,
                        modifier   = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(activeItemIds: Set<String>, onClick: () -> Unit) {
    val total    = METRO_ITEM_REGISTRY.size
    val unlocked = METRO_ITEM_REGISTRY.count { it.item.id in activeItemIds }
    val shape    = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.gold.copy(alpha = 0.25f), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(AppColors.surfaceDeep)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .background(AppColors.gold.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint               = AppColors.gold,
                    modifier           = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text       = "Metro's Collection",
                    color      = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                )
                Text(
                    text     = "$unlocked of $total items unlocked",
                    color    = AppColors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Text(
            text       = "See all  →",
            color      = AppColors.gold.copy(alpha = 0.55f),
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0D1A, widthDp = 360, heightDp = 400)
@Composable
private fun CountdownPanelPreview() {
    CountdownPanel(countDown = 3)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D1A)
@Composable
private fun BeatDotsRowPreview() {
    BeatDotsRow(currentBeat = 1, timeSig = 4)
}
