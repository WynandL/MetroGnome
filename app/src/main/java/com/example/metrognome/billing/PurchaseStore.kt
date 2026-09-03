package com.example.metrognome.billing

/**
 * A read-only snapshot of the store, for a screen that sells something but does not own the
 * [BillingManager].
 *
 * The Settings screen reads the manager's flows straight off `MetronomeViewModel`, which
 * owns the one billing connection. The Tuner screen sells drone voices from a different
 * ViewModel, and the answer to that is emphatically not a second `BillingManager`: one
 * connection per process is the whole point of that class. So the values are gathered once
 * where they live and handed down as this, which keeps the number of parameters on
 * `TunerScreen` to one and makes it obvious that the screen is a consumer of purchase
 * state, never a source of it.
 */
data class PurchaseStore(
    val purchasedProductIds: Set<String> = emptySet(),
    val prices: Map<String, String?> = emptyMap(),
    val availableProductIds: Set<String> = emptySet(),
    val isPurchasing: Boolean = false,
    val isConnecting: Boolean = false,
) {
    fun owns(productId: String): Boolean = productId in purchasedProductIds

    fun priceOf(productId: String): String? = prices[productId]

    /** True once Play has confirmed the product exists and can be bought. */
    fun isAvailable(productId: String): Boolean = productId in availableProductIds
}
