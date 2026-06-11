package com.example.metrognome.points.rewards

import com.example.metrognome.points.EARN_RULES

/**
 * Constants for the Gnotes reward system.
 *
 * [MAX_DAILY_BEATS] is derived live from [EARN_RULES] so it updates automatically
 * when earn rates or caps change — no manual sync needed.
 * [AD_FREE_DAYS] is the only number to change here when adjusting the reward.
 */
object RewardConfig {
    const val AD_FREE_DAYS: Int = 3
    val MAX_DAILY_BEATS: Int get() = EARN_RULES.sumOf { it.maxPerDay }
}
