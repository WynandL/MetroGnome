package com.example.metrognome.ui.screens

import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.metrognome.presets.BpmPreset
import com.example.metrognome.ads.AdBannerView
import com.example.metrognome.ui.dialogs.DialogCloseButton
import com.example.metrognome.ui.components.GnomeCanvas
import com.example.metrognome.ui.components.PresetChipsRow
import com.example.metrognome.ui.dialogs.ShowcaseFrame
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MetronomeScreen(vm: MetronomeViewModel) {
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
    val isPresetsUnlocked by vm.isPresetsUnlocked.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val presetsPriceText by vm.presetsPriceText.collectAsStateWithLifecycle()
    val isPurchasing by vm.isPurchasing.collectAsStateWithLifecycle()
    val isBillingConnecting by vm.isBillingConnecting.collectAsStateWithLifecycle()
    val isPracticeModeUnlocked by vm.isPracticeModeUnlocked.collectAsStateWithLifecycle()
    val practiceModePriceText by vm.practiceModePriceText.collectAsStateWithLifecycle()
    val isPracticeActive by vm.isPracticeActive.collectAsStateWithLifecycle()
    val practiceSecondsRemaining by vm.practiceSecondsRemaining.collectAsStateWithLifecycle()
    val practiceGoalSeconds by vm.practiceGoalSeconds.collectAsStateWithLifecycle()
    val practiceStreak by vm.practiceStreak.collectAsStateWithLifecycle()
    val pendingPracticeResult by vm.pendingPracticeResult.collectAsStateWithLifecycle()

    var tappedItem by remember { mutableStateOf<MetroItem?>(null) }
    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()
    val pendingWhatsNew by vm.pendingWhatsNew.collectAsStateWithLifecycle()

    var tapHintShown by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showBuyPresetsDialog by remember { mutableStateOf(false) }
    var showBuyPracticeDialog by remember { mutableStateOf(false) }
    var presetPendingDelete by remember { mutableStateOf<Pair<Int, BpmPreset>?>(null) }
    val presetLongPressHintSeen by vm.presetLongPressHintSeen.collectAsStateWithLifecycle()
    var showPracticeDialog by remember { mutableStateOf(false) }

    // Auto-close the buy dialog and open the save dialog once purchase confirms
    LaunchedEffect(isPresetsUnlocked) {
        if (isPresetsUnlocked && showBuyPresetsDialog) {
            showBuyPresetsDialog = false
            showSavePresetDialog = true
        }
    }

    LaunchedEffect(isPracticeModeUnlocked) {
        if (isPracticeModeUnlocked && showBuyPracticeDialog) {
            showBuyPracticeDialog = false
            showPracticeDialog = true
        }
    }

    LaunchedEffect(Unit) {
        vm.checkForNewUnlocks()
    }

    val activity = LocalActivity.current
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
            isOnSavedPreset = isPresetsUnlocked && presets.any { it.bpm == bpm },
            isPracticeActive = isPracticeActive,
            onSavePreset = {
                if (isPresetsUnlocked) showSavePresetDialog = true
                else showBuyPresetsDialog = true
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
                if (!isPracticeActive) {
                    if (isPracticeModeUnlocked) showPracticeDialog = true
                    else showBuyPracticeDialog = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.background)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        if (isPracticeActive) {
            PracticeProgressRow(
                practiceSecondsRemaining = practiceSecondsRemaining,
                practiceGoalSeconds = practiceGoalSeconds,
                onCancelPractice = { vm.cancelPractice() },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
            )
        } else if (practiceStreak > 0 && isPracticeModeUnlocked) {
            StreakBadgeRow(
                streak = practiceStreak,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            )
        }

        if (isPresetsUnlocked && presets.isNotEmpty()) {
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

    if (showBuyPresetsDialog) {
        BuyPresetsDialog(
            priceText = presetsPriceText,
            isPurchasing = isPurchasing,
            isBillingConnecting = isBillingConnecting,
            onBuy = { activity?.let { vm.purchasePresets(it) } },
            onRestore = { vm.restorePurchases() },
            onDismiss = { showBuyPresetsDialog = false }
        )
    }

    if (showBuyPracticeDialog) {
        BuyPracticeDialog(
            priceText = practiceModePriceText,
            isPurchasing = isPurchasing,
            isBillingConnecting = isBillingConnecting,
            onBuy = { activity?.let { vm.purchasePracticeMode(it) } },
            onRestore = { vm.restorePurchases() },
            onDismiss = { showBuyPracticeDialog = false }
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
            onDismiss = { vm.dismissPracticeResult() }
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
    onSavePreset: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleScreenOn: () -> Unit,
    onPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactIconChip(
            icon = Icons.Filled.Favorite,
            contentDescription = "Save BPM preset",
            active = isOnSavedPreset,
            accentColor = AppColors.gold,
            onClick = onSavePreset,
            modifier = Modifier.weight(1f).height(44.dp)
        )
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
            icon = Icons.Filled.Timer,
            contentDescription = "Practice session",
            active = isPracticeActive,
            accentColor = AppColors.primaryPurple,
            onClick = onPractice,
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
private fun StreakBadgeRow(
    streak: Int,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(AppColors.surfaceDim)
            .border(1.dp, AppColors.primaryPurple.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = "🔥", fontSize = 13.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Day $streak",
                color = AppColors.gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " streak",
                color = AppColors.textMuted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PracticeDurationDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(10) }

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
        onDismissRequest = onDismiss,
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

                // Hero: the selected duration, mirroring Save Preset's big BPM number.
                Text(
                    text = "$selected min",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30).forEach { mins ->
                        val isSelected = selected == mins
                        Surface(
                            onClick = { selected = mins },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) AppColors.primaryPurple else AppColors.surfaceDim,
                            border = if (isSelected) BorderStroke(1.dp, AppColors.gold) else null,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${mins}m",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "✨ Time spent practicing unlocks new gnome items",
                    color = AppColors.gold.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                Surface(
                    onClick = { onStart(selected) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, AppColors.gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Start Practice",
                            color = AppColors.gold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Cancel",
                    color = AppColors.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                )
            }
        }
    }
}

private fun formatPracticeTime(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun BuyPresetsDialog(
    priceText: String?,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    com.example.metrognome.ui.dialogs.PremiumPurchaseDialog(
        title              = "BPM Presets",
        description        = "Stop dialing in the same tempos over and over. Save them once, recall them instantly.",
        actionLabel        = "Unlock Presets",
        priceText          = priceText,
        isPurchasing       = isPurchasing,
        isBillingConnecting = isBillingConnecting,
        isAvailable        = priceText != null,
        onPurchase         = onBuy,
        onRestore          = onRestore,
        onDismiss          = onDismiss,
        highlights         = listOf(
            "Save up to 10 song tempos",
            "Switch tempo in a single tap",
            "One-time unlock - yours forever",
        ),
        previewContent     = {
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
                    currentBpm = 110,
                    showLongPressHint = false,
                    onPresetTap = {},
                    onPresetLongPress = { _, _ -> },
                )
            }
        },
    )
}

@Composable
private fun BuyPracticeDialog(
    priceText: String?,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    com.example.metrognome.ui.dialogs.PremiumPurchaseDialog(
        title              = "Practice Sessions",
        description        = "Turn loose noodling into real, measurable progress - one focused session at a time.",
        actionLabel        = "Unlock Practice",
        priceText          = priceText,
        isPurchasing       = isPurchasing,
        isBillingConnecting = isBillingConnecting,
        isAvailable        = priceText != null,
        onPurchase         = onBuy,
        onRestore          = onRestore,
        onDismiss          = onDismiss,
        highlights         = listOf(
            "Set a daily practice goal",
            "Track your day-by-day streak",
            "Every finished session celebrated",
            "Practice unlocks exclusive gnome items",
        ),
        previewContent     = {
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
            val dotColor by animateColorAsState(
                targetValue = when {
                    isActive && isAccent -> AppColors.gold
                    isActive -> AppColors.textAccent
                    isAccent -> Color(0x66FFD700)
                    else -> Color(0x33FFFFFF)
                },
                animationSpec = tween(80),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .size(if (isActive) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (i < timeSig - 1) Spacer(modifier = Modifier.width(10.dp))
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
        onSavePreset = {},
        onToggleMute = {},
        onToggleScreenOn = {},
        onPractice = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun StreakBadgeRowPreview() {
    StreakBadgeRow(streak = 5)
}
