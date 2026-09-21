package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.audio.drone.DroneBlend
import com.example.metrognome.audio.drone.DroneState
import com.example.metrognome.audio.drone.DroneTimbre
import com.example.metrognome.ui.theme.AppColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * The tuning drone: pick a note, hold it, tune against it by ear.
 *
 * Sits between the environment panel and the calibration card on the Tuner screen, and is
 * stateless in the same way as the rest of that screen so previews and UI tests can drive
 * every combination.
 *
 * Collapsed it shows only what is needed to sound a note: the keyboard, the octave, and the
 * play key. The voice controls live behind the chevron, because "which note" is a decision
 * made every time and "which timbre" is one made about once. [expanded] is hoisted rather
 * than held here, because the caller persists it: the panel opens on a first visit so the
 * controls are seen at all, and afterwards stays wherever the user last left it.
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
    onSetTimbre: (DroneTimbre) -> Unit,
    onSetBlend: (DroneBlend) -> Unit,
    onSetVolume: (Float) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Which voices are paid, so their chips carry the star. Not which ones are *locked*:
     * the star marks a premium feature and stays after purchase, and whether a tap selects
     * the voice or opens the purchase dialog is the caller's decision, not this panel's.
     */
    premiumTimbres: Set<DroneTimbre> = emptySet(),
    premiumBlends: Set<DroneBlend> = emptySet(),
) {
    val chevronDeg by animateFloatAsState(if (expanded) 180f else 0f, label = "droneChevron")

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {

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
                Text(
                    "DRONE",
                    color = AppColors.textDim,
                    fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.noteLabel} · ${state.timbre.displayName}" +
                        if (state.blend == DroneBlend.ROOT) "" else " · ${state.blend.displayName}",
                    color = if (state.playing) AppColors.gold else AppColors.textMuted,
                    fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
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

            // ── Octave stepper + note readout + play key ──────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.alpha(if (state.canOctaveDown) 1f else 0.3f)) {
                    CircleButton("−", onClick = { onShiftOctave(-1) })
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
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
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.alpha(if (state.canOctaveUp) 1f else 0.3f)) {
                    CircleButton("+", onClick = { onShiftOctave(1) })
                }

                Spacer(Modifier.weight(1f))

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

            // ── Voice controls ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    ChipSection(heading = "TIMBRE", caption = state.timbre.caption) {
                        DroneTimbre.entries.forEach { timbre ->
                            if (timbre in premiumTimbres) {
                                PremiumChip(
                                    selected = timbre == state.timbre,
                                    label = timbre.displayName,
                                    onClick = { onSetTimbre(timbre) },
                                )
                            } else {
                                AppFilterChip(
                                    selected = timbre == state.timbre,
                                    onClick = { onSetTimbre(timbre) },
                                    label = timbre.displayName,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    ChipSection(heading = "BLEND", caption = state.blend.caption) {
                        DroneBlend.entries.forEach { blend ->
                            if (blend in premiumBlends) {
                                PremiumChip(
                                    selected = blend == state.blend,
                                    label = blend.displayName,
                                    onClick = { onSetBlend(blend) },
                                )
                            } else {
                                AppFilterChip(
                                    selected = blend == state.blend,
                                    onClick = { onSetBlend(blend) },
                                    label = blend.displayName,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "VOLUME",
                            color = AppColors.textDim,
                            fontSize = 10.sp, lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        GoldSlider(
                            value = state.volume,
                            onValueChange = onSetVolume,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

/** Caps heading, a scrollable chip row, and the one-line explanation of what is selected. */
@Composable
private fun ChipSection(
    heading: String,
    caption: String,
    chips: @Composable () -> Unit,
) {
    Column {
        Text(
            heading,
            color = AppColors.textDim,
            fontSize = 10.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
        }
        Crossfade(targetState = caption, animationSpec = tween(200), label = "droneCaption") { text ->
            // The chip row already says which one is selected, so the caption's job is to
            // say what it is for. Capped at two lines so switching chips never reflows the card.
            Text(
                text,
                color = AppColors.textMuted,
                fontSize = 11.sp, lineHeight = 15.sp,
                maxLines = 2,
            )
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

// ── Header glyph ─────────────────────────────────────────────────────────────────

/**
 * A sine wave that travels while the tone sounds and sits still when it does not.
 *
 * The panel's identity mark and its state indicator in one: a drone that keeps playing
 * after the user has scrolled or switched tabs is visibly still running.
 */
@Composable
private fun DroneWaveIcon(sounding: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "droneWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "droneWavePhase",
    )
    val color by animateColorAsState(
        targetValue = if (sounding) AppColors.gold else AppColors.textDim,
        animationSpec = tween(260),
        label = "droneWaveTint",
    )

    Canvas(modifier = modifier) {
        val travel = if (sounding) phase else 0f
        val amplitude = size.height * 0.34f
        val midY = size.height / 2f
        val path = Path()
        val steps = 28
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = midY + amplitude * sin(2.0 * PI * (1.6 * i / steps - travel)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun DronePanelIdlePreview() {
    DronePanel(
        state = DroneState(),
        referenceHz = 440f,
        onToggle = {}, onSetNote = {}, onShiftOctave = {},
        onSetTimbre = {}, onSetBlend = {}, onSetVolume = {},
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
        onSetTimbre = {}, onSetBlend = {}, onSetVolume = {},
        expanded = false, onExpandedChange = {},
        modifier = Modifier.padding(16.dp),
    )
}
