package com.example.metrognome.ui.screens

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.BuildConfig
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.dev.DevTapTarget
import com.example.metrognome.ads.AdBannerView
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.billing.PremiumSoundDef
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.ui.overlays.ItemPreviewCanvas
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.billing.PurchasableItemDef
import com.example.metrognome.billing.PURCHASABLE_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.UnlockCondition
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MetronomeViewModel) {
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
    val isPresetsUnlocked by vm.isPresetsUnlocked.collectAsStateWithLifecycle()
    val isPracticeModeUnlocked by vm.isPracticeModeUnlocked.collectAsStateWithLifecycle()
    val practiceModePrice by vm.practiceModePriceText.collectAsStateWithLifecycle()

    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()
    var previewIndex by remember { mutableIntStateOf(0) }
    var showUnlockRules by remember { mutableStateOf(false) }
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
                        FilterChip(
                            selected = sig == timeSig,
                            onClick = { vm.setTimeSig(sig) },
                            label = { Text("$sig/4") },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
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
                        FilterChip(
                            selected = index == soundType,
                            onClick = { vm.setSoundType(index) },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
                        )
                    }
                    // Premium sounds — one chip per registry entry
                    PREMIUM_SOUND_REGISTRY.forEach { def ->
                        val owned = def.productId in purchasedSoundIds
                        FilterChip(
                            selected = soundType == def.soundTypeIndex,
                            onClick = {
                                if (owned) vm.setSoundType(def.soundTypeIndex)
                                else dialogSoundDef = def
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(def.displayName)
                                    Text(
                                        "  ★",
                                        color = if (soundType == def.soundTypeIndex) Color.White
                                                else AppColors.gold,
                                        fontSize = 9.sp
                                    )
                                }
                            },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
                        )
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
                    FilterChip(
                        selected = accentBeat == 0,
                        onClick = { vm.setAccentBeat(0) },
                        label = { Text("None") },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.primaryPurple,
                            selectedLabelColor = Color.White,
                            containerColor = AppColors.surface,
                            labelColor = AppColors.textSecondary
                        )
                    )
                    for (beat in 1..timeSig) {
                        FilterChip(
                            selected = beat == accentBeat,
                            onClick = { vm.setAccentBeat(beat) },
                            label = { Text("$beat") },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
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
                    onClick = { dialogItemDef = def }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Practice Mode")

            PracticeModeSection(
                isPracticeModeUnlocked = isPracticeModeUnlocked,
                priceText = practiceModePrice,
                isPurchasing = isPurchasing,
                isBillingConnecting = isBillingConnecting,
                onPurchase = { activity?.let { vm.purchasePracticeMode(it) } },
                onRestore  = { vm.restorePurchases() },
            )

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
                    if (isPresetsUnlocked) "Clear Presets Unlock + Data" else "Presets Not Unlocked",
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
                    if (isPracticeModeUnlocked) "Clear Practice Mode + Streak" else "Practice Not Unlocked",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            } // end DEBUG block

            Spacer(Modifier.height(8.dp))
        }

        if (!isAdFree) {
            AdBannerView(modifier = Modifier.fillMaxWidth())
        }
    }

    if (showUnlockRules) {
        AlertDialog(
            onDismissRequest = { showUnlockRules = false },
            containerColor = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor = AppColors.textSecondary,
            title = { Text("Unlock Rules", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    METRO_ITEM_REGISTRY.sortedBy { entry ->
                        when (val c = entry.condition) {
                            is UnlockCondition.MetronomeSeconds     -> c.required.toDouble()
                            is UnlockCondition.RhythmGamesCompleted -> c.required * 300.0
                            is UnlockCondition.DaysSinceFirstLaunch -> c.required * 86_400.0
                            UnlockCondition.Always                  -> -1.0
                        }
                    }.forEach { entry ->
                        Text(
                            text = entry.item.displayName,
                            color = AppColors.textAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = entry.item.unlockCondition,
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
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            def.displayName,
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Medium,
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
        when {
            alreadyUnlocked -> Text(
                "✓  Already yours",
                color = AppColors.gold,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            else -> {
                // Always tappable — dialog handles all billing states (loading, unavailable, buy)
                val buttonLabel = when {
                    isBillingConnecting -> "Loading…"
                    priceText != null   -> "Get ${def.displayName} - $priceText"
                    else                -> "Get ${def.displayName}"
                }
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (!isBillingConnecting && isAvailable) AppColors.gold
                                       else AppColors.textMuted
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!isBillingConnecting && isAvailable) AppColors.gold
                        else AppColors.surfaceVariant
                    )
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
    com.example.metrognome.ui.components.PremiumPurchaseDialog(
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
        previewContent     = { ItemPreviewCanvas(entry = entry) },
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
    com.example.metrognome.ui.components.PremiumPurchaseDialog(
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
        secondaryButton    = {
            com.example.metrognome.ui.components.PreviewActionButton(
                label   = "▶  Preview (4 beats)",
                onClick = onPreview,
            )
        },
    )
}

@Composable
private fun PracticeModeSection(
    isPracticeModeUnlocked: Boolean,
    priceText: String?,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    if (isPracticeModeUnlocked) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                "✓  Practice Mode unlocked",
                color = AppColors.gold,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Set daily goals, track your streak, and celebrate every completed session.",
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        return
    }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            "Build a practice habit",
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Set a daily practice goal, track your streak, and celebrate every completed session - building a habit one day at a time.",
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        when {
            isBillingConnecting -> Text(
                "Loading…",
                color = AppColors.textMuted,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic
            )
            priceText == null -> Text(
                "Unavailable",
                color = AppColors.textMuted,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic
            )
            else -> {
                val label = if (isPurchasing) "Please wait…" else "Unlock Practice Mode  —  $priceText"
                OutlinedButton(
                    onClick = onPurchase,
                    enabled = !isPurchasing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.gold,
                        disabledContentColor = AppColors.textMuted
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!isPurchasing) AppColors.gold else AppColors.surfaceVariant
                    )
                ) {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
private fun RemoveAdsSection(
    isAdFree: Boolean,
    priceText: String?,
    isBillingAvailable: Boolean,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    if (isAdFree) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "✓  You're ad-free!",
                color = AppColors.gold,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Metro has the full stage to himself. Enjoy the extra room.",
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        return
    }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "Give Metro the full stage",
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
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
