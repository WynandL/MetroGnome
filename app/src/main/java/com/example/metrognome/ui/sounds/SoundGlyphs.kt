package com.example.metrognome.ui.sounds

import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcons

/**
 * The mark for each click voice, keyed by [com.example.metrognome.billing.ClickSound.slug].
 * The glyphs live in AppIcons (generated, see tools/icons/gen_icons.py); this is only the
 * lookup, kept out of billing/ so the sound list stays free of UI. `SoundGlyphsTest` fails
 * if a voice in CLICK_SOUNDS has no entry here.
 */
val CLICK_SOUND_GLYPHS: Map<String, AppGlyph> = mapOf(
    "classic"      to AppIcons.SoundClassic,
    "hihat"        to AppIcons.SoundHiHat,
    "wood"         to AppIcons.SoundWood,
    "warm"         to AppIcons.SoundWarm,
    "bell"         to AppIcons.SoundBell,
    "crystal_bowl" to AppIcons.SoundCrystalBowl,
    "kalimba"      to AppIcons.SoundKalimba,
    "cowbell"      to AppIcons.SoundCowbell,
    "metal_kick"   to AppIcons.SoundMetalKick,
)

/** The voice's mark; the music note for a slug with none (the test keeps that from shipping). */
fun soundGlyphFor(slug: String): AppGlyph = CLICK_SOUND_GLYPHS[slug] ?: AppIcons.MusicNote
