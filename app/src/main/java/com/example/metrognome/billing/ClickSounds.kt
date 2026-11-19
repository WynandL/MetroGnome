package com.example.metrognome.billing

/**
 * One metronome click voice, free or paid. [index] is the engine's `soundType` (see
 * `MetronomeEngine.buildBeatBuffer`), [productId] is null for a free voice.
 *
 * [slug] is the stable name analytics sends as `sound_type` (a registered dimension; the
 * number it used to send read as "(not set)" in reports) and the key the UI looks a glyph
 * up by. Never rename a slug: reports group by it across versions.
 *
 * [tagline] is one short line for places that show several sounds at once (the sound
 * stage); [description] is the paywall's longer copy. Neither may sell a premium voice by
 * calling a free one weak.
 */
data class ClickSound(
    val index: Int,
    val slug: String,
    val displayName: String,
    val tagline: String,
    val description: String,
    val productId: String? = null,
) {
    val isPremium: Boolean get() = productId != null
}

/**
 * Every click voice, in the order the app shows them: the four free ones, then the paid.
 * The single list of sounds: Settings, the sound stage, analytics and
 * [PREMIUM_SOUND_REGISTRY] all read it, so a voice is named in exactly one place.
 *
 * To add a premium sound:
 *   1. Add a PRODUCT_SOUND_* constant to BillingManager's companion and to SOUND_PRODUCTS
 *   2. Add buffers + a case in MetronomeEngine.buildBeatBuffer / buildPreviewBuffer, and a
 *      test of the MetalKickTest shape with its own bars
 *   3. Add an entry here (next index, a new slug), and its instruments in InstrumentAffinity
 *   4. Add its glyph in ui/sounds/SoundGlyphs.kt (the build fails until you do)
 *   5. Create the product in Play Console
 */
val CLICK_SOUNDS: List<ClickSound> = listOf(
    ClickSound(
        index = 0, slug = "classic", displayName = "Classic",
        tagline = "Crisp and bright. Heard over anything.",
        description = "The classic metronome click, with a higher note on the accent. " +
                "Clean and exact, and the one Groove Check listens past while you clap.",
    ),
    ClickSound(
        index = 1, slug = "hihat", displayName = "Hi-Hat",
        tagline = "A tight, airy tick.",
        description = "A short, high tick in the spirit of a closed hi-hat. " +
                "Light on the ear, easy to play around.",
    ),
    ClickSound(
        index = 2, slug = "wood", displayName = "Wood",
        tagline = "A dry woodblock knock.",
        description = "A dry, wooden knock with a brighter knock on the accent. " +
                "The sound of a practice-room metronome.",
    ),
    ClickSound(
        index = 3, slug = "warm", displayName = "Warm",
        tagline = "A soft, rounded pulse.",
        description = "A low, rounded pulse that sits under the music rather than over it.",
    ),
    ClickSound(
        index = 4, slug = "bell", displayName = "Bell",
        tagline = "Clear, with ringing overtones.",
        description = "A clear bell strike with natural harmonic overtones. " +
                "Bright and distinct. Sits above the mix without being sharp.",
        productId = BillingManager.PRODUCT_SOUND_BELL,
    ),
    ClickSound(
        index = 5, slug = "crystal_bowl", displayName = "Crystal Bowl",
        tagline = "Warm, resonant, meditative.",
        description = "A crystal singing bowl strike, warm resonant harmonics with a gentle shimmer. " +
                "Deep and meditative. The kind of sound you could practise to for hours.",
        productId = BillingManager.PRODUCT_SOUND_BOWL,
    ),
    ClickSound(
        index = 6, slug = "kalimba", displayName = "Kalimba",
        tagline = "Wooden tines, soft shimmer.",
        description = "A kalimba thumb-piano pluck, warm wooden tines with a soft, bright shimmer. " +
                "Gentle and grounding, the kind of calm pulse you could practise to all day.",
        productId = BillingManager.PRODUCT_SOUND_KALIMBA,
    ),
    ClickSound(
        index = 7, slug = "cowbell", displayName = "Cowbell",
        tagline = "Punchy. Heard over a full kit.",
        description = "A bright, metallic cowbell built to cut through a loud kit. " +
                "Punchy and unmistakable, the click drummers can actually hear over the cymbals.",
        productId = BillingManager.PRODUCT_SOUND_COWBELL,
    ),
    ClickSound(
        index = 8, slug = "metal_kick", displayName = "Metal Kick",
        tagline = "A deep, driving thump.",
        // The shipped voice is a saturated low body with a soft beater and only a hint of
        // room; the "big gated room" this copy used to promise was an earlier cut that was
        // rejected for reading as reverb (see CLAUDE.md, the Metal Kick).
        description = "A deep, heavy-metal kick drum: a big saturated low body, a soft beater " +
                "and a hint of room, voiced to thump even through a phone speaker. " +
                "The pulse that drives a wall of guitars.",
        productId = BillingManager.PRODUCT_SOUND_METAL_KICK,
    ),
)

/** The voice the engine plays for [index]; Classic for anything unknown, as the engine does. */
fun clickSoundFor(index: Int): ClickSound = CLICK_SOUNDS.firstOrNull { it.index == index } ?: CLICK_SOUNDS[0]
