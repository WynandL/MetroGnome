package com.example.metrognome.ui.components.metro_items

import com.example.metrognome.ui.components.metro_items.items.GoldEarring
import com.example.metrognome.ui.components.metro_items.items.GlowingMushroom
import com.example.metrognome.ui.components.metro_items.items.LuxuryWatch
import com.example.metrognome.ui.components.metro_items.items.GoldChain
import com.example.metrognome.ui.components.metro_items.items.ForestFloorFlowers
import com.example.metrognome.ui.components.metro_items.items.ForestTree
import com.example.metrognome.ui.components.metro_items.items.TorchPost
import com.example.metrognome.ui.components.metro_items.items.Fireflies
import com.example.metrognome.ui.components.metro_items.items.MoonAndStars

/**
 * Single source of truth for every cosmetic item and its unlock condition.
 *
 * Order matters for display in a future wardrobe screen (easiest → hardest).
 * Add new items here — nothing else needs to change to wire them in.
 *
 * Thresholds (metronome seconds):
 *   10 min  =     600 s
 *   30 min  =   1 800 s
 *    1 h    =   3 600 s
 *    3 h    =  10 800 s
 *   10 h    =  36 000 s
 */
val METRO_ITEM_REGISTRY: List<MetroItemEntry> = listOf(

    // ── Wearables ─────────────────────────────────────────────────────────────
    MetroItemEntry(GoldEarring,      UnlockCondition.MetronomeSeconds(600)),        // 10 min
    MetroItemEntry(LuxuryWatch,      UnlockCondition.MetronomeSeconds(1_800)),      // 30 min
    MetroItemEntry(GoldChain,        UnlockCondition.MetronomeSeconds(3_600)),      // 1 h

    // ── Forest progression — days since first launch (draw order: back → front) ─
    MetroItemEntry(ForestTree,         UnlockCondition.DaysSinceFirstLaunch(30)),   // 1 month  — drawn first (farthest back)
    MetroItemEntry(TorchPost,          UnlockCondition.RhythmGamesCompleted(15)),   // 15 games — behind mushroom and flowers
    MetroItemEntry(GlowingMushroom,    UnlockCondition.RhythmGamesCompleted(5)),    // 5 games  — behind flowers
    MetroItemEntry(ForestFloorFlowers, UnlockCondition.DaysSinceFirstLaunch(3)),    // 3 days   — in front of mushrooms

    // ── Sky / atmosphere — long play-time ─────────────────────────────────────
    MetroItemEntry(Fireflies,          UnlockCondition.MetronomeSeconds(10_800)),   // 3 h
    MetroItemEntry(MoonAndStars,       UnlockCondition.MetronomeSeconds(36_000)),   // 10 h

    // ── Future wearables — add entries here as new files are created ──────────
    // MetroItemEntry(ArmBracelet,   UnlockCondition.MetronomeSeconds(7_200)),    // 2 h
    // MetroItemEntry(LapelPin,      UnlockCondition.MetronomeSeconds(10_800)),   // 3 h
    // MetroItemEntry(TieBar,        UnlockCondition.MetronomeSeconds(18_000)),   // 5 h
    // MetroItemEntry(PinkyRing,     UnlockCondition.MetronomeSeconds(36_000)),   // 10 h
    // MetroItemEntry(DiamondShades, UnlockCondition.MetronomeSeconds(54_000)),   // 15 h
    // MetroItemEntry(WristTattoo,   UnlockCondition.MetronomeSeconds(72_000)),   // 20 h
    // MetroItemEntry(WalkingCane,   UnlockCondition.MetronomeSeconds(108_000)),  // 30 h
    // MetroItemEntry(HatFeather,    UnlockCondition.MetronomeSeconds(144_000)),  // 40 h
    // ── Future forest / companions ────────────────────────────────────────────
    // MetroItemEntry(Bambi,         UnlockCondition.DaysSinceFirstLaunch(7)),    // 1 week
    // MetroItemEntry(Butterfly,     UnlockCondition.DaysSinceFirstLaunch(14)),
    // MetroItemEntry(Squirrel,      UnlockCondition.DaysSinceFirstLaunch(21)),
    // MetroItemEntry(OwlOnHat,      UnlockCondition.DaysSinceFirstLaunch(30)),   // isHeadAttached = true
    // MetroItemEntry(FoxCub,        UnlockCondition.DaysSinceFirstLaunch(45)),
    // MetroItemEntry(BirthdayHat,   UnlockCondition.DaysSinceFirstLaunch(365)),  // 1 year
)
