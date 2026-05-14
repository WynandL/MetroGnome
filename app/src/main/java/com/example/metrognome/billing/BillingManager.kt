package com.example.metrognome.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

class BillingManager(application: Application) {

    companion object {
        // ── Product IDs ───────────────────────────────────────────────────────
        const val PRODUCT_REMOVE_ADS    = "remove_ads"
        const val PRODUCT_BPM_PRESETS   = "bpm_presets"
        const val PRODUCT_PRACTICE_MODE = "practice_mode"

        // Sounds — add future PRODUCT_SOUND_* constants here, then add to SOUND_PRODUCTS
        const val PRODUCT_SOUND_BELL   = "sound_bell"
        const val PRODUCT_SOUND_BOWL   = "sound_bowl"
        val SOUND_PRODUCTS: Set<String> = setOf(PRODUCT_SOUND_BELL, PRODUCT_SOUND_BOWL)

        // Items — add future PRODUCT_ITEM_* constants here, then add to ITEM_PRODUCTS
        const val PRODUCT_ITEM_GLISSIE = "item_glissie"
        val ITEM_PRODUCTS: Set<String> = setOf(PRODUCT_ITEM_GLISSIE)

        // ── SharedPreferences keys ────────────────────────────────────────────
        private const val PREFS_NAME                     = "billing_state"
        private const val KEY_AD_FREE                    = "ad_free"
        private const val KEY_PRESETS_UNLOCKED           = "presets_unlocked"
        private const val KEY_PRACTICE_MODE_UNLOCKED     = "practice_mode_unlocked"
        private const val KEY_PURCHASED_SOUND_IDS        = "purchased_sound_ids"
        private const val KEY_PURCHASED_ITEM_PRODUCT_IDS = "purchased_item_product_ids"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Public state ──────────────────────────────────────────────────────────

    private val _isAdFree = MutableStateFlow(prefs.getBoolean(KEY_AD_FREE, false))
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _priceText = MutableStateFlow<String?>(null)
    val priceText: StateFlow<String?> = _priceText.asStateFlow()

    // Presets
    private val _isPresetsUnlocked = MutableStateFlow(prefs.getBoolean(KEY_PRESETS_UNLOCKED, false))
    val isPresetsUnlocked: StateFlow<Boolean> = _isPresetsUnlocked.asStateFlow()

    private val _presetsPrice = MutableStateFlow<String?>(null)
    val presetsPrice: StateFlow<String?> = _presetsPrice.asStateFlow()

    // Practice Mode
    private val _isPracticeModeUnlocked = MutableStateFlow(prefs.getBoolean(KEY_PRACTICE_MODE_UNLOCKED, false))
    val isPracticeModeUnlocked: StateFlow<Boolean> = _isPracticeModeUnlocked.asStateFlow()

    private val _practiceModePrice = MutableStateFlow<String?>(null)
    val practiceModePrice: StateFlow<String?> = _practiceModePrice.asStateFlow()

    // Sounds
    private val _purchasedSoundIds = MutableStateFlow(loadSet(KEY_PURCHASED_SOUND_IDS))
    val purchasedSoundIds: StateFlow<Set<String>> = _purchasedSoundIds.asStateFlow()

    private val _soundPrices = MutableStateFlow<Map<String, String?>>(emptyMap())
    val soundPrices: StateFlow<Map<String, String?>> = _soundPrices.asStateFlow()

    private val _availableSoundProductIds = MutableStateFlow<Set<String>>(emptySet())
    val availableSoundProductIds: StateFlow<Set<String>> = _availableSoundProductIds.asStateFlow()

    // Items
    private val _purchasedItemProductIds = MutableStateFlow(loadSet(KEY_PURCHASED_ITEM_PRODUCT_IDS))
    val purchasedItemProductIds: StateFlow<Set<String>> = _purchasedItemProductIds.asStateFlow()

    private val _itemPrices = MutableStateFlow<Map<String, String?>>(emptyMap())
    val itemPrices: StateFlow<Map<String, String?>> = _itemPrices.asStateFlow()

    private val _availableItemProductIds = MutableStateFlow<Set<String>>(emptySet())
    val availableItemProductIds: StateFlow<Set<String>> = _availableItemProductIds.asStateFlow()

    /** True while a purchase or restore query is in flight. */
    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _isBillingAvailable = MutableStateFlow(false)
    val isBillingAvailable: StateFlow<Boolean> = _isBillingAvailable.asStateFlow()

    private val _isConnecting = MutableStateFlow(true)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    // ── Billing client ────────────────────────────────────────────────────────

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        _isPurchasing.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> Unit
        }
    }

    private val billingClient = BillingClient.newBuilder(application)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        connect()
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                _isConnecting.value = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _isBillingAvailable.value = false
                scope.launch {
                    delay(3_000L)
                    if (!billingClient.isReady) connect()
                }
            }
        })
    }

    // ── Product details ───────────────────────────────────────────────────────

    private suspend fun queryProductDetails() {
        val allProductIds = buildList {
            add(PRODUCT_REMOVE_ADS)
            add(PRODUCT_BPM_PRESETS)
            add(PRODUCT_PRACTICE_MODE)
            addAll(SOUND_PRODUCTS)
            addAll(ITEM_PRODUCTS)
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(allProductIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.productDetailsList?.forEach { details ->
                val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                when (details.productId) {
                    PRODUCT_REMOVE_ADS -> {
                        _priceText.value = price
                        _isBillingAvailable.value = true
                    }
                    PRODUCT_BPM_PRESETS   -> _presetsPrice.value = price
                    PRODUCT_PRACTICE_MODE -> _practiceModePrice.value = price
                    in SOUND_PRODUCTS -> {
                        _soundPrices.value += (details.productId to price)
                        _availableSoundProductIds.value += details.productId
                    }
                    in ITEM_PRODUCTS -> {
                        _itemPrices.value += (details.productId to price)
                        _availableItemProductIds.value += details.productId
                    }
                }
            }
        }
    }

    // ── Purchase flow ─────────────────────────────────────────────────────────

    fun launchPurchaseFlow(activity: Activity) =
        launchPurchaseFlowFor(activity, PRODUCT_REMOVE_ADS)

    fun launchPresetsPurchaseFlow(activity: Activity) =
        launchPurchaseFlowFor(activity, PRODUCT_BPM_PRESETS)

    fun launchPracticePurchaseFlow(activity: Activity) =
        launchPurchaseFlowFor(activity, PRODUCT_PRACTICE_MODE)

    fun launchSoundPurchaseFlow(activity: Activity, productId: String) =
        launchPurchaseFlowFor(activity, productId)

    fun launchItemPurchaseFlow(activity: Activity, productId: String) =
        launchPurchaseFlowFor(activity, productId)

    private fun launchPurchaseFlowFor(activity: Activity, productId: String) {
        if (_isPurchasing.value) return
        _isPurchasing.value = true

        scope.launch {
            val result = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    ))
                    .build()
            )
            val productDetails = result.productDetailsList?.firstOrNull()
            if (productDetails == null) {
                _isPurchasing.value = false
                return@launch
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                ))
                .build()
            val billingResult = billingClient.launchBillingFlow(activity, flowParams)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _isPurchasing.value = false
            }
        }
    }

    // ── Restore purchases ─────────────────────────────────────────────────────

    fun restorePurchases() {
        if (_isPurchasing.value) return
        _isPurchasing.value = true

        scope.launch {
            val result = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            _isPurchasing.value = false

            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchases = result.purchasesList

                setAdFree(purchases.any {
                    it.products.contains(PRODUCT_REMOVE_ADS) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                })
                setPresetsUnlocked(purchases.any {
                    it.products.contains(PRODUCT_BPM_PRESETS) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                })
                setPracticeModeUnlocked(purchases.any {
                    it.products.contains(PRODUCT_PRACTICE_MODE) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                })
                setPurchasedSounds(purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { it.products }
                    .filter { it in SOUND_PRODUCTS }
                    .toSet()
                )
                setPurchasedItems(purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { it.products }
                    .filter { it in ITEM_PRODUCTS }
                    .toSet()
                )

                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .filter { !it.isAcknowledged }
                    .forEach { acknowledgePurchase(it) }
            }
        }
    }

    // ── Handle purchase callback ──────────────────────────────────────────────

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        when {
            purchase.products.contains(PRODUCT_REMOVE_ADS) ->
                setAdFree(true)
            purchase.products.contains(PRODUCT_BPM_PRESETS) ->
                setPresetsUnlocked(true)
            purchase.products.contains(PRODUCT_PRACTICE_MODE) ->
                setPracticeModeUnlocked(true)
            purchase.products.any { it in SOUND_PRODUCTS } ->
                setPurchasedSounds(_purchasedSoundIds.value +
                        purchase.products.filter { it in SOUND_PRODUCTS })
            purchase.products.any { it in ITEM_PRODUCTS } ->
                setPurchasedItems(_purchasedItemProductIds.value +
                        purchase.products.filter { it in ITEM_PRODUCTS })
            else -> return
        }
        if (!purchase.isAcknowledged) {
            scope.launch { acknowledgePurchase(purchase) }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun setAdFree(value: Boolean) {
        _isAdFree.value = value
        prefs.edit { putBoolean(KEY_AD_FREE, value) }
    }

    private fun setPresetsUnlocked(value: Boolean) {
        _isPresetsUnlocked.value = value
        prefs.edit { putBoolean(KEY_PRESETS_UNLOCKED, value) }
    }

    private fun setPracticeModeUnlocked(value: Boolean) {
        _isPracticeModeUnlocked.value = value
        prefs.edit { putBoolean(KEY_PRACTICE_MODE_UNLOCKED, value) }
    }

    private fun setPurchasedSounds(ids: Set<String>) {
        _purchasedSoundIds.value = ids
        prefs.edit { putStringSet(KEY_PURCHASED_SOUND_IDS, ids) }
    }

    private fun setPurchasedItems(ids: Set<String>) {
        _purchasedItemProductIds.value = ids
        prefs.edit { putStringSet(KEY_PURCHASED_ITEM_PRODUCT_IDS, ids) }
    }

    fun debugClearAdFree()          { setAdFree(false) }
    fun debugClearPresets()         { setPresetsUnlocked(false) }
    fun debugClearPracticeMode()    { setPracticeModeUnlocked(false) }
    fun debugClearSoundPurchases()  { setPurchasedSounds(emptySet()) }
    fun debugClearItemPurchases()   { setPurchasedItems(emptySet()) }

    fun release() {
        billingClient.endConnection()
        scope.cancel()
    }
}
