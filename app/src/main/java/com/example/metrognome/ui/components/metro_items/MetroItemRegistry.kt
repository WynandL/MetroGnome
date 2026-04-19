package com.example.metrognome.ui.components.metro_items

import com.example.metrognome.ui.components.metro_items.items.GoldEarring
import com.example.metrognome.ui.components.metro_items.items.GlowingMushroom
import com.example.metrognome.ui.components.metro_items.items.LuxuryWatch
import com.example.metrognome.ui.components.metro_items.items.GoldChain

/**
 * Single source of truth for every cosmetic item and its unlock condition.
 *
 * Order matters for display in a future wardrobe screen (easiest → hardest).
 * Add new items here — nothing else needs to change to wire them in.
 *
 * Thresholds (metronome seconds):
 *   10 min  =   600 s
 *   30 min  = 1 800 s
 *   1 h     = 3 600 s
 *   3 h     = 10 800 s
 */
val METRO_ITEM_REGISTRY: List<MetroItemEntry> = listOf(

    MetroItemEntry(
        item = GoldEarring,
        condition = UnlockCondition.MetronomeSeconds(600)        // 10 minutes
    ),
    MetroItemEntry(
        item = GlowingMushroom,
        condition = UnlockCondition.RhythmGamesCompleted(5)      // 5 rhythm games finished
    ),
    MetroItemEntry(
        item = LuxuryWatch,
        condition = UnlockCondition.MetronomeSeconds(1_800)      // 30 minutes
    ),
    MetroItemEntry(
        item = GoldChain,
        condition = UnlockCondition.MetronomeSeconds(3_600)      // 1 hour
    ),

    // ── Future items — add entries here as new files are created ─────────────
    // MetroItemEntry(ArmBracelet,     UnlockCondition.MetronomeSeconds(7_200)),   // 2 h
    // MetroItemEntry(LapelPin,        UnlockCondition.MetronomeSeconds(10_800)),  // 3 h
    // MetroItemEntry(TieBar,          UnlockCondition.MetronomeSeconds(18_000)),  // 5 h
    // MetroItemEntry(PinkyRing,       UnlockCondition.MetronomeSeconds(36_000)),  // 10 h
    // MetroItemEntry(DiamondShades,   UnlockCondition.MetronomeSeconds(54_000)),  // 15 h
    // MetroItemEntry(WristTattoo,     UnlockCondition.MetronomeSeconds(72_000)),  // 20 h
    // MetroItemEntry(WalkingCane,     UnlockCondition.MetronomeSeconds(108_000)), // 30 h
    // MetroItemEntry(HatFeather,      UnlockCondition.MetronomeSeconds(144_000)), // 40 h
    // MetroItemEntry(CityBackdrop,    UnlockCondition.DaysSinceFirstLaunch(30)),  // 30 days
)
