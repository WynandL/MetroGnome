package com.example.metrognome.billing

data class PurchasableItemDef(
    val itemId: String,
    val productId: String,
    val displayName: String,
    val description: String,
    val unlockAlternative: String,
)

/**
 * Registry of all items that can be purchased outright.
 * Items here can also still be unlocked the normal way (time / games / days).
 * Purchase is just an instant override — both paths lead to the same unlocked state.
 *
 * To add a new purchasable item:
 *   1. Add PRODUCT_ITEM_* constant to BillingManager.companion
 *   2. Add it to BillingManager.ITEM_PRODUCTS
 *   3. Add an entry here
 *   4. Create the product in Play Console
 */
val PURCHASABLE_ITEM_REGISTRY: List<PurchasableItemDef> = listOf(
    PurchasableItemDef(
        itemId = "glissie_fairy",
        productId = BillingManager.PRODUCT_ITEM_GLISSIE,
        displayName = "Glissie",
        description = "A fairy companion who dances alongside Metro. She's been spinning glissandos in the background all along. Now she finally shows herself.",
        unlockAlternative = "or play the metronome for 6 hours"
    ),
    // Add new purchasable items here
)
