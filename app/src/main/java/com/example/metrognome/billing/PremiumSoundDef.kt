package com.example.metrognome.billing

data class PremiumSoundDef(
    val soundTypeIndex: Int,
    val productId: String,
    val displayName: String,
    val description: String,
)

/**
 * The purchasable click sounds, derived from [CLICK_SOUNDS] (the one list of voices, where
 * the recipe for adding a sound now lives). Kept as its own view because billing and the
 * paywall only ever need the paid ones.
 */
val PREMIUM_SOUND_REGISTRY: List<PremiumSoundDef> = CLICK_SOUNDS.mapNotNull { s ->
    s.productId?.let { PremiumSoundDef(s.index, it, s.displayName, s.description) }
}
