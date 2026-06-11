package com.example.metrognome.debug.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.practice.PracticeSessionManager
import com.example.metrognome.usage.ActivitySummary
import com.example.metrognome.usage.ActivitySummaryLogger
import com.example.metrognome.usage.ActivitySummaryRestorer
import com.example.metrognome.usage.mergeSummaries
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.max
import kotlin.math.min

private val passColor = Color(0xFF4CAF50)
private val failColor = Color(0xFFE53935)
private val otherColor = Color(0xFF64B5F6)   // "incoming device won this field"

private enum class Mode { ROUND_TRIP, MERGE_DEMO }

/**
 * Dev tool with two modes for the profile capture/restore path:
 *
 *  - ROUND_TRIP: collect → restore(self) → collect, then diff. PASS proves every field
 *    survives a SharedPreferences write+read with no loss. Touches real prefs but is
 *    non-destructive (restore merges by furthest progress, so self-restore is identity).
 *
 *  - MERGE_DEMO: collect this device's profile (read-only, NO writes), fabricate a
 *    synthetic "other device" that is ahead on some fields and behind on others, run
 *    the pure [mergeSummaries], and show per-field which side won. Proves the
 *    cross-device reconciliation keeps the furthest progress (max / union / earliest),
 *    and self-checks each field so it also tests the merge.
 */
@Composable
fun ProfileRoundTripOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(Mode.ROUND_TRIP) }

    // Round-trip state.
    var before by remember { mutableStateOf<ActivitySummary?>(null) }
    var after by remember { mutableStateOf<ActivitySummary?>(null) }

    // Merge-demo state.
    var local by remember { mutableStateOf<ActivitySummary?>(null) }
    var other by remember { mutableStateOf<ActivitySummary?>(null) }
    var merged by remember { mutableStateOf<ActivitySummary?>(null) }

    LaunchedEffect(mode) {
        when (mode) {
            Mode.ROUND_TRIP -> if (before == null) {
                val logger = ActivitySummaryLogger(context)
                val b = logger.collect()
                ActivitySummaryRestorer(context).restore(b)   // writes (non-destructive)
                before = b
                after = logger.collect()
            }
            Mode.MERGE_DEMO -> if (local == null) {
                val l = ActivitySummaryLogger(context).collect() // read-only
                val o = syntheticOtherDevice(l)
                local = l
                other = o
                merged = mergeSummaries(l, o)                    // pure, no writes
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                // ── Header ────────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⟳", fontSize = 16.sp, color = AppColors.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "PROFILE SYNC",
                        color = AppColors.gold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            )
                            .padding(6.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = AppColors.textMuted)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Mode toggle ───────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModePill("Round-Trip", mode == Mode.ROUND_TRIP, Modifier.weight(1f)) { mode = Mode.ROUND_TRIP }
                    ModePill("Merge Demo", mode == Mode.MERGE_DEMO, Modifier.weight(1f)) { mode = Mode.MERGE_DEMO }
                }

                Spacer(Modifier.height(12.dp))

                when (mode) {
                    Mode.ROUND_TRIP -> RoundTripContent(before, after)
                    Mode.MERGE_DEMO -> MergeDemoContent(local, other, merged)
                }
            }
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) AppColors.gold.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) AppColors.gold else AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

// ── Round-trip ──────────────────────────────────────────────────────────────────

