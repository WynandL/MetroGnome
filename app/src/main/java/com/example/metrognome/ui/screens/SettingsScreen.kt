package com.example.metrognome.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import com.example.metrognome.ui.components.AppFilterChip
import com.example.metrognome.ui.components.TimeSignaturePicker
import com.example.metrognome.theory.Meter
import com.example.metrognome.theory.MeterTheory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.BuildConfig
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.debug.settings.DevToolsSection
import com.example.metrognome.dev.DevTapTarget
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.billing.PremiumSoundDef
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.ui.overlays.ItemPreviewCanvas
import com.example.metrognome.ui.overlays.MicCheckOverlay
import com.example.metrognome.ui.components.MicOptIn
import com.example.metrognome.ui.components.rememberMicPermissionRecovery
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.billing.PurchasableItemDef
import com.example.metrognome.billing.PURCHASABLE_ITEM_REGISTRY
import com.example.metrognome.ui.components.OwnedBadge
import com.example.metrognome.ui.components.instruments.InstrumentAffinityRow
import com.example.metrognome.ui.components.instruments.InstrumentAffinityBadges
import com.example.metrognome.ui.dialogs.ShowcaseFrame
import com.example.metrognome.ui.dialogs.GrooveCheckRecalibrateDialog
import com.example.metrognome.notifications.NotificationPermissionState
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import kotlin.math.roundToInt

private val itemOwnedMessages = mapOf(
    "glissie_fairy" to "Glissie is in your collection. She knows how to make an entrance.",
)

