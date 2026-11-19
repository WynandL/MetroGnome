package com.example.metrognome.ui.sounds

import com.example.metrognome.billing.BillingManager
import com.example.metrognome.billing.CLICK_SOUNDS
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.ui.components.instruments.InstrumentAffinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one list of click voices and everything keyed off it stay in step. */
class SoundGlyphsTest {

    @Test fun everyVoiceHasAGlyph() {
        CLICK_SOUNDS.forEach { assertTrue("no glyph for ${it.slug}", it.slug in CLICK_SOUND_GLYPHS) }
        assertEquals(CLICK_SOUNDS.map { it.slug }.toSet(), CLICK_SOUND_GLYPHS.keys)
    }

    @Test fun indicesAndSlugsAreUniqueAndContiguous() {
        assertEquals(CLICK_SOUNDS.indices.toList(), CLICK_SOUNDS.map { it.index })
        assertEquals(CLICK_SOUNDS.size, CLICK_SOUNDS.map { it.slug }.toSet().size)
    }

    @Test fun everyPaidVoiceIsASoundProductAndFreeOnesComeFirst() {
        CLICK_SOUNDS.mapNotNull { it.productId }.forEach {
            assertTrue("$it not in SOUND_PRODUCTS", it in BillingManager.SOUND_PRODUCTS)
        }
        val firstPaid = CLICK_SOUNDS.indexOfFirst { it.isPremium }
        assertTrue(CLICK_SOUNDS.drop(firstPaid).all { it.isPremium })
        assertEquals(CLICK_SOUNDS.count { it.isPremium }, PREMIUM_SOUND_REGISTRY.size)
    }

    @Test fun everyVoiceSuitsSomeInstrument() {
        CLICK_SOUNDS.forEach {
            assertTrue("no instruments for ${it.slug}", InstrumentAffinity.instrumentsFor(it.index).isNotEmpty())
        }
    }

    @Test fun copyHasNoDashes() {
        CLICK_SOUNDS.forEach {
            val text = it.tagline + it.description
            assertTrue("dash in ${it.slug}", '—' !in text && '–' !in text)
        }
    }
}
