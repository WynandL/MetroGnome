package com.example.metrognome.billing

data class PremiumSoundDef(
    val soundTypeIndex: Int,
    val productId: String,
    val displayName: String,
    val description: String,
)

/**
 * Registry of all purchasable sounds.
 * To add a new premium sound:
 *   1. Add a PRODUCT_SOUND_* constant to BillingManager companion
 *   2. Add it to BillingManager.SOUND_PRODUCTS
 *   3. Add buffers + a case in MetronomeEngine.buildBeatBuffer / buildPreviewBuffer
 *   4. Add an entry here
 *   5. Create the product in Play Console
 */
val PREMIUM_SOUND_REGISTRY: List<PremiumSoundDef> = listOf(
    PremiumSoundDef(
        soundTypeIndex = 4,
        productId = BillingManager.PRODUCT_SOUND_BELL,
        displayName = "Bell",
        description = "A clear bell strike with natural harmonic overtones. " +
                "Bright and distinct. Sits above the mix without being sharp."
    ),
    PremiumSoundDef(
        soundTypeIndex = 5,
        productId = BillingManager.PRODUCT_SOUND_BOWL,
        displayName = "Crystal Bowl",
        description = "A crystal singing bowl strike - warm, resonant harmonics with a gentle shimmer. " +
                "Deep and meditative. The kind of sound you could practise to for hours."
    ),
    PremiumSoundDef(
        soundTypeIndex = 6,
        productId = BillingManager.PRODUCT_SOUND_KALIMBA,
        displayName = "Kalimba",
        description = "A kalimba thumb-piano pluck, warm wooden tines with a soft, bright shimmer. " +
                "Gentle and grounding, the kind of calm pulse you could practise to all day."
    ),
    PremiumSoundDef(
        soundTypeIndex = 7,
        productId = BillingManager.PRODUCT_SOUND_COWBELL,
        displayName = "Cowbell",
        description = "A bright, metallic cowbell built to cut through a loud kit. " +
                "Punchy and unmistakable, the click drummers can actually hear over the cymbals."
    ),
    // Add new premium sounds here
)
