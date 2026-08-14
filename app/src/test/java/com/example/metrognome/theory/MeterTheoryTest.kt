package com.example.metrognome.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MeterTheory] — verifies classification, grouping, default accents,
 * and labels against textbook music theory for the common and odd meters the app exposes.
 */
class MeterTheoryTest {

    private fun m(top: Int, bottom: Int) = Meter(top, bottom)

    // --- classification ---

    @Test
    fun simpleMetersClassifyAsSimple() {
        listOf(m(2, 4), m(3, 4), m(4, 4), m(3, 8), m(2, 2)).forEach {
            assertEquals("$it should be simple", MeterClass.SIMPLE, MeterTheory.classify(it))
        }
    }

    @Test
    fun compoundMetersClassifyAsCompound() {
        listOf(m(6, 8), m(9, 8), m(12, 8), m(6, 4)).forEach {
            assertEquals("$it should be compound", MeterClass.COMPOUND, MeterTheory.classify(it))
        }
    }

    @Test
    fun oddMetersClassifyAsIrregular() {
        listOf(m(5, 4), m(7, 8), m(7, 4), m(5, 8), m(11, 8)).forEach {
            assertEquals("$it should be irregular", MeterClass.IRREGULAR, MeterTheory.classify(it))
        }
    }

    @Test
    fun denominatorDoesNotChangeClass() {
        // 3/8 and 3/4 are both simple; 6/8 and 6/4 are both compound.
        assertEquals(MeterTheory.classify(m(3, 8)), MeterTheory.classify(m(3, 4)))
        assertEquals(MeterTheory.classify(m(6, 8)), MeterTheory.classify(m(6, 4)))
    }

    // --- grouping ---

    @Test
    fun groupingSumsToTop() {
        for (top in MeterTheory.TOP_RANGE) {
            val g = MeterTheory.beatGrouping(m(top, 8))
            assertEquals("grouping of $top/8 must sum to $top", top.coerceAtLeast(1), g.sum())
        }
    }

    @Test
    fun compoundGroupsInThrees() {
        assertEquals(listOf(3, 3), MeterTheory.beatGrouping(m(6, 8)))
        assertEquals(listOf(3, 3, 3), MeterTheory.beatGrouping(m(9, 8)))
        assertEquals(listOf(3, 3, 3, 3), MeterTheory.beatGrouping(m(12, 8)))
    }

    @Test
    fun irregularGroupsTrailingThree() {
        assertEquals(listOf(2, 3), MeterTheory.beatGrouping(m(5, 4)))
        assertEquals(listOf(2, 2, 3), MeterTheory.beatGrouping(m(7, 8)))
        assertEquals(listOf(2, 2, 2, 2), MeterTheory.beatGrouping(m(8, 8)))
    }

    @Test
    fun simpleMeterIsOneGroup() {
        assertEquals(listOf(4), MeterTheory.beatGrouping(m(4, 4)))
        assertEquals(listOf(3), MeterTheory.beatGrouping(m(3, 4)))
    }

    // --- accents ---

    @Test
    fun simpleAccentsOnlyDownbeat() {
        assertEquals(setOf(0), MeterTheory.defaultAccents(m(4, 4)))
        assertEquals(setOf(0), MeterTheory.defaultAccents(m(2, 4)))
    }

    @Test
    fun compoundAccentsOnGroupStarts() {
        assertEquals(setOf(0, 3), MeterTheory.defaultAccents(m(6, 8)))
        assertEquals(setOf(0, 3, 6), MeterTheory.defaultAccents(m(9, 8)))
    }

    @Test
    fun irregularAccentsFollowGrouping() {
        assertEquals(setOf(0, 2, 4), MeterTheory.defaultAccents(m(7, 8)))   // 2+2+3
        assertEquals(setOf(0, 2), MeterTheory.defaultAccents(m(5, 4)))      // 2+3
    }

    @Test
    fun accentsAreWithinTheBar() {
        for (top in MeterTheory.TOP_RANGE) {
            MeterTheory.defaultAccents(m(top, 8)).forEach {
                assertTrue("accent index $it out of range for $top", it in 0 until top.coerceAtLeast(1))
            }
        }
    }

    // --- labels ---

    @Test
    fun labelsReadCorrectly() {
        assertEquals("Simple quadruple", MeterTheory.label(m(4, 4)))
        assertEquals("Simple triple", MeterTheory.label(m(3, 4)))
        assertEquals("Compound duple", MeterTheory.label(m(6, 8)))
        assertEquals("Compound quadruple", MeterTheory.label(m(12, 8)))
        assertEquals("Odd", MeterTheory.label(m(7, 8)))
        assertEquals("Odd", MeterTheory.label(m(5, 4)))
        assertEquals("Odd", MeterTheory.label(m(11, 8)))
        // 8, 10, 14, 16 are irregular (neither simple nor compound) but NOT odd numbers -
        // must not be mislabeled "Odd", a factual music-theory error a musician would catch.
        assertEquals("Irregular", MeterTheory.label(m(8, 8)))
        assertEquals("Irregular", MeterTheory.label(m(10, 8)))
    }

    @Test
    fun descriptionsReadCorrectly() {
        assertEquals("Felt in 4, beats split in two", MeterTheory.description(m(4, 4)))
        assertEquals("Felt in 2, beats split in three", MeterTheory.description(m(6, 8)))
        assertEquals("Felt in 2+2+3", MeterTheory.description(m(7, 8)))
    }

    @Test
    fun commonMetersAreFlaggedCommon() {
        assertTrue(MeterTheory.isCommon(m(4, 4)))
        assertTrue(MeterTheory.isCommon(m(6, 8)))
        assertFalse(MeterTheory.isCommon(m(11, 8)))
    }
}
