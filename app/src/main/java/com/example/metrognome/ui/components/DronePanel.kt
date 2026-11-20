package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import com.example.metrognome.audio.NoteNames
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.audio.drone.DroneBlend
import com.example.metrognome.audio.drone.DroneState
import com.example.metrognome.audio.drone.DroneTimbre
import com.example.metrognome.ui.theme.AppColors
import java.util.Locale

/**
 * The tuning drone: pick a note, hold it, tune against it by ear.
 *
 * Sits between the environment panel and the calibration card on the Tuner screen, and is
 * stateless in the same way as the rest of that screen so previews and UI tests can drive
 * every combination.
 *
 * The card always shows what is needed to sound a note: the keyboard, the octave, the voice
 * pill and the play key, on one row whatever the voice (2026-11-20). The pill names the voice
 * in use on two lines, timbre over blend, with the timbre's mark, and opens the drone's sound stage, where every
 * timbre and blend is browsed, heard and bought (2026-11-19; it replaced two chip rows and a
 * purchase dialog). Only the volume lives behind the chevron, and the header says what it is
 * set to so the hidden control is never a mystery. [expanded] is hoisted because the caller
 * persists it.
 *
 * The keyboard is drawn rather than labelled. A musician finds a note on a keyboard by the
 * black-key groups of two and three, the same way they do on a real one, so letters would
 * only add clutter; the chosen note is spelled out beside the octave stepper anyway. That
 * argument depends entirely on the two kinds of key actually contrasting, which is where
 * the first version of this failed: drawn in four shades of the same dark violet, the
 * grouping that was doing all the orienting simply was not visible. See [DroneKeyboard].
 */
