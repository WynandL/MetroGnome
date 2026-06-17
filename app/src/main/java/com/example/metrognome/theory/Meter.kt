package com.example.metrognome.theory

/**
 * Single source of truth for time-signature music theory.
 *
 * Pure Kotlin (no Android dependencies) so it can be unit-tested directly and shared by
 * the metronome engine, the ViewModel, and the Settings UI without duplicating rules.
 *
 * Design notes:
 *  - Classification (simple / compound / irregular) depends only on the top number. That
 *    is the globally agreed structural rule: the bottom number is notational (which note
 *    value carries the pulse) and never changes the class. So 3/8 and 3/4 are both simple,
 *    6/8 and 6/4 are both compound.
 *  - The metronome clicks [Meter.top] pulses per bar (one click per counted unit), which
 *    matches the engine's existing behaviour. defaultAccents says which of those pulses
 *    are accented, and that set is derived from beatGrouping so the same grouping that
 *    names the meter also drives the sound.
 *  - Irregular groupings are a sensible default, not gospel. The Settings UI lets the user
 *    override accents per beat, which is exactly why we do not try to pick the "one true"
 *    grouping for odd meters (a contested topic with no global consensus).
 */
data class Meter(val top: Int, val bottom: Int) {
    /** "7/8" style label for display. */
    val display: String get() = "$top/$bottom"
}

enum class MeterClass { SIMPLE, COMPOUND, IRREGULAR }

object MeterTheory {

    /** Bottom numbers that make musical sense (note values that are powers of two). */
    val SENSIBLE_DENOMINATORS = listOf(2, 4, 8, 16)

    /** Allowed range for the top number in the picker. */
    val TOP_RANGE = 1..16

    /** Everyday meters offered as one-tap presets, ordered slow/simple to fast/odd. */
    val COMMON_METERS = listOf(
        Meter(2, 4), Meter(3, 4), Meter(4, 4),
        Meter(3, 8), Meter(6, 8), Meter(9, 8), Meter(12, 8),
        Meter(5, 4), Meter(7, 8),
    )

    /**
     * Simple (2,3,4 beats), compound (multiples of 3 above 3: 6,9,12,15), or irregular
     * (everything else: 5,7,8,10,11...). A 1-beat bar is treated as simple.
     */
    fun classify(m: Meter): MeterClass = when {
        m.top <= 1        -> MeterClass.SIMPLE
        m.top in 2..4     -> MeterClass.SIMPLE
        m.top % 3 == 0    -> MeterClass.COMPOUND   // top >= 5 here, so 6, 9, 12, 15...
        else              -> MeterClass.IRREGULAR  // 5, 7, 8, 10, 11, 13, 14, 16...
    }

    /**
     * Group lengths in pulses, summing to [Meter.top]. The start of each group is a natural
     * accent (see [defaultAccents]).
     *  - Simple: one group spanning the bar, so only the downbeat is accented.
     *  - Compound: groups of three (6/8 -> [3,3], 9/8 -> [3,3,3]).
     *  - Irregular: twos with a trailing three absorbing any odd pulse (5 -> [2,3], 7 -> [2,2,3]).
     */
    fun beatGrouping(m: Meter): List<Int> = when (classify(m)) {
        MeterClass.SIMPLE   -> listOf(m.top.coerceAtLeast(1))
        MeterClass.COMPOUND -> List(m.top / 3) { 3 }
        MeterClass.IRREGULAR -> irregularGroups(m.top)
    }

    /** Zero-based pulse indices that fall on a group boundary, i.e. the default accents. */
    fun defaultAccents(m: Meter): Set<Int> {
        val accents = sortedSetOf<Int>()
        var index = 0
        for (group in beatGrouping(m)) {
            accents.add(index)
            index += group
        }
        return accents
    }

    /** "Simple quadruple", "Compound duple", "Irregular". */
    fun label(m: Meter): String = when (classify(m)) {
        MeterClass.SIMPLE    -> "Simple" + countWord(m.top).orEmptyPrefixed()
        MeterClass.COMPOUND  -> "Compound" + countWord(m.top / 3).orEmptyPrefixed()
        MeterClass.IRREGULAR -> "Irregular"
    }

    /** Plain-English feel, e.g. "Felt in 2, beats split in three" or "Felt in 2+2+3". */
    fun description(m: Meter): String = when (classify(m)) {
        MeterClass.SIMPLE    -> "Felt in ${m.top.coerceAtLeast(1)}, beats split in two"
        MeterClass.COMPOUND  -> "Felt in ${m.top / 3}, beats split in three"
        MeterClass.IRREGULAR -> "Felt in ${beatGrouping(m).joinToString("+")}"
    }

    /** True for the everyday meters surfaced as quick-pick presets. */
    fun isCommon(m: Meter): Boolean = m in COMMON_METERS

    // --- internals ---

    private fun irregularGroups(n: Int): List<Int> {
        if (n <= 0) return listOf(1)
        val groups = mutableListOf<Int>()
        var remaining = n
        while (remaining >= 2) {
            groups.add(2)
            remaining -= 2
        }
        if (remaining == 1) {
            if (groups.isNotEmpty()) groups[groups.lastIndex] = 3  // 2 + leftover 1 -> 3
            else groups.add(1)
        }
        return groups
    }

    private fun countWord(beats: Int): String? = when (beats) {
        2 -> "duple"
        3 -> "triple"
        4 -> "quadruple"
        5 -> "quintuple"
        6 -> "sextuple"
        else -> null
    }

    private fun String?.orEmptyPrefixed(): String = if (this == null) "" else " $this"
}
