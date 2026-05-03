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
        const val PRODUCT_REMOVE_ADS = "remove_ads"
        private const val PREFS_NAME  = "billing_state"
        private const val KEY_AD_FREE = "ad_free"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Public state ──────────────────────────────────────────────────────────

    private val _isAdFree = MutableStateFlow(prefs.getBoolean(KEY_AD_FREE, false))
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _priceText = MutableStateFlow<String?>(null)
    val priceText: StateFlow<String?> = _priceText.asStateFlow()

    /** True while a purchase or restore query is in flight. */
    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    /**
     * False while billing is connecting on startup; true once the product is confirmed
     * to exist in Play Console (or permanently false if unavailable / not configured).
     */
    private val _isBillingAvailable = MutableStateFlow(false)
    val isBillingAvailable: StateFlow<Boolean> = _isBillingAvailable.asStateFlow()

    /**
     * True during the initial connection attempt before we know if billing is available.
     * Lets the UI show a neutral loading state instead of "Unavailable".
     */
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
                // Single reconnect attempt after a short delay
                scope.launch {
                    delay(3_000L)
                    if (!billingClient.isReady) connect()
                }
            }
        })
    }

    // ── Product details ───────────────────────────────────────────────────────

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val details = result.productDetailsList?.firstOrNull()
            _priceText.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            _isBillingAvailable.value = details != null
        }
    }

    // ── Purchase flow ─────────────────────────────────────────────────────────

    fun launchPurchaseFlow(activity: Activity) {
        if (_isPurchasing.value) return
        _isPurchasing.value = true

        scope.launch {
            val result = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_REMOVE_ADS)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()
            )

            val productDetails = result.productDetailsList?.firstOrNull()
            if (productDetails == null) {
                _isPurchasing.value = false
                return@launch
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()

            val billingResult = billingClient.launchBillingFlow(activity, flowParams)
            // If the flow couldn't even open, reset immediately — purchasesUpdatedListener
            // is only called when the Play sheet actually appears and closes.
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
                val purchased = result.purchasesList.any {
                    it.products.contains(PRODUCT_REMOVE_ADS) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                setAdFree(purchased)

                result.purchasesList
                    .filter { it.products.contains(PRODUCT_REMOVE_ADS) }
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .filter { !it.isAcknowledged }
                    .forEach { acknowledgePurchase(it) }
            }
        }
    }

    // ── Handle purchase callback ──────────────────────────────────────────────

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_REMOVE_ADS)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        setAdFree(true)
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

    private fun setAdFree(value: Boolean) {
        _isAdFree.value = value
        prefs.edit { putBoolean(KEY_AD_FREE, value) }
    }

    /** DEV: wipe the local ad-free cache so the UI reverts to showing ads. */
    fun debugClearAdFree() {
        setAdFree(false)
    }

    fun release() {
        billingClient.endConnection()
        scope.cancel()
    }
}
