package com.example.metrognome.ui.meter

import kotlin.math.min

/**
 * Where the beats of a bar sit, as pure arithmetic, shared by the Home strip (a line) and the
 * meter stage's wheel (a circle). Pure Kotlin so it is tested on the JVM (`MeterLayoutTest`).
 *
 * The bar is drawn **grouped**: a small extra gap opens before every accented beat, so 7/8
 * reads 2+2+3 and 6/8 reads 3+3 at a glance, which a row of evenly spaced dots never said.
 * The groups come from the accents actually in use, not [com.example.metrognome.theory.MeterTheory]'s
 * defaults, so a player's own pattern shows as theirs.
 */
object MeterLayout {

    /** The extra gap before a group, as a fraction of the beat-to-beat pitch. */
    const val GROUP_GAP = 0.55f

    /** Most beats a bar can have (`MeterTheory.TOP_RANGE`). */
    const val MAX_BEATS = 16

    class Line(
        /** Centre of each beat, from the left edge of [width]. */
        val centres: FloatArray,
        /** Distance between neighbouring beats inside a group. */
        val pitch: Float,
        /** Width actually used: the first cell's left edge to the last cell's right edge. */
        val width: Float,
    )

    /** True where a group opens: an accent that is not the first beat. */
    private fun opensGroup(i: Int, accents: Set<Int>) = i > 0 && i in accents

    /**
     * [beats] laid out in a line no wider than [available] and no looser than [maxPitch].
     * Beats beyond [MAX_BEATS] or below 1 are clamped; accents outside the bar are ignored.
     */
    fun line(beats: Int, accents: Set<Int>, available: Float, maxPitch: Float, gap: Float = GROUP_GAP): Line {
        val n = beats.coerceIn(1, MAX_BEATS)
        val groups = (0 until n).count { opensGroup(it, accents) }
        // First centre to last centre, in pitches, plus one pitch for the two half cells.
        val span = (n - 1) + groups * gap + 1f
        val pitch = min(maxPitch, available / span).coerceAtLeast(0f)
        val centres = FloatArray(n)
        var x = pitch / 2f
        for (i in 0 until n) {
            if (opensGroup(i, accents)) x += gap * pitch
            centres[i] = x
            if (i < n - 1) x += pitch
        }
        return Line(centres, pitch, span * pitch)
    }

    /**
     * [beats] around a circle as turns from the top (0 = twelve o'clock, clockwise, 0 until 1).
     * The bar repeats, so when it has more than one group the gap before the next bar's
     * downbeat is counted too, and the wheel reads as groups all the way round. A bar of one
     * group (4/4) has no gaps at all, so its beats sit evenly (12, 3, 6 and 9 o'clock).
     */
    fun ring(beats: Int, accents: Set<Int>, gap: Float = GROUP_GAP): FloatArray {
        val n = beats.coerceIn(1, MAX_BEATS)
        val pos = FloatArray(n)
        var p = 0f
        for (i in 0 until n) {
            if (opensGroup(i, accents)) p += gap
            pos[i] = p
            p += 1f
        }
        val total = p + if (wrapGap(n, accents)) gap else 0f
        for (i in 0 until n) pos[i] /= total
        return pos
    }

    /** Pitch between beats on the ring, as turns (for sizing the gems to the circle). */
    fun ringPitch(beats: Int, accents: Set<Int>, gap: Float = GROUP_GAP): Float {
        val n = beats.coerceIn(1, MAX_BEATS)
        val groups = (0 until n).count { opensGroup(it, accents) } + if (wrapGap(n, accents)) 1 else 0
        return 1f / (n + groups * gap)
    }

    /** The gap back to the downbeat: only for a bar split into groups. */
    private fun wrapGap(n: Int, accents: Set<Int>) =
        n > 1 && (0 until n).any { opensGroup(it, accents) }
}