@Composable
private fun RoundTripContent(before: ActivitySummary?, after: ActivitySummary?) {
    if (before == null || after == null) {
        Text("Running round-trip…", color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        return
    }
    val rows = compareRows(before, after)
    val matched = rows.count { it.ok }
    val allOk = matched == rows.size

    Surface(
        color = (if (allOk) passColor else failColor).copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (allOk) "PASS" else "FAIL",
                color = if (allOk) passColor else failColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            )
            Text(
                "$matched / ${rows.size} fields survived capture → restore → capture",
                color = AppColors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Text(
                "re-read confirmed: capturedAt ${before.capturedAtMs} → ${after.capturedAtMs}",
                color = AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { DiffRow(it) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DiffRow(row: CmpRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${if (row.ok) "✓" else "✗"} ${row.label}",
                color = if (row.ok) passColor else failColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (row.ok) FontWeight.Normal else FontWeight.Bold,
            )
            Text(row.before, color = AppColors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        if (!row.ok) {
            Text(
                "    before: ${row.before}   after: ${row.after}",
                color = failColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}

// ── Merge demo ──────────────────────────────────────────────────────────────────

@Composable
private fun MergeDemoContent(local: ActivitySummary?, other: ActivitySummary?, merged: ActivitySummary?) {
    if (local == null || other == null || merged == null) {
        Text("Building merge…", color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        return
    }
    val rows = mergeRows(local, other, merged)
    val correct = rows.count { it.ok }
    val allOk = correct == rows.size
    val differing = rows.count { it.tag != "same" }

    Surface(
        color = (if (allOk) passColor else failColor).copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (allOk) "MERGE OK" else "MERGE WRONG",
                color = if (allOk) passColor else failColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            Text(
                "$differing fields differed; merge kept the furthest progress in all.",
                color = AppColors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "L = this device   O = other device   → = merged",
                color = AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { MergeRowView(it) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MergeRowView(row: MergeRow) {
    // Colour the merged value by who contributed it.
    val mergedColor = when (row.tag) {
        "O wins", "O earlier" -> otherColor
        "L wins"              -> AppColors.textSecondary
        "same"                -> AppColors.textMuted
        else                  -> AppColors.gold   // union / per-key max
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${if (row.ok) "✓" else "✗"} ${row.label}",
                color = if (row.ok) AppColors.textPrimary else failColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(row.tag, color = mergedColor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        if (row.multiline) {
            Text("  L: ${row.local}", color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("  O: ${row.other}", color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("  → ${row.merged}", color = mergedColor, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        } else {
            Text(
                "  L ${row.local}   O ${row.other}   → ${row.merged}",
                color = mergedColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}

// ── Synthetic "other device" ────────────────────────────────────────────────────

/**
 * A plausible second device for the same user: ahead on some activities, behind on
 * others, with extra days / records / items, an earlier first launch and an active
 * ad-free reward. Crafted so the merge has to choose every way (other wins, local
 * wins, earliest, union, per-key max).
 */
private fun syntheticOtherDevice(l: ActivitySummary): ActivitySummary {
    val today = PracticeSessionManager.currentEpochDay()
    return l.copy(
        capturedAtMs                  = l.capturedAtMs + 5_000,
        firstLaunchMs                 = l.firstLaunchMs - 10L * 86_400_000L,   // earlier → other
        distinctUsageDays             = l.distinctUsageDays + 3,               // other ahead
        daysSinceInstall              = l.daysSinceInstall + 10,
        metronomeSeconds              = l.metronomeSeconds + 3_600,            // other ahead
        tunerSeconds                  = l.tunerSeconds + 7_200,                // other ahead
        speedTrainerSeconds           = l.speedTrainerSeconds / 2,             // other behind → local
        tunerNotesLocked              = l.tunerNotesLocked + 40,               // other ahead
        tunerFeedbackGiven            = l.tunerFeedbackGiven + 3,
        gamesCompleted                = (l.gamesCompleted - 1).coerceAtLeast(0), // behind → local
        totalGameScore                = l.totalGameScore / 2,                  // behind → local
        practiceMinutesTotal          = l.practiceMinutesTotal + 15,           // other ahead
        practiceSessionsCompleted     = l.practiceSessionsCompleted + 2,
        speedTrainerSessionsCompleted = (l.speedTrainerSessionsCompleted - 3).coerceAtLeast(0), // local
        performanceBonusPoints        = l.performanceBonusPoints + 30,
        rewardedAdGnotes              = l.rewardedAdGnotes + 50,
        bestPracticeStreak            = l.bestPracticeStreak + 5,              // other ahead
        practicedEpochDays            = l.practicedEpochDays + setOf(today, today - 9), // new days → union
        // shared keys lower on other (local wins per-key max) + one key only on other (union)
        rhythmHighScores              = l.rhythmHighScores.mapValues { (it.value - 200).coerceAtLeast(0) } +
                                            mapOf("Advanced" to 3_000),
        // shared keys higher on other (other wins per-key max) + one key only on other (union)
        speedTrainerRecords           = l.speedTrainerRecords.mapValues { it.value + 10 } +
                                            mapOf("60_90" to 120),
        unlockedItemIds               = l.unlockedItemIds + "demo_other_item",
        celebratedItemIds             = l.celebratedItemIds + "demo_other_item",
        adFreeRewardUntilMs           = System.currentTimeMillis() + 3L * 86_400_000L, // other has reward
        gnoteTotal                    = l.gnoteTotal + 50,
    )
}

// ── Round-trip comparison model ─────────────────────────────────────────────────

private data class CmpRow(val label: String, val before: String, val after: String) {
    val ok get() = before == after
}

/** Every field except capturedAtMs (expected to differ — it proves a real re-read). */
private fun compareRows(b: ActivitySummary, a: ActivitySummary): List<CmpRow> = listOf(
    CmpRow("firstLaunchMs", b.firstLaunchMs.toString(), a.firstLaunchMs.toString()),
    CmpRow("distinctUsageDays", b.distinctUsageDays.toString(), a.distinctUsageDays.toString()),
    CmpRow("daysSinceInstall", b.daysSinceInstall.toString(), a.daysSinceInstall.toString()),
    CmpRow("metronomeSeconds", b.metronomeSeconds.toString(), a.metronomeSeconds.toString()),
    CmpRow("tunerSeconds", b.tunerSeconds.toString(), a.tunerSeconds.toString()),
    CmpRow("speedTrainerSeconds", b.speedTrainerSeconds.toString(), a.speedTrainerSeconds.toString()),
    CmpRow("tunerNotesLocked", b.tunerNotesLocked.toString(), a.tunerNotesLocked.toString()),
    CmpRow("tunerFeedbackGiven", b.tunerFeedbackGiven.toString(), a.tunerFeedbackGiven.toString()),
    CmpRow("gamesCompleted", b.gamesCompleted.toString(), a.gamesCompleted.toString()),
    CmpRow("totalGameScore", b.totalGameScore.toString(), a.totalGameScore.toString()),
    CmpRow("practiceMinutesTotal", b.practiceMinutesTotal.toString(), a.practiceMinutesTotal.toString()),
    CmpRow("practiceSessionsCompleted", b.practiceSessionsCompleted.toString(), a.practiceSessionsCompleted.toString()),
    CmpRow("speedTrainerSessionsCompleted", b.speedTrainerSessionsCompleted.toString(), a.speedTrainerSessionsCompleted.toString()),
    CmpRow("performanceBonusPoints", b.performanceBonusPoints.toString(), a.performanceBonusPoints.toString()),
    CmpRow("rewardedAdGnotes", b.rewardedAdGnotes.toString(), a.rewardedAdGnotes.toString()),
    CmpRow("bestPracticeStreak", b.bestPracticeStreak.toString(), a.bestPracticeStreak.toString()),
    CmpRow("practicedEpochDays", fmtSet(b.practicedEpochDays), fmtSet(a.practicedEpochDays)),
    CmpRow("rhythmHighScores", fmtMap(b.rhythmHighScores), fmtMap(a.rhythmHighScores)),
    CmpRow("speedTrainerRecords", fmtMap(b.speedTrainerRecords), fmtMap(a.speedTrainerRecords)),
    CmpRow("unlockedItemIds", fmtSet(b.unlockedItemIds), fmtSet(a.unlockedItemIds)),
    CmpRow("celebratedItemIds", fmtSet(b.celebratedItemIds), fmtSet(a.celebratedItemIds)),
    CmpRow("adFreeRewardUntilMs", b.adFreeRewardUntilMs.toString(), a.adFreeRewardUntilMs.toString()),
    CmpRow("gnoteTotal", b.gnoteTotal.toString(), a.gnoteTotal.toString()),
)

// ── Merge model ─────────────────────────────────────────────────────────────────

private data class MergeRow(
    val label: String,
    val local: String,
    val other: String,
    val merged: String,
    val tag: String,
    val ok: Boolean,
    val multiline: Boolean,
)

private fun mergeRows(l: ActivitySummary, o: ActivitySummary, m: ActivitySummary): List<MergeRow> = listOf(
    maxRowL("firstLaunchMs", l.firstLaunchMs, o.firstLaunchMs, m.firstLaunchMs, earliest = true),
    maxRowI("distinctUsageDays", l.distinctUsageDays, o.distinctUsageDays, m.distinctUsageDays),
    maxRowI("daysSinceInstall", l.daysSinceInstall, o.daysSinceInstall, m.daysSinceInstall),
    maxRowL("metronomeSeconds", l.metronomeSeconds, o.metronomeSeconds, m.metronomeSeconds),
    maxRowL("tunerSeconds", l.tunerSeconds, o.tunerSeconds, m.tunerSeconds),
    maxRowL("speedTrainerSeconds", l.speedTrainerSeconds, o.speedTrainerSeconds, m.speedTrainerSeconds),
    maxRowI("tunerNotesLocked", l.tunerNotesLocked, o.tunerNotesLocked, m.tunerNotesLocked),
    maxRowI("tunerFeedbackGiven", l.tunerFeedbackGiven, o.tunerFeedbackGiven, m.tunerFeedbackGiven),
    maxRowI("gamesCompleted", l.gamesCompleted, o.gamesCompleted, m.gamesCompleted),
    maxRowI("totalGameScore", l.totalGameScore, o.totalGameScore, m.totalGameScore),
    maxRowI("practiceMinutesTotal", l.practiceMinutesTotal, o.practiceMinutesTotal, m.practiceMinutesTotal),
    maxRowI("practiceSessionsCompleted", l.practiceSessionsCompleted, o.practiceSessionsCompleted, m.practiceSessionsCompleted),
    maxRowI("speedTrainerSessionsCompleted", l.speedTrainerSessionsCompleted, o.speedTrainerSessionsCompleted, m.speedTrainerSessionsCompleted),
    maxRowI("performanceBonusPoints", l.performanceBonusPoints, o.performanceBonusPoints, m.performanceBonusPoints),
    maxRowI("rewardedAdGnotes", l.rewardedAdGnotes, o.rewardedAdGnotes, m.rewardedAdGnotes),
    maxRowI("bestPracticeStreak", l.bestPracticeStreak, o.bestPracticeStreak, m.bestPracticeStreak),
    maxRowL("adFreeRewardUntilMs", l.adFreeRewardUntilMs, o.adFreeRewardUntilMs, m.adFreeRewardUntilMs),
    setRow("practicedEpochDays", l.practicedEpochDays, o.practicedEpochDays, m.practicedEpochDays),
    setRow("unlockedItemIds", l.unlockedItemIds, o.unlockedItemIds, m.unlockedItemIds),
    setRow("celebratedItemIds", l.celebratedItemIds, o.celebratedItemIds, m.celebratedItemIds),
    mapRow("rhythmHighScores", l.rhythmHighScores, o.rhythmHighScores, m.rhythmHighScores),
    mapRow("speedTrainerRecords", l.speedTrainerRecords, o.speedTrainerRecords, m.speedTrainerRecords),
)

private fun maxRowL(label: String, l: Long, o: Long, m: Long, earliest: Boolean = false): MergeRow {
    val expected = if (earliest) min(l, o) else max(l, o)
    val tag = when {
        l == o            -> "same"
        m == o && earliest -> "O earlier"
        m == o            -> "O wins"
        else              -> "L wins"
    }
    return MergeRow(label, l.toString(), o.toString(), m.toString(), tag, m == expected, false)
}

private fun maxRowI(label: String, l: Int, o: Int, m: Int): MergeRow =
    maxRowL(label, l.toLong(), o.toLong(), m.toLong())

private fun setRow(label: String, l: Set<*>, o: Set<*>, m: Set<*>): MergeRow {
    val expected = l.map { it.toString() }.toSet() + o.map { it.toString() }.toSet()
    val got = m.map { it.toString() }.toSet()
    return MergeRow(label, fmtSet(l), fmtSet(o), fmtSet(m), "union", got == expected, true)
}

private fun mapRow(label: String, l: Map<String, Int>, o: Map<String, Int>, m: Map<String, Int>): MergeRow {
    val expected = (l.keys + o.keys).associateWith { max(l[it] ?: 0, o[it] ?: 0) }
    return MergeRow(label, fmtMap(l), fmtMap(o), fmtMap(m), "per-key max", m == expected, true)
}

// ── Formatting ──────────────────────────────────────────────────────────────────

private fun fmtSet(s: Set<*>): String =
    s.map { it.toString() }.sorted().joinToString(",").ifEmpty { "-" }

private fun fmtMap(map: Map<String, Int>): String =
    map.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { "-" }
