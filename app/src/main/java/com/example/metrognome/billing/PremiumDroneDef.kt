package com.example.metrognome.billing

import com.example.metrognome.audio.drone.DroneBlend
import com.example.metrognome.audio.drone.DroneTimbre

/**
 * Which drone voices are paid, and how they are sold.
 *
 * The counterpart of [PREMIUM_SOUND_REGISTRY] for the tuner's drone. Kept here rather than
 * on the enums themselves so `audio/drone/` stays free of any billing knowledge: the
 * synthesis has no opinion about what costs money, and the paywall has no opinion about how
 * a tone is built. A timbre or blend absent from these maps is free.
 *
 * To add a paid drone voice:
 *   1. Add a PRODUCT_DRONE_* constant to BillingManager's companion
 *   2. Add it to BillingManager.SOUND_PRODUCTS (the shared "things you buy that make a
 *      sound" bucket, so it prices, reconciles and restores with everything else)
 *   3. Add the timbre or blend itself to DroneTimbre / DroneBlend
 *   4. Add an entry below
 *   5. Create the product in Play Console
 *
 * The [description] is the dialog's sales copy and has room to explain what the voice is
 * for. It is not the chip caption: that lives on the enum, is much shorter, and follows the
 * no-synthesis-vocabulary rule documented on `DroneTimbre.caption`.
 */
data class PremiumDroneDef(
    val productId: String,
    val description: String,
)

/** Paid timbres. Pure and Warm stay free, so the drone is fully usable without paying. */
val PREMIUM_DRONE_TIMBRES: Map<DroneTimbre, PremiumDroneDef> = mapOf(
    DroneTimbre.REED to PremiumDroneDef(
        productId = BillingManager.PRODUCT_DRONE_REED,
        description = "A bright, reedy drone, like an accordion holding one note. " +
                "The extra edge in the sound gives you far more to listen against, so a note " +
                "that is slightly out announces itself instead of slipping past.",
    ),
    DroneTimbre.STRING to PremiumDroneDef(
        productId = BillingManager.PRODUCT_DRONE_STRING,
        description = "A bowed string, full and dense, the sound an orchestra tunes to. " +
                "The richest voice in the set and the kindest to play along with for a whole " +
                "session without tiring your ear.",
    ),
)

/** Paid blends. Root and Octave stay free. */
val PREMIUM_DRONE_BLENDS: Map<DroneBlend, PremiumDroneDef> = mapOf(
    DroneBlend.FIFTH to PremiumDroneDef(
        productId = BillingManager.PRODUCT_DRONE_FIFTH,
        description = "Sounds the note together with a perfect fifth above it, tuned pure " +
                "rather than to the piano's compromise. This is the drone string and wind " +
                "players have always used: when your own fifth is right, the wavering stops " +
                "completely and you can hear it happen.",
    ),
)

/** The product a timbre needs, or null if it is free. */
fun premiumProductFor(timbre: DroneTimbre): String? = PREMIUM_DRONE_TIMBRES[timbre]?.productId

/** The product a blend needs, or null if it is free. */
fun premiumProductFor(blend: DroneBlend): String? = PREMIUM_DRONE_BLENDS[blend]?.productId

/** Every product id the drone can sell, for the entitlement guard in TunerViewModel. */
val DRONE_PRODUCT_IDS: Set<String> =
    (PREMIUM_DRONE_TIMBRES.values + PREMIUM_DRONE_BLENDS.values).map { it.productId }.toSet()

/** The free fallback a locked timbre reverts to when it turns out not to be owned. */
val FREE_DRONE_TIMBRE: DroneTimbre = DroneTimbre.WARM

/** The free fallback a locked blend reverts to when it turns out not to be owned. */
val FREE_DRONE_BLEND: DroneBlend = DroneBlend.ROOT
