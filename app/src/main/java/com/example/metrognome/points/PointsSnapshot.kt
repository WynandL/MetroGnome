package com.example.metrognome.points

/**
 * One line item in the Beats ledger.
 * [rawValue] and [rawUnit] are for display only ("45 min", "8 games").
 */
data class PointsContribution(
    val label: String,
    val rawValue: Long,
    val rawUnit: String,
    val points: Int,
)

/**
 * Immutable snapshot of a user's current Beats score.
 *
 * [total] is always derived from [contributions] — never stored independently.
 * This mirrors the event-sourcing pattern used by banking-grade ledgers:
 * the authoritative record is the list of inputs, not a running total.
 * When a cloud sync is added, local and remote totals stay in sync by
 * recalculating from the same counters rather than merging separate totals.
 */
data class PointsSnapshot(
    val contributions: List<PointsContribution>,
) {
    val total: Int get() = contributions.sumOf { it.points }
}