@Composable
fun DronePanel(
    state: DroneState,
    referenceHz: Float,
    onToggle: () -> Unit,
    onSetNote: (Int) -> Unit,
    onShiftOctave: (Int) -> Unit,
    onOpenVoices: () -> Unit,
    onSetVolume: (Float) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronDeg by animateFloatAsState(if (expanded) 180f else 0f, label = "droneChevron")

    AppCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {

            // ── Header: wave glyph + what is loaded + expand chevron ──────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onExpandedChange(!expanded) },
            ) {
                DroneWaveIcon(sounding = state.playing, modifier = Modifier.size(width = 26.dp, height = 18.dp))
                Spacer(Modifier.width(8.dp))
                CardHeaderLabel("DRONE")
                Spacer(Modifier.weight(1f))
                // What the chevron hides, so the folded control is never a mystery.
                Text(
                    "Volume ${(state.volume * 100).roundToInt()}%",
                    color = AppColors.textMuted,
                    fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                AppIcon(
                    glyph = AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = AppColors.textDim,
                    modifier = Modifier.size(18.dp).rotate(chevronDeg),
                )
            }

            Spacer(Modifier.height(12.dp))

            DroneKeyboard(
                pitchClass = state.pitchClass,
                sounding = state.playing,
                onSelect = onSetNote,
                modifier = Modifier
                    .fillMaxWidth()
                    // Tall enough that a natural is clearly longer than it is wide. At the
                    // original 48 dp the seven keys came out nearly square and the picker
                    // read as a different instrument from the Chords tab's keyboard, which is
                    // the same component drawn at real key proportions.
                    .height(84.dp)
                    // The keyboard carries its meaning entirely in what it looks like, so
                    // without this a screen reader finds an unlabelled canvas where the
                    // note picker should be.
                    .semantics { contentDescription = "Drone note keyboard, ${state.noteLabel} selected" },
            )

            Spacer(Modifier.height(12.dp))

            // ── Octave stepper + note readout + voice pill + play key ─────────────
            // The steppers, readout and play key never shrink; the pill takes what is left.
            // The readout is as wide as the widest note and Hz it can show at this font scale
            // (measured, not guessed), so the pill never moves when a key is tapped, and no
            // wider: a flat 70 dp left a 360 dp phone's pill room for "Pure" and little else.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val readoutWidth = remember(referenceHz, density) {
                val note = listOf("C#5", "D#5", "F#5", "G#5", "A#5").maxOf {
                    measurer.measure(it, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black)).size.width
                }
                val topHz = String.format(Locale.US, "%.1f Hz", NoteNames.frequencyOf(DroneState.MAX_MIDI, referenceHz))
                val hz = measurer.measure(topHz, TextStyle(fontSize = 9.sp)).size.width
                with(density) { maxOf(note, hz).toDp() + 4.dp }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.alpha(if (state.canOctaveDown) 1f else 0.3f)) {
                    CircleButton("−", onClick = { onShiftOctave(-1) }, contentDescription = "Octave down", enabled = state.canOctaveDown)
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(readoutWidth)) {
                    Text(
                        state.noteLabel,
                        color = if (state.playing) AppColors.gold else AppColors.textPrimary,
                        fontSize = 20.sp, lineHeight = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        String.format(Locale.US, "%.1f Hz", state.frequencyHz(referenceHz)),
                        color = AppColors.textDim,
                        fontSize = 9.sp, lineHeight = 12.sp,
                        maxLines = 1, softWrap = false,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.alpha(if (state.canOctaveUp) 1f else 0.3f)) {
                    CircleButton("+", onClick = { onShiftOctave(1) }, contentDescription = "Octave up", enabled = state.canOctaveUp)
                }

                // The voice in use, opening the drone's sound stage: the Home sound pill's
                // twin, on two lines (timbre over blend) so both fit in the longer word's
                // width and inside the play key's height. Takes whatever width the row leaves.
                Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    GoldPill(
                        text = state.timbre.displayName,
                        secondaryText = state.blend.displayName,
                        leadingIcon = com.example.metrognome.ui.sounds.droneGlyphFor(state.timbre),
                        onClick = onOpenVoices,
                        modifier = Modifier.semantics {
                            contentDescription = "Drone voice: ${state.timbre.displayName}, ${state.blend.displayName}. Browse voices"
                        },
                    )
                }

                PlayStopKey(
                    playing = state.playing,
                    onClick = onToggle,
                    playDescription = "Play the drone",
                    stopDescription = "Stop the drone",
                )
            }

            // ── Why the needle went quiet ─────────────────────────────────────────
            // Without this the gauge simply stops responding the moment the drone starts,
            // which reads as the tuner having broken rather than having stepped aside.
            // Says what happened and that it undoes itself, and nothing else: the reason
            // (the mic would hear the app's own tone and lock onto it) is the developer's
            // problem, not the reader's, and lives in TunerViewModel.toggleDrone instead.
            AnimatedVisibility(
                visible = state.playing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tuner needle paused",
                        color = AppColors.gold,
                        fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "The needle returns when you stop the drone.",
                        color = AppColors.textMuted,
                        fontSize = 10.sp, lineHeight = 14.sp,
                    )
                }
            }

            // ── Volume (behind the chevron) ───────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("VOLUME")
                        Spacer(Modifier.width(12.dp))
                        GoldSlider(
                            value = state.volume,
                            onValueChange = onSetVolume,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        // Fixed width, end-aligned, so the rail keeps its length from 0% to 100%.
                        Text(
                            "${(state.volume * 100).roundToInt()}%",
                            color = AppColors.textSecondary,
                            fontSize = 12.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
}

// ── Keyboard ─────────────────────────────────────────────────────────────────────

/**
 * A one-octave keyboard for choosing the drone's note: the shared [PianoKeyboard] with
 * pitch classes standing in for MIDI numbers (0..11), and no octave labels, since the
 * chosen note is spelled out beside the octave stepper anyway.
 */
@Composable
private fun DroneKeyboard(
    pitchClass: Int,
    sounding: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PianoKeyboard(
        octaves = 1,
        lowestMidi = 0,
        litMidi = setOf(pitchClass),
        // Gold while sounding, matching the rest of the tuner's "this is live" language.
        accent = if (sounding) AppColors.gold else AppColors.mediumPurple,
        onKeyTap = onSelect,
        modifier = modifier,
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun DronePanelIdlePreview() {
    DronePanel(
        state = DroneState(),
        referenceHz = 440f,
        onToggle = {}, onSetNote = {}, onShiftOctave = {},
        onOpenVoices = {}, onSetVolume = {},
        expanded = true, onExpandedChange = {},
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun DronePanelPlayingPreview() {
    DronePanel(
        state = DroneState(playing = true, midi = 50, timbre = DroneTimbre.REED, blend = DroneBlend.FIFTH),
        referenceHz = 442f,
        onToggle = {}, onSetNote = {}, onShiftOctave = {},
        onOpenVoices = {}, onSetVolume = {},
        expanded = false, onExpandedChange = {},
        modifier = Modifier.padding(16.dp),
    )
}
