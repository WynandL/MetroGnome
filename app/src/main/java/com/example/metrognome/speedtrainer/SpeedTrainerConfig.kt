package com.example.metrognome.speedtrainer

import kotlin.math.roundToInt

data class SpeedTrainerConfig(
    val startBpm: Int = 60,
    val targetBpm: Int = 120,
    val stepSize: Float = 5f,
    val incrementMode: IncrementMode = IncrementMode.FIXED,
    val barsPerStep: Int = 4,
    val repeatsPerStep: Int = 1,
    val micEnabled: Boolean = false,
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
}
