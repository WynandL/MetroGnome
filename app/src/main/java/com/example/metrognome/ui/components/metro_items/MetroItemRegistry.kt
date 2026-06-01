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
import com.example.metrognome.ui.components.metro_items.items.GlissieFairy
import com.example.metrognome.ui.components.metro_items.items.MusicStand
import com.example.metrognome.ui.components.metro_items.items.TuningFork
import com.example.metrognome.ui.components.metro_items.items.HatFeather

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
 *    6 h    =  21 600 s
 *   10 h    =  36 000 s
 *
 * Practice sessions (PracticeSessionsCompleted):
 *   Counts unique days practised (not raw session count — one session per day).
 *   Only available to users who purchased Practice Mode.
 *   Safe for permanent unlocks — total never decreases.
 *   Do NOT use PracticeStreakDays as an unlock condition: a broken streak hides earned items.
 */
val METRO_ITEM_REGISTRY: List<MetroItemEntry> = listOf(

    // ── Wearables ─────────────────────────────────────────────────────────────
    MetroItemEntry(GoldEarring,      UnlockCondition.MetronomeSeconds(300)),        // 5 min
    MetroItemEntry(LuxuryWatch,      UnlockCondition.MetronomeSeconds(1_800)),      // 30 min
    MetroItemEntry(GoldChain,        UnlockCondition.MetronomeSeconds(3_600)),      // 1 h

    // ── Forest progression — days since first launch (draw order: back → front) ─
    MetroItemEntry(ForestTree,         UnlockCondition.DaysSinceFirstLaunch(30)),   // 1 month  — drawn first (farthest back)
    MetroItemEntry(TorchPost,          UnlockCondition.RhythmGamesCompleted(15)),   // 15 games — behind mushroom and flowers
    MetroItemEntry(GlowingMushroom,    UnlockCondition.RhythmGamesCompleted(5)),    // 5 games  — behind flowers
    MetroItemEntry(ForestFloorFlowers, UnlockCondition.DaysSinceFirstLaunch(3)),    // 3 days   — in front of mushrooms

    // ── Sky / atmosphere — long play-time (draw order: back → front) ─────────
    MetroItemEntry(Fireflies,          UnlockCondition.MetronomeSeconds(10_800)),   // 3 h  — farthest back
    MetroItemEntry(MoonAndStars,       UnlockCondition.MetronomeSeconds(36_000)),   // 10 h
    MetroItemEntry(GlissieFairy,        UnlockCondition.MetronomeSeconds(21_600)),   // 6 h — drawn last, floats closest

    // ── Practice rewards — require Practice Mode IAP to earn ─────────────────
    // Earn-only (no purchase path): these reward consistent practice, not spending.
    MetroItemEntry(MusicStand, UnlockCondition.PracticeSessionsCompleted(5)),  // 5 sessions

    // ── Tuner feedback reward ─────────────────────────────────────────────────
    // Earned by submitting a single thumbs-up reading via the tuner feedback card.
    MetroItemEntry(TuningFork, UnlockCondition.TunerFeedbackGiven(1)),

    // ── Tuner rewards — TunerSeconds accumulates from v3.3 onwards ────────────
    // Thresholds (tuner seconds — mic actively open):
    //   5 min  =     300 s
    //   15 min =     900 s
    //   1 h    =   3 600 s
    //   3 h    =  10 800 s
    MetroItemEntry(HatFeather, UnlockCondition.TunerSeconds(900)),  // 15 min — head-attached plume

    // ── Speed Trainer rewards — unlocked by completing sessions ───────────────
    // Earn-only: rewards consistent structured practice. No purchase path.
    // Thresholds: 1 session = first time, 5 = habit-forming, 10 = dedicated
    // MetroItemEntry(SpeedRacerBadge, UnlockCondition.SpeedTrainingSessionsCompleted(1)),   // first session ever
    // MetroItemEntry(SpeedBoots,      UnlockCondition.SpeedTrainingSessionsCompleted(5)),   // 5 sessions
    // MetroItemEntry(SpeedHalo,       UnlockCondition.SpeedTrainingSessionsCompleted(10)),  // 10 sessions

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
