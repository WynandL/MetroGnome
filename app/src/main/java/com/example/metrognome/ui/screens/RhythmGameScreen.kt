package com.example.metrognome.ui.screens

import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.points.PointsManager
import com.example.metrognome.points.PointsSnapshot
import com.example.metrognome.points.UsageDayTracker
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.MicTimingNudge
import com.example.metrognome.ui.components.rememberMicPermissionRecovery
import com.example.metrognome.ui.components.RoomNoiseIndicator
import com.example.metrognome.ui.components.LOYALTY_MILESTONES
import com.example.metrognome.ui.components.LoyaltyMilestonePath
import com.example.metrognome.ui.components.StreakWeekCard
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.dialogs.EarnRulesDialog
import com.example.metrognome.ui.dialogs.ItemCatalogDialog
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.ui.components.AppCard
import com.example.metrognome.ui.components.AppInset
import com.example.metrognome.ui.components.CardGap
import com.example.metrognome.ui.components.CardHeader
import com.example.metrognome.ui.game.RhythmResultPanel
import com.example.metrognome.ui.game.RhythmStage
import com.example.metrognome.ui.game.StarRating
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.metrognome.ui.components.MetroCollectionIcon
import com.example.metrognome.ui.components.GhostButton
import com.example.metrognome.ui.components.GoldButton
import com.example.metrognome.ui.dialogs.AppDialog
import com.example.metrognome.ui.dialogs.DialogTitle
import com.example.metrognome.ui.components.AppDivider
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.GamePhase
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.viewmodel.RhythmGameViewModel
import com.example.metrognome.viewmodel.rhythmStars

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
    val countDown by vm.countDown.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val highScores by vm.highScores.collectAsStateWithLifecycle()
    val roomNoisy by vm.roomNoisy.collectAsStateWithLifecycle()

    val unlockQueue  by vm.unlockQueue.collectAsStateWithLifecycle()


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

                // The countdown and the game share one stage, so the lane is already there
                // while the numbers count down.
                GamePhase.COUNTDOWN, GamePhase.PLAYING -> RhythmStage(vm, phase, countDown)

                GamePhase.RESULT -> RhythmResultPanel(
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

    // Live room-noise indicator, shown only during play (the live complement to the enable-time mic
    // self-test): a small tappable amber glyph in the top corner. Never affects detection or scoring.
    RoomNoiseIndicator(
        visible = phase == GamePhase.PLAYING && roomNoisy,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 8.dp, end = 12.dp),
    )

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

        Spacer(Modifier.height(CardGap))

        LoyaltyCard(
            currentDays = loyaltyDays,
            modifier    = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(CardGap))

        // Always show the streak on the Rhythm page, even at Day 0 - it reads "Start your streak"
        // and nudges the user to begin one. (The home page keeps the conditional gating so Metro
        // gets maximum space there.)
        PracticeStreakCard(
            streak             = practiceStreak,
            bestStreak         = bestStreak,
            practicedEpochDays = practicedEpochDays,
            modifier           = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CardGap))

        CollectionCard(
            activeItemIds = activeItemIds,
            onClick       = { showItemCatalog = true },
        )

        Spacer(Modifier.height(CardGap))

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
        com.example.metrognome.ui.components.DailyBonusConfirmDialog(
            earn      = earn,
            onConfirm = {
                showWatchAdDialog = false
                onWatchRewardedAd {}
            },
            onDismiss = { showWatchAdDialog = false },
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
    // Groove Check can read as opted-in (MicCalibration.isActive, a SharedPreferences flag
    // that survives an uninstall/reinstall via backup) while the OS RECORD_AUDIO grant is
    // actually gone. Starting a game routes through micRecovery.startWithMicPermissionCheck
    // so that gap gets a real permission prompt right when the player taps a difficulty,
    // instead of the game silently running in tap-only mode with no explanation. See
    // MicPermissionRecovery.
    val micRecovery = rememberMicPermissionRecovery()

    val pendingStart = remember { mutableStateOf<(() -> Unit)?>(null) }

    if (pendingStart.value != null) {
        AppDialog(onDismiss = { pendingStart.value = null }) {
            DialogTitle("Metronome is running")
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
                GhostButton("Keep Playing", { pendingStart.value?.invoke(); pendingStart.value = null }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                GoldButton("Stop & Play", { onStopMetronome(); pendingStart.value?.invoke(); pendingStart.value = null }, Modifier.weight(1f))
            }
        }
    }

    AppCard {
        CardHeader("RHYTHM GAME")
        Spacer(Modifier.height(4.dp))
        Text(
            "Notes fall down the lane. Hit each one as it fills the ring.",
            color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 17.sp,
        )

        Spacer(Modifier.height(14.dp))

        // Difficulty tiles: tempo, then this level's best run. All five share one structure
        // and height whether played or not.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            difficulties.forEach { d ->
                val best = highScores[d.name] ?: 0
                val played = best > 0
                AppInset(
                    highlighted = played,
                    onClick = {
                        val start = {
                            micRecovery.startWithMicPermissionCheck {
                                vm.setDifficulty(d.bpm, d.beats, d.name)
                                vm.startGame()
                            }
                        }
                        if (isMetronomePlaying) pendingStart.value = start else start()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "${d.name}, ${d.bpm} beats per minute, ${d.beats} notes" +
                                if (played) ", best $best" else ""
                        },
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = d.short,
                            color = if (played) AppColors.gold else AppColors.textDim,
                            fontSize = 10.sp, lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        )
                        Text(
                            text = "${d.bpm}",
                            color = AppColors.textPrimary,
                            fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "BPM",
                            color = AppColors.textDim,
                            fontSize = 10.sp, lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        StarRating(stars = rhythmStars(best, d.beats), starSize = 10.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (played) "$best" else "Play",
                            color = if (played) AppColors.gold else AppColors.textMuted,
                            fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

            Spacer(Modifier.height(12.dp))
            DailyTargetMeter(earned = dailyEarned, cap = dailyCap)

            MicTimingNudge(
                onFixPermission = micRecovery.fixPermission,
                refreshKey = micRecovery.refreshKey,
            )
    }
}

// ── Daily target meter ────────────────────────────────────────────────────────
//
// Shows today's Rhythm-game Gnotes against the daily cap, reading from the points
// single source of truth (PointsManager.rhythmDailyTarget). Gives the card a clear
// "how close am I to maxing today" goal without inventing a new challenge system.

@Composable
private fun DailyTargetMeter(earned: Int, cap: Int) {
    val reached  = cap in 1..earned
    val fraction = if (cap > 0) (earned.toFloat() / cap).coerceIn(0f, 1f) else 0f

    AppInset(highlighted = reached, modifier = Modifier.fillMaxWidth()) {
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

    AppCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Tappable header: number + GNOTES + chevron ───────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                ) { expanded = !expanded },
        ) {
            com.example.metrognome.ui.components.GnoteTickFlair(
                count    = snapshot.total,
                modifier = Modifier.fillMaxWidth(),
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

        // ── Daily bonus CTA ────────────────────────────────────────────────────
        // Collapsed, this row is the last thing in the card, so it butts straight up
        // against the card's own 18dp bottom padding (plus DailyBonusCta's 4dp internal
        // padding = 22dp of trailing whitespace) - the gap above needs to match that, not
        // the smaller 4dp used when expanded (where contributions follow below instead).
        Spacer(Modifier.height(6.dp))
        AppDivider()
        Spacer(Modifier.height(if (expanded) 4.dp else 18.dp))
        com.example.metrognome.ui.components.DailyBonusCta(
            canWatchToday  = canWatchToday,
            adReady        = adReady,
            remainingToday = remainingToday,
            onClick        = onWatchAdClick,
        )

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
                    AppDivider()
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
                AppDivider()
                Text(
                    text       = "How to earn  →",
                    color      = AppColors.goldBorderStrong,
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
}

@Composable
private fun LoyaltyCard(
    currentDays: Int,
    modifier: Modifier = Modifier,
) {
    val nextMilestone = LOYALTY_MILESTONES.firstOrNull { currentDays < it.days }

    AppCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
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

    AppCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        StreakWeekCard(
            streak             = streak,
            bestStreak         = bestStreak,
            practicedEpochDays = practicedEpochDays,
            modifier           = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        AppDivider()
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
    AppCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetroCollectionIcon(size = 28.dp)
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
            color      = AppColors.goldBorderStrong,
            fontSize   = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
}
