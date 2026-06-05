package com.example.metrognome.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import com.example.metrognome.ui.components.AppFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.BuildConfig
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.dev.DevTapTarget
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.billing.PremiumSoundDef
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.ui.overlays.ItemPreviewCanvas
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.billing.PurchasableItemDef
import com.example.metrognome.billing.PURCHASABLE_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.UnlockCondition
import com.example.metrognome.ui.components.metro_items.displayText
import com.example.metrognome.ui.components.LoyaltyMilestonePath
import com.example.metrognome.ui.components.OwnedBadge
import com.example.metrognome.ui.components.StreakWeekCard
import com.example.metrognome.points.UsageDayTracker
import com.example.metrognome.ui.dialogs.ShowcaseFrame
import com.example.metrognome.points.PointsManager
import com.example.metrognome.points.PointsSnapshot
import com.example.metrognome.ui.dialogs.EarnRulesDialog
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import com.example.metrognome.whatsnew.AppWhatsNew
import kotlin.math.roundToInt

private val itemOwnedMessages = mapOf(
    "glissie_fairy" to "Glissie is in your collection. She knows how to make an entrance.",
)

@Composable
fun SettingsScreen(
    vm: MetronomeViewModel,
    onTriggerFeedback: () -> Unit = {},
    onWatchRewardedAd: (onDone: () -> Unit) -> Unit = { it() },
) {
    val context = LocalContext.current
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val accentBeat by vm.accentBeat.collectAsStateWithLifecycle()
    val soundType by vm.soundType.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    val cheatModeEnabled by vm.cheatModeEnabled.collectAsStateWithLifecycle()
    var isDevMode by remember { mutableStateOf(DevEasterEgg.isDevModeActive(context)) }

    val isAdFree by vm.isAdFree.collectAsStateWithLifecycle()
    val removeAdsPriceText by vm.removeAdsPriceText.collectAsStateWithLifecycle()
    val isBillingAvailable by vm.isBillingAvailable.collectAsStateWithLifecycle()
    val isPurchasing by vm.isPurchasing.collectAsStateWithLifecycle()
    val isBillingConnecting by vm.isBillingConnecting.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    val purchasedSoundIds by vm.purchasedSoundIds.collectAsStateWithLifecycle()
    val soundPrices by vm.soundPrices.collectAsStateWithLifecycle()
    val availableSoundProductIds by vm.availableSoundProductIds.collectAsStateWithLifecycle()

    val purchasedItemProductIds by vm.purchasedItemProductIds.collectAsStateWithLifecycle()
    val itemPrices by vm.itemPrices.collectAsStateWithLifecycle()
    val availableItemProductIds by vm.availableItemProductIds.collectAsStateWithLifecycle()
    val activeItemIds by vm.activeItemIds.collectAsStateWithLifecycle()
    val isPresetsEnabled by vm.isPresetsEnabled.collectAsStateWithLifecycle()
    val isPracticeEnabled by vm.isPracticeEnabled.collectAsStateWithLifecycle()
    val isSpeedTrainerEnabled by vm.isSpeedTrainerEnabled.collectAsStateWithLifecycle()
    val practiceStreak by vm.practiceStreak.collectAsStateWithLifecycle()
    val bestStreak by vm.bestStreak.collectAsStateWithLifecycle()
    val practicedEpochDays by vm.practicedEpochDays.collectAsStateWithLifecycle()

    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()
    val gnoteCount by vm.gnoteCount.collectAsStateWithLifecycle()
    val adLoaded   by vm.rewardedAdLoaded.collectAsStateWithLifecycle()
    val pointsSnapshot = remember(gnoteCount) { PointsManager(context, vm.rewardedAdManager).getSnapshot() }
    val loyaltyDays    = remember { UsageDayTracker(context).distinctDaysCount() }
    var showEarnRules by remember { mutableStateOf(false) }
    var showWatchAdDialog by remember { mutableStateOf(false) }
    var showItemCatalog by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var testBannerCount by remember { mutableIntStateOf(1) }
    var showUnlockRules by remember { mutableStateOf(false) }
    var showAdPolicy    by remember { mutableStateOf(false) }
    var dialogSoundDef by remember { mutableStateOf<PremiumSoundDef?>(null) }
    var dialogItemDef  by remember { mutableStateOf<PurchasableItemDef?>(null) }

    LaunchedEffect(purchasedSoundIds) {
        val def = dialogSoundDef ?: return@LaunchedEffect
        if (def.productId in purchasedSoundIds) {
            dialogSoundDef = null
            vm.setSoundType(def.soundTypeIndex)
        }
    }

    LaunchedEffect(purchasedItemProductIds) {
        val def = dialogItemDef ?: return@LaunchedEffect
        if (def.productId in purchasedItemProductIds) {
            dialogItemDef = null
            // Unlock is handled by the ViewModel's billing observer; no extra call needed
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            PointsCard(
                snapshot       = pointsSnapshot,
                onInfoClick    = { showEarnRules = true },
                canWatchToday  = remember(gnoteCount) { vm.rewardedAdManager.canWatch() },
                adReady        = adLoaded,
                remainingToday = remember(gnoteCount) { vm.rewardedAdManager.remainingToday() },
                onWatchAdClick = { showWatchAdDialog = true },
            )

            Spacer(Modifier.height(6.dp))

            LoyaltyCard(
                currentDays = loyaltyDays,
                modifier    = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            if (isPracticeEnabled || (isSpeedTrainerEnabled && practiceStreak > 0)) {
                PracticeStreakCard(
                    streak             = practiceStreak,
                    bestStreak         = bestStreak,
                    practicedEpochDays = practicedEpochDays,
                    modifier           = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }

            CollectionCard(
                activeItemIds = activeItemIds,
                onClick       = { showItemCatalog = true },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Tempo & Rhythm")

            // BPM slider
            SettingsSliderRow(
                label = "Tempo",
                value = bpm.toFloat(),
                valueText = "$bpm BPM · ${tempoLabel(bpm)}",
                range = 20f..300f,
                onValueChange = { vm.setBpm(it.roundToInt()) }
            )

            // Time signature chips
            SettingsRow(label = "Time Signature") {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(2, 3, 4, 6, 7).forEach { sig ->
                        AppFilterChip(
                            selected = sig == timeSig,
                            onClick = { vm.setTimeSig(sig) },
                            label = "$sig/4",
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Sound")

            // Sound type chips
            SettingsRow(label = "Click Sound") {
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Classic", "Hi-Hat", "Wood", "Warm").forEachIndexed { index, name ->
                        AppFilterChip(
                            selected = index == soundType,
                            onClick = { vm.setSoundType(index) },
                            label = name,
                        )
                    }
                    // Premium sounds — one chip per registry entry
                    PREMIUM_SOUND_REGISTRY.forEach { def ->
                        val owned = def.productId in purchasedSoundIds
                        AppFilterChip(
                            selected = soundType == def.soundTypeIndex,
                            onClick = {
                                if (owned) vm.setSoundType(def.soundTypeIndex)
                                else dialogSoundDef = def
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(def.displayName)
                                Text(
                                    "  ★",
                                    color = if (soundType == def.soundTypeIndex) Color.White
                                            else AppColors.gold,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Volume slider
            SettingsSliderRow(
                label = "Click Volume",
                value = volume,
                valueText = "${(volume * 100).roundToInt()}%",
                range = 0f..1f,
                onValueChange = { vm.setVolume(it) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Visual")

            SettingsRow(label = "Accent Beat") {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AppFilterChip(
                        selected = accentBeat == 0,
                        onClick = { vm.setAccentBeat(0) },
                        label = "None",
                    )
                    for (beat in 1..timeSig) {
                        AppFilterChip(
                            selected = beat == accentBeat,
                            onClick = { vm.setAccentBeat(beat) },
                            label = "$beat",
                        )
                    }
                }
            }

            SettingsSwitchRow(
                checked = flashOnBeat,
                onChecked = { vm.setFlashOnBeat(it) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Items")

            PURCHASABLE_ITEM_REGISTRY.forEach { def ->
                val alreadyUnlocked = def.itemId in activeItemIds
                PurchasableItemRow(
                    def = def,
                    alreadyUnlocked = alreadyUnlocked,
                    priceText = itemPrices[def.productId],
                    isBillingConnecting = isBillingConnecting,
                    isAvailable = def.productId in availableItemProductIds,
                    ownedMessage = itemOwnedMessages[def.itemId] ?: "She's all yours.",
                    onClick = { dialogItemDef = def }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Remove Ads")

            RemoveAdsSection(
                isAdFree = isAdFree,
                priceText = removeAdsPriceText,
                isBillingAvailable = isBillingAvailable,
                isPurchasing = isPurchasing,
                isBillingConnecting = isBillingConnecting,
                onPurchase = { activity?.let { vm.purchaseRemoveAds(it) } },
                onRestore  = { vm.restorePurchases() },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("About")

            DevTapTarget(onToggled = { isDevMode = it }) {
                Column {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        buildString {
                            append("Build: ${if (BuildConfig.DEBUG) "Debug" else "Release"}")
                            if (!BuildConfig.DEBUG && DevEasterEgg.isManuallyEnabled(context)) {
                                append(" · Dev Mode ✓")
                            }
                        },
                        color = if (isDevMode) AppColors.gold else AppColors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isDevMode) {
            // ── DEV ONLY ──────────────────────────────────────────────────────────
            OutlinedButton(
                onClick = { vm.toggleCheatMode() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (cheatModeEnabled) AppColors.gold else AppColors.devGrey
                ),
                border = androidx.compose.foundation.BorderStroke(
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.deepPurple)
                ) {
                    Text("Preview Popup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { if (METRO_ITEM_REGISTRY.isNotEmpty()) previewIndex = (previewIndex + 1) % METRO_ITEM_REGISTRY.size },
                    modifier = Modifier.padding(start = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.surfaceVariant)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Show Unlock Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = { showAdPolicy = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Show Ad Policy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = { vm.resetAllProgress() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
            ) {
                Text("Reset All Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = { vm.debugClearAdFree() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Show ${AppWhatsNew.ALL.last()} What's New Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = { vm.debugResetReview() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Reset Review Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = onTriggerFeedback,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("Trigger Tuner Feedback Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = {
                    val limit = 3
                    com.example.metrognome.points.PointsBannerQueue.postActivity(
                        "Rhythm Game",
                        1,
                        testBannerCount,
                        limit,
                    )
                    testBannerCount = if (testBannerCount >= limit + 1) 1 else testBannerCount + 1
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
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
                    com.example.metrognome.points.PointsBannerQueue.postMilestone(
                        milestoneDays[testMilestoneIndex]
                    )
                    testMilestoneIndex = (testMilestoneIndex + 1) % milestoneDays.size
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text(
                    "Test Loyalty Banner  (${milestoneDays[testMilestoneIndex]} days)",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
            } // end DEBUG block

            Spacer(Modifier.height(8.dp))
        }

        if (!isAdFree) {
            AdBannerView(modifier = Modifier.fillMaxWidth())
        }
    }

    if (showEarnRules) {
        EarnRulesDialog(onDismiss = { showEarnRules = false })
    }

    if (showWatchAdDialog) {
        val remaining = vm.rewardedAdManager.remainingToday()
        val earn = minOf(com.example.metrognome.points.PointsConfig.REWARDED_GNOTES_PER_WATCH, remaining)
        AlertDialog(
            onDismissRequest  = { showWatchAdDialog = false },
            containerColor    = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor  = AppColors.textSecondary,
            title = { Text("Metro's Daily Bonus", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    text = "Watch a short clip and Metro rewards you with $earn ${com.example.metrognome.points.PointsConfig.CURRENCY_NAME}. " +
                           "Two clips per day. Come back tomorrow for more.",
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

    if (showItemCatalog) {
        com.example.metrognome.ui.dialogs.ItemCatalogDialog(
            activeItemIds = activeItemIds,
            tracker       = vm.itemTracker,
            onDismiss     = { showItemCatalog = false },
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

    dialogItemDef?.let { def ->
        val entry = METRO_ITEM_REGISTRY.find { it.item.id == def.itemId }
        if (entry != null) {
            PurchasableItemDialog(
                def = def,
                entry = entry,
                alreadyUnlocked = def.itemId in activeItemIds,
                priceText = itemPrices[def.productId],
                isPurchasing = isPurchasing,
                isBillingConnecting = isBillingConnecting,
                isAvailable = def.productId in availableItemProductIds,
                onPurchase = { activity?.let { vm.purchaseItem(it, def.productId) } },
                onRestore = { vm.restorePurchases() },
                onDismiss = { dialogItemDef = null }
            )
        }
    }

    dialogSoundDef?.let { def ->
        PremiumSoundDialog(
            def = def,
            priceText = soundPrices[def.productId],
            isPurchasing = isPurchasing,
            isBillingConnecting = isBillingConnecting,
            isAvailable = def.productId in availableSoundProductIds,
            onPreview = { vm.previewSound(def.soundTypeIndex) },
            onPurchase = { activity?.let { vm.purchaseSound(it, def.productId) } },
            onRestore = { vm.restorePurchases() },
            onDismiss = { dialogSoundDef = null }
        )
    }

    unlockQueue.firstOrNull()?.let { entry ->
        UnlockCelebrationOverlay(
            entry = entry,
            onDismiss = { vm.markCelebrated(entry.item.id) },
        )
    }
    } // close outer Box
}

@Composable
private fun PurchasableItemRow(
    def: PurchasableItemDef,
    alreadyUnlocked: Boolean,
    priceText: String?,
    isBillingConnecting: Boolean,
    isAvailable: Boolean,
    ownedMessage: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            def.displayName,
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            def.description,
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (alreadyUnlocked) {
            OwnedBadge(ownedMessage)
        } else when {
            isBillingConnecting -> {
                Text(
                    text = "Loading…",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                )
            }
            !isAvailable -> {
                Text(
                    text = "Unavailable",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                )
            }
            else -> {
                val buttonLabel = if (priceText != null) "Get ${def.displayName} - $priceText"
                                  else "Get ${def.displayName}"
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.gold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.gold)
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PurchasableItemDialog(
    def: PurchasableItemDef,
    entry: com.example.metrognome.ui.components.metro_items.MetroItemEntry,
    alreadyUnlocked: Boolean,
    priceText: String?,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    isAvailable: Boolean,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.example.metrognome.ui.dialogs.PremiumPurchaseDialog(
        title              = def.displayName,
        description        = def.description,
        actionLabel        = "Get ${def.displayName}",
        priceText          = priceText,
        isPurchasing       = isPurchasing,
        isBillingConnecting = isBillingConnecting,
        isAvailable        = isAvailable,
        alreadyUnlocked    = alreadyUnlocked,
        onPurchase         = onPurchase,
        onRestore          = onRestore,
        onDismiss          = onDismiss,
        previewContent     = { ItemPreviewCanvas(entry = entry, modifier = Modifier.size(width = 220.dp, height = 170.dp)) },
    )
}

@Composable
private fun PremiumSoundDialog(
    def: PremiumSoundDef,
    priceText: String?,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    isAvailable: Boolean,
    onPreview: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.example.metrognome.ui.dialogs.PremiumPurchaseDialog(
        title              = def.displayName,
        description        = def.description,
        actionLabel        = "Unlock ${def.displayName}",
        priceText          = priceText,
        isPurchasing       = isPurchasing,
        isBillingConnecting = isBillingConnecting,
        isAvailable        = isAvailable,
        onPurchase         = onPurchase,
        onRestore          = onRestore,
        onDismiss          = onDismiss,
        previewContent     = { SoundShowcase(def.displayName) },
        secondaryButton    = {
            com.example.metrognome.ui.dialogs.PreviewActionButton(
                label   = "▶  Preview (4 beats)",
                onClick = onPreview,
            )
        },
    )
}

/**
 * Animated showcase for a premium sound: a music note radiating soft gold
 * rings outward, suggesting a clear, resonant strike. The audible Preview
 * button does the real selling — this gives the dialog a living focal point.
 */
@Composable
private fun SoundShowcase(soundName: String) {
    ShowcaseFrame(caption = "PREMIUM SOUND") {
        val pulse by rememberInfiniteTransition(label = "soundPulse")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
                label = "soundPulseT",
            )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(132.dp)) {
                    val ringCount = 3
                    for (i in 0 until ringCount) {
                        val phase = (pulse + i.toFloat() / ringCount) % 1f
                        val radius = size.minDimension * (0.16f + phase * 0.34f)
                        drawCircle(
                            color = AppColors.gold.copy(alpha = (1f - phase) * 0.55f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.2f),
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .background(AppColors.gold.copy(alpha = 0.14f), CircleShape),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint               = AppColors.gold,
                        modifier           = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text       = soundName,
                color      = Color.White,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RemoveAdsSection(
    isAdFree: Boolean,
    priceText: String?,
    isBillingAvailable: Boolean,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "Give Metro the full stage",
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (isAdFree) {
            OwnedBadge("You have the ad-free version. Metro plays without interruptions.")
            return
        }
        Text(
            text = "Remove all ads, banner and full-screen, and Metro gets more room to dance. " +
                    "A bigger canvas, no interruptions. One-time purchase, forever.",
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        when {
            // Still connecting to Play on startup — neutral loading text, no button yet
            isBillingConnecting -> {
                Text(
                    text = "Loading…",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                )
            }

            // Connected but product not found in Play Console
            !isBillingAvailable && priceText == null -> {
                Text(
                    text = "Unavailable",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                )
            }

            else -> {
                val buttonLabel = when {
                    isPurchasing -> "Please wait…"
                    priceText != null -> "Remove Ads - $priceText"
                    else -> "Remove Ads"
                }
                OutlinedButton(
                    onClick = onPurchase,
                    enabled = !isPurchasing && isBillingAvailable,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.gold,
                        disabledContentColor = AppColors.textMuted,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!isPurchasing) AppColors.gold else AppColors.surfaceVariant
                    ),
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                TextButton(onClick = onRestore, enabled = !isPurchasing) {
                    Text(
                        "Already purchased? Restore",
                        color = if (!isPurchasing) AppColors.textDim else Color(0x22FFFFFF),
                        fontSize = 11.sp
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
                    text          = com.example.metrognome.points.PointsConfig.CURRENCY_NAME.uppercase(),
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
        // Three states: tappable (cap not hit AND a clip is loaded), cueing up
        // (cap not hit but no ad ready yet), and done for the day (cap hit).
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
            val earn = minOf(com.example.metrognome.points.PointsConfig.REWARDED_GNOTES_PER_WATCH, remainingToday)
            Text(
                text       = when {
                    canTap         -> "+$earn  →"
                    canWatchToday  -> "+$earn"        // available, clip still cueing up
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
                        text      = "Start playing to earn your first ${com.example.metrognome.points.PointsConfig.CURRENCY_NAME_SINGULAR}.",
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
    val nextMilestone = com.example.metrognome.ui.components.LOYALTY_MILESTONES
        .firstOrNull { currentDays < it.days }

    Column(
        modifier = modifier
            .border(1.dp, AppColors.gold.copy(alpha = 0.20f), shape)
            .clip(shape)
            .background(AppColors.surfaceDeep)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Header — matches StreakWeekCard header style exactly
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

        // ── "How streaks work" label + (i) toggle ───────────────────────────
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

        // ── Expandable rules ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showInfo,
            enter   = expandVertically() + fadeIn(tween(180)),
            exit    = shrinkVertically() + fadeOut(tween(180)),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Finish a Practice session or a Speed Trainer session to count today.",
                    "Come back tomorrow to keep your streak going.",
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
private fun StreakSimulator(
    currentStreak: Int,
    onApply: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var days by remember(currentStreak) { mutableIntStateOf(currentStreak) }
    val shape = RoundedCornerShape(10.dp)
    val btnPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)

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
                border         = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devDarkBorder),
                contentPadding = btnPadding,
            ) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Text(
                text       = "$days",
                color      = AppColors.gold,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.width(30.dp),
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
            )

            OutlinedButton(
                onClick        = { days++ },
                modifier       = Modifier.size(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                border         = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devDarkBorder),
                contentPadding = btnPadding,
            ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick        = { onApply(days) },
                modifier       = Modifier.height(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border         = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            ) { Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick        = onReset,
                modifier       = Modifier.height(36.dp),
                shape          = shape,
                colors         = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                border         = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devDarkBorder),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            ) { Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = AppColors.gold,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                valueText,
                color = AppColors.textAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.gold,
                activeTrackColor = AppColors.mediumPurple,
                inactiveTrackColor = AppColors.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(label, color = AppColors.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Flash on Beat", color = AppColors.textPrimary, fontWeight = FontWeight.Medium)
            Text("Golden screen flash on each beat", color = AppColors.textMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.gold,
                checkedTrackColor = AppColors.primaryPurple,
                uncheckedThumbColor = AppColors.controlInactive,
                uncheckedTrackColor = AppColors.surfaceVariant
            )
        )
    }
}