@Composable
fun SettingsScreen(
    vm: MetronomeViewModel,
    onTriggerFeedback: () -> Unit = {},
    onSimulateTuner: () -> Unit = {},
    onStopTunerSimulation: () -> Unit = {},
    notificationPermission: NotificationPermissionState? = null,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val timeSigDenom by vm.timeSigDenom.collectAsStateWithLifecycle()
    val accentBeats by vm.accentBeats.collectAsStateWithLifecycle()
    val soundType by vm.soundType.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    var isDevMode by remember { mutableStateOf(DevEasterEgg.isDevModeActive(context)) }
    var showMicCheck by remember { mutableStateOf(false) }
    // Shown when an already-calibrated user flips Groove Check back on: re-enable vs re-check.
    var showRecalPrompt by remember { mutableStateOf(false) }
    // Bumped when the mic check closes (or is reset) so the toggle re-reads engine state.
    var micCheckRefresh by remember { mutableIntStateOf(0) }

    // Live RECORD_AUDIO state, checked fresh (not trusted from the stored micModeEnabled
    // flag): after an uninstall/reinstall, Android restores SharedPreferences via backup
    // but never restores runtime permission grants, so micModeEnabled can read true while
    // the OS permission is actually gone. This is what lets the toggle notice that and
    // route back through a real permission request instead of silently no-op'ing. Shared
    // with Practice/Speed Trainer/Rhythm Game via MicPermissionRecovery.
    val micRecovery = rememberMicPermissionRecovery()

    // Re-reads on either a full Groove Check resolving (micCheckRefresh) or a lightweight
    // permission fix resolving (micRecovery.refreshKey) - both are monotonic counters, so
    // the sum changes whenever either one fires.
    val micCal = remember(micCheckRefresh + micRecovery.refreshKey) {
        com.example.metrognome.audio.selftest.MicCalibration.read(context)
    }

    val isAdFree by vm.isAdFree.collectAsStateWithLifecycle()
    val removeAdsPriceText by vm.removeAdsPriceText.collectAsStateWithLifecycle()
    val isBillingAvailable by vm.isBillingAvailable.collectAsStateWithLifecycle()
    val isPurchasing by vm.isPurchasing.collectAsStateWithLifecycle()
    val isBillingConnecting by vm.isBillingConnecting.collectAsStateWithLifecycle()

    val purchasedSoundIds by vm.purchasedSoundIds.collectAsStateWithLifecycle()
    val soundPrices by vm.soundPrices.collectAsStateWithLifecycle()
    val availableSoundProductIds by vm.availableSoundProductIds.collectAsStateWithLifecycle()

    val purchasedItemProductIds by vm.purchasedItemProductIds.collectAsStateWithLifecycle()
    val itemPrices by vm.itemPrices.collectAsStateWithLifecycle()
    val availableItemProductIds by vm.availableItemProductIds.collectAsStateWithLifecycle()
    val activeItemIds by vm.activeItemIds.collectAsStateWithLifecycle()

    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()
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
            Spacer(Modifier.height(16.dp))

            SettingsSectionTitle("Tempo & Rhythm")

            // BPM slider
            SettingsSliderRow(
                label = "Tempo",
                value = bpm.toFloat(),
                valueText = "$bpm BPM · ${tempoLabel(bpm)}",
                range = 20f..300f,
                onValueChange = { vm.setBpm(it.roundToInt()) }
            )

            // Time signature: presets + custom stepper + accent editor. The live classification
            // (e.g. "Compound triple") rides next to the heading as a quiet annotation.
            SettingsRow(
                label = "Time Signature",
                trailing = {
                    Text(
                        MeterTheory.label(Meter(timeSig, timeSigDenom)),
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                    )
                },
            ) {
                TimeSignaturePicker(
                    top = timeSig,
                    bottom = timeSigDenom,
                    accentBeats = accentBeats,
                    onMeterChange = { top, bottom -> vm.setMeter(top, bottom) },
                    onToggleAccent = { vm.toggleAccent(it) },
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Sound")

            // The single, app-wide mic-mode toggle. Speed Trainer, Practice, and the
            // Rhythm Game all use the result; there is no per-feature toggle. Turning it on
            // requires a passing self-test — if the device is not calibrated yet, the check
            // runs first and the toggle reflects the outcome (so the X / a fail leaves it
            // off). Kept first under Sound so it's seen before the click-sound chips rather
            // than after, since mic mode overrides the effective sound type when active.
            MicOptIn(
                // ANDed with live permission: if the OS grant is gone (stale post-reinstall
                // state) the switch reads as off, so the "permission required" copy and the
                // onRequestPermission path actually engage instead of silently no-op'ing.
                enabled = micCal.isActive && micRecovery.micGranted,
                hasMicPermission = micRecovery.micGranted,
                onToggle = {
                    val store = com.example.metrognome.audio.selftest.SelfTestCalibrationStore(context)
                    when {
                        micCal.isActive    -> { store.micModeEnabled = false; micCheckRefresh++ }
                        // Already calibrated and turning back on: ask whether to re-enable as-is
                        // or re-run the check, rather than silently re-enabling.
                        micCal.isCalibrated -> showRecalPrompt = true
                        else                -> showMicCheck = true
                    }
                },
                onRequestPermission = {
                    // Reinstall recovery: this device already proved itself, so if it's
                    // calibrated only the OS grant is missing - go straight for it (or App
                    // Settings if permanently denied) instead of re-running the whole check.
                    if (micCal.isCalibrated) micRecovery.fixPermission()
                    else showMicCheck = true
                },
                isPermanentlyDenied = micRecovery.micPermanentlyDenied,
            )

            Spacer(Modifier.height(10.dp))

            // Sound type chips, with the instrument-affinity nudge inline in the heading row:
            // the instruments the selected sound suits glow gold, the rest stay dim.
            SettingsRow(
                label = "Click Sound",
                trailing = { InstrumentAffinityRow(soundType = soundType) },
                trailingFillWidth = true,
                trailingSpacing = 20.dp,
            ) {
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
                onValueChange = { vm.setVolume(it) },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Visual")

            SettingsSwitchRow(
                checked = flashOnBeat,
                onChecked = { vm.setFlashOnBeat(it) }
            )

            if (notificationPermission != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = AppColors.surfaceVariant)
                Spacer(Modifier.height(8.dp))

                SettingsSectionTitle("System")
                NotificationsRow(state = notificationPermission)
            }

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
                DevToolsSection(
                    vm = vm,
                    micCal = micCal,
                    onTriggerFeedback = onTriggerFeedback,
                    onSimulateTuner = onSimulateTuner,
                    onStopTunerSimulation = onStopTunerSimulation,
                    onMicStateChanged = { micCheckRefresh++ },
                )
            }

            if (showMicCheck) {
                MicCheckOverlay(
                    onDismiss = {
                        showMicCheck = false
                        // Enable the toggle iff the check left a passing calibration; an X or a
                        // fail leaves it off ("read from the engine").
                        val store = com.example.metrognome.audio.selftest.SelfTestCalibrationStore(context)
                        if (store.isCalibrated) store.micModeEnabled = true
                        micCheckRefresh++
                    },
                    // Credit any resolved run toward the Groove Check item unlock. Queues the
                    // celebration; it surfaces when the user next visits the Gnome/Rhythm screen.
                    onRunCompleted = {
                        vm.itemTracker.recordMicCheckCompleted()
                        vm.checkForNewUnlocks()
                    },
                )
            }

            if (showRecalPrompt) {
                GrooveCheckRecalibrateDialog(
                    onReEnable = {
                        com.example.metrognome.audio.selftest.SelfTestCalibrationStore(context).micModeEnabled = true
                        micCheckRefresh++
                        showRecalPrompt = false
                    },
                    onRecalibrate = {
                        showRecalPrompt = false
                        showMicCheck = true   // same self-test flow a first-time user runs
                    },
                    onDismiss = { showRecalPrompt = false },
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        if (!isAdFree) {
            AdBannerView(modifier = Modifier.fillMaxWidth())
        }
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
            modifier = Modifier.padding(bottom = 2.dp)
        )
        // Once owned, drop the sales-pitch description and show only the collection subtext,
        // mirroring the ad-free section (heading + owned message, no advertising copy).
        if (!alreadyUnlocked) {
            Text(
                def.description,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
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
        belowDescription   = { InstrumentAffinityBadges(soundType = def.soundTypeIndex) },
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
            // CTA before purchase; a settled confirmation after it, so owners are not shown the
            // same "Go ad-free" pitch they already paid for. Styling stays identical to the other
            // owned surfaces (e.g. Glissie) for consistency: only the words change.
            text = if (isAdFree) "You're ad-free" else "Go ad-free",
            color = AppColors.textPrimary,
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
            isBillingConnecting -> {
                Text(
                    text = "Loading…",
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                )
            }

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
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
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
    onValueChange: (Float) -> Unit,
    bottomPadding: Dp = 16.dp,
) {
    Column(modifier = Modifier.padding(bottom = bottomPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = AppColors.textPrimary, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
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
private fun SettingsRow(
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    trailingFillWidth: Boolean = false,
    trailingSpacing: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        if (trailing == null) {
            Text(label, color = AppColors.textPrimary, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(label, color = AppColors.textPrimary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(trailingSpacing))
                if (trailingFillWidth) {
                    Box(Modifier.weight(1f)) { trailing() }
                } else {
                    trailing()
                }
            }
        }
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
        Spacer(Modifier.width(12.dp))
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

/**
 * Self-serve entry point for the notification permission - always live, never a stored
 * flag (that's exactly the bug the mic toggle had after a reinstall). The switch always
 * reflects the real OS permission, checked fresh on every recomposition via
 * [NotificationPermissionState.granted].
 *
 * Since an app cannot revoke its own POST_NOTIFICATIONS grant, tapping while already
 * granted opens the system per-app notification screen instead of pretending to toggle
 * something locally - the OS is the single source of truth either direction.
 */
@Composable
private fun NotificationsRow(state: NotificationPermissionState) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Notifications", color = AppColors.textPrimary, fontWeight = FontWeight.Medium)
            Text(
                when {
                    state.granted -> "New sounds, features, and app news"
                    state.permanentlyDenied -> "Blocked. Tap to open App Settings."
                    else -> "Get notified about new sounds and features"
                },
                color = if (state.permanentlyDenied) AppColors.gold.copy(alpha = 0.8f) else AppColors.textMuted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = state.granted,
            onCheckedChange = {
                if (state.granted) {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                } else {
                    state.request()
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.gold,
                checkedTrackColor = AppColors.primaryPurple,
                uncheckedThumbColor = AppColors.controlInactive,
                uncheckedTrackColor = AppColors.surfaceVariant
            )
        )
    }
}
