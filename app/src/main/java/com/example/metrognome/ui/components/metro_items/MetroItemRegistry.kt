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
import com.example.metrognome.ui.components.metro_items.items.LapelPin
import com.example.metrognome.ui.components.metro_items.items.StudioMic
import com.example.metrognome.ui.components.metro_items.items.CheekTattoo

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
    MetroItemEntry(MoonAndStars,       UnlockCondition.MetronomeSeconds(14_400)),   // 4 h
    MetroItemEntry(GlissieFairy,        UnlockCondition.MetronomeSeconds(21_600)),   // 6 h — drawn last, floats closest

    // ── Loyalty wearable — earned by sticking with Metro, not by play-time ───
    // First wearable tied to calendar days rather than usage. Drawn after GoldChain
    // so the pin sits on top of the lapel when both are worn. Fills the day-based
    // gap between ForestFloorFlowers (3 d) and ForestTree (30 d).
    MetroItemEntry(LapelPin, UnlockCondition.LoyaltyDays(7)),   // 7 distinct days opened

    // ── Practice rewards — require Practice Mode IAP to earn ─────────────────
    // Earn-only (no purchase path): these reward consistent practice, not spending.
    MetroItemEntry(MusicStand, UnlockCondition.PracticeSessionsCompleted(5)),  // 5 sessions

    // ── Tuner feedback reward ─────────────────────────────────────────────────
    // Earned by submitting three feedback readings via the tuner feedback card
    // (one was too easy to trip accidentally).
    MetroItemEntry(TuningFork, UnlockCondition.TunerFeedbackGiven(3)),

    // ── Tuner rewards — TunerSeconds accumulates from v3.3 onwards ────────────
    // Thresholds (tuner seconds — mic actively open):
    //   5 min  =     300 s
    //   15 min =     900 s
    //   1 h    =   3 600 s
    //   3 h    =  10 800 s
    MetroItemEntry(HatFeather, UnlockCondition.TunerSeconds(900)),  // 15 min — head-attached plume

    // ── Groove Check reward — earned by running the microphone self-test ──────
    // Unlocks on the first completed run of any verdict (PASS / FAIL / fixable ABORT),
    // both rewarding the attempt and surfacing an otherwise-buried feature. Drawn after
    // GoldChain so the mic sits on top of the right lapel when worn.
    MetroItemEntry(StudioMic, UnlockCondition.MicChecksCompleted(1)),

    // ── Loyalty ink — the long-horizon endgame wearable ───────────────────────
    // A tattoo is permanent, so it is earned by real commitment: 30 distinct days
    // opened, not 30 calendar days elapsed. Extends the collection past ForestTree
    // (30 d since install), which was previously the last earnable free item.
    MetroItemEntry(CheekTattoo, UnlockCondition.LoyaltyDays(30)),

    // ── Speed Trainer rewards — unlocked by completing sessions ───────────────
    // Earn-only: rewards consistent structured practice. No purchase path.
    // Thresholds: 1 session = first time, 5 = habit-forming, 10 = dedicated
    // MetroItemEntry(SpeedRacerBadge, UnlockCondition.SpeedTrainingSessionsCompleted(1)),   // first session ever
    // MetroItemEntry(SpeedBoots,      UnlockCondition.SpeedTrainingSessionsCompleted(5)),   // 5 sessions
    // MetroItemEntry(SpeedHalo,       UnlockCondition.SpeedTrainingSessionsCompleted(10)),  // 10 sessions

    // ── Future wearables — add entries here as new files are created ──────────
    // (WristTattoo + ArmBracelet dropped: sleeves cover wrists and arms.
    //  LapelPin shipped as LoyaltyDays(7). CheekTattoo shipped as LoyaltyDays(30).)
    // MetroItemEntry(TieBar,        UnlockCondition.MetronomeSeconds(18_000)),   // 5 h
    // MetroItemEntry(PinkyRing,     UnlockCondition.MetronomeSeconds(36_000)),   // 10 h
    // MetroItemEntry(DiamondShades, UnlockCondition.MetronomeSeconds(54_000)),   // 15 h
    // MetroItemEntry(WalkingCane,   UnlockCondition.MetronomeSeconds(108_000)),  // 30 h
    // ── Future forest / companions ────────────────────────────────────────────
    // MetroItemEntry(Bambi,         UnlockCondition.DaysSinceFirstLaunch(7)),    // 1 week
    // MetroItemEntry(Butterfly,     UnlockCondition.DaysSinceFirstLaunch(14)),
    // MetroItemEntry(Squirrel,      UnlockCondition.DaysSinceFirstLaunch(21)),
    // MetroItemEntry(OwlOnHat,      UnlockCondition.DaysSinceFirstLaunch(30)),   // isHeadAttached = true
    // MetroItemEntry(FoxCub,        UnlockCondition.DaysSinceFirstLaunch(45)),
    // MetroItemEntry(BirthdayHat,   UnlockCondition.DaysSinceFirstLaunch(365)),  // 1 year
)
