package com.example.metrognome.speedtrainer

import kotlin.math.roundToInt

data class SpeedTrainerConfig(
    val startBpm: Int = 60,
    val targetBpm: Int = 120,
    val stepSize: Float = 5f,
    val incrementMode: IncrementMode = IncrementMode.FIXED,
    val barsPerStep: Int = 4,
    val repeatsPerStep: Int = 1,
    val autoAdvanceWindowMs: Int = 30,
) {
    enum class IncrementMode { FIXED, PERCENT }

    val totalBarsPerStep: Int get() = barsPerStep * repeatsPerStep
    val ascending: Boolean get() = startBpm < targetBpm

    fun stepsSequence(): List<Int> {
        val steps = mutableListOf<Int>()
        var current = startBpm.toDouble()
        while (true) {
            val snapped = current.roundToInt().coerceIn(20, 300)
            // Stop if we looped back (can happen with floating-point drift)
            if (steps.isNotEmpty()) {
                if (ascending && snapped <= steps.last()) break
                if (!ascending && snapped >= steps.last()) break
            }
            // Snap any overshoot to exactly targetBpm so it's always the final step
            if (ascending && snapped >= targetBpm) { steps.add(targetBpm.coerceIn(20, 300)); break }
            if (!ascending && snapped <= targetBpm) { steps.add(targetBpm.coerceIn(20, 300)); break }
            steps.add(snapped)
            current = when (incrementMode) {
                IncrementMode.FIXED   -> if (ascending) current + stepSize else current - stepSize
                IncrementMode.PERCENT -> if (ascending) current * (1.0 + stepSize / 100.0)
                                         else           current * (1.0 - stepSize / 100.0)
            }
        }
        if (steps.isEmpty()) steps.add(targetBpm.coerceIn(20, 300))
        return steps.distinct()
    }

    fun stepSizeLabel(): String = when (incrementMode) {
        IncrementMode.FIXED -> "+${stepSize.roundToInt()} BPM"
        IncrementMode.PERCENT -> "+${stepSize}%"
    }

    /**
     * Rough wall-clock length of the whole session for [timeSig], in seconds.
     *
     * Both increment modes share this: [stepsSequence] has already resolved FIXED vs
     * PERCENT into the concrete list of tempos, so the duration is just the sum of each
     * step's bars at that step's tempo, plus the count-in. A bar at B BPM in t/4 lasts
     * t * 60 / B seconds; each step plays [totalBarsPerStep] bars.
     */
    fun estimatedDurationSeconds(timeSig: Int): Int {
        val steps = stepsSequence()
        if (steps.isEmpty()) return 0
        val playing = steps.sumOf { bpm -> totalBarsPerStep * timeSig * 60.0 / bpm }
        // Count-in before the first step: timeSig * 2 + 1 beats (matches beginSession).
        val countIn = (timeSig * 2 + 1) * 60.0 / steps.first()
        return (playing + countIn).roundToInt()
    }
}
