package com.example.metrognome.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.debug.chords.ChordLoopRunningPill
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import com.example.metrognome.theory.ChordMatch
import com.example.metrognome.theory.ChordReading
import com.example.metrognome.theory.ChordTheory
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.AppFilterChip
import com.example.metrognome.ui.components.FadingHorizontalScrollbar
import com.example.metrognome.ui.components.GoldPill
import com.example.metrognome.ui.components.GuitarFretboard
import com.example.metrognome.ui.components.SCROLLBAR_HINT_HEIGHT
import com.example.metrognome.ui.components.InputLevelMeter
import com.example.metrognome.ui.components.ListeningStateBadge
import com.example.metrognome.ui.components.MicAccessStrip
import com.example.metrognome.ui.components.PianoKeyboard
import com.example.metrognome.ui.components.fretboardPositionFractions
import com.example.metrognome.ui.components.pianoKeyCentreFraction
import com.example.metrognome.ui.overlays.UnlockCelebrationOverlay
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.ChordFinderViewModel
import com.example.metrognome.viewmodel.ChordInstrument
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Stateful entry point ─────────────────────────────────────────────────────────

/**
 * The Chords tab: play or tap notes, read the chord.
 *
 * Owns the side effects (mic permission, opening and closing the mic with the tab, the
 * capture flash); everything drawn lives in the stateless [ChordFinderContent]. The mic
 * opens on entry when the permission is held and the user has not switched it off, and
 * closes when the tab is left, so a chord named here never keeps the mic open behind
 * another screen. Like the other tabs it carries no heading; the nav bar names it.
 */
@Composable
fun ChordFinderScreen(
    vm: ChordFinderViewModel,
    isAdFree: Boolean = false,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptics = LocalHaptics.current

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var micPermanentlyDenied by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (!granted && activity != null) {
            micPermanentlyDenied = !androidx.core.app.ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }
    }

    // Re-read the grant whenever the app comes back to the foreground: the only way past
    // "permanently denied" is a round trip through App Settings, and the strip would
    // otherwise keep saying "tap to grant" after the user had.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    // One session per visit to the tab, for analytics; separate from the mic effect below,
    // which re-runs when the permission or the mic choice changes mid-visit.
    DisposableEffect(Unit) {
        vm.onScreenEntered()
        onDispose { vm.onScreenLeft() }
    }

    val micEnabled by vm.micEnabled.collectAsStateWithLifecycle()
    DisposableEffect(micGranted, micEnabled) {
        if (micGranted && micEnabled) vm.startListening()
        onDispose { vm.stopListening() }
    }

    val notes by vm.notes.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    val instrument by vm.instrument.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val heard by vm.heard.collectAsStateWithLifecycle()
    val amplitude by vm.amplitude.collectAsStateWithLifecycle()
    val ambient by vm.ambient.collectAsStateWithLifecycle()
    val unlockQueue by vm.unlockQueue.collectAsStateWithLifecycle()

    // A captured note flashes on the instrument and ticks in the hand, so a note that
    // arrived from the mic is unmistakably an event rather than a key that changed colour.
    var glowMidi by remember { mutableStateOf<Int?>(null) }
    var captureSerial by remember { mutableIntStateOf(0) }
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(vm) {
        // collectLatest: a second note during the fade restarts it on the new key.
        vm.captured.collectLatest { midi ->
            haptics.fire(HapticPattern.TICK)
            glowMidi = midi
            captureSerial++
            glowAlpha.snapTo(1f)
            glowAlpha.animateTo(0f, tween(GLOW_MS))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChordFinderContent(
            notes = notes,
            reading = reading,
            instrument = instrument,
            listening = listening,
            micEnabled = micEnabled,
            heard = heard,
            amplitude = amplitude,
            listeningState = if (listening) ambient.state else null,
            micGranted = micGranted,
            micPermanentlyDenied = micPermanentlyDenied,
            glow = glowMidi?.let { it to glowAlpha.value },
            captureSerial = captureSerial,
            isAdFree = isAdFree,
            onToggleNote = vm::toggleNote,
            onRemoveNote = vm::removeNote,
            onClear = vm::clear,
            onSetInstrument = vm::setInstrument,
            onToggleMic = vm::toggleMic,
            onRequestMic = {
                if (micPermanentlyDenied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )

        // Dev only in effect: composes to nothing unless a Chord Loop run is in progress,
        // which only the dev tools can start. Overlaid, so the page never reflows.
        ChordLoopRunningPill(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )

        // The Acoustic Guitar is earned here, so it is celebrated here, the way the rhythm
        // game celebrates its own items rather than waiting for the Gnome tab.
        unlockQueue.firstOrNull()?.let { entry ->
            UnlockCelebrationOverlay(
                entry = entry,
                onDismiss = { vm.markCelebrated(entry.item.id) },
            )
        }
    }
}

private const val GLOW_MS = 700

/** Easing the instrument to a captured note: long enough to follow, short enough to end before the glow does. */
private const val SCROLL_TO_NOTE_MS = 450

// ── Stateless content ────────────────────────────────────────────────────────────

/**
 * The whole page as a function of its inputs, so previews and tests can drive every
 * reading.
 *
 * Ordered chord card, mic strip, instrument, notes, tip: the chord card leads because
 * it is the page's answer and, in its empty state, the only place that says "Chord
 * Finder". Nothing may jump while a chord is being played in, so every element that a
 * reading changes has a fixed height whatever it holds: the chord card pins its line
 * counts (one line of name, two of description), the notes strip is one scrolling row
 * with a placeholder when empty, and the tip strip always carries either the alternative
 * readings or the next step. Nothing appears, disappears or reflows; only the words and
 * colours change. Spacing between every card is [SECTION_GAP].
 */
@Composable
internal fun ChordFinderContent(
    notes: List<Int>,
    reading: ChordReading,
    instrument: ChordInstrument,
    listening: Boolean,
    micEnabled: Boolean,
    heard: Tuner.Reading?,
    amplitude: Float,
    listeningState: ListeningState?,
    micGranted: Boolean,
    micPermanentlyDenied: Boolean = false,
    glow: Pair<Int, Float>? = null,
    /** Bumped on every mic capture, so the instrument scrolls to [glow] even for a repeat note. */
    captureSerial: Int = 0,
    isAdFree: Boolean = false,
    onToggleNote: (Int) -> Unit = {},
    onRemoveNote: (Int) -> Unit = {},
    onClear: () -> Unit = {},
    onSetInstrument: (ChordInstrument) -> Unit = {},
    onToggleMic: () -> Unit = {},
    onRequestMic: () -> Unit = {},
) {
    // The chord's own reading of each note: spelled from the root, labelled by degree.
    val best = reading.bestMatch()
    fun spell(midi: Int): String {
        val pc = ((midi % 12) + 12) % 12
        return best?.spell(pc) ?: NoteNames.nameOf(pc)
    }
    // The octave follows the spelled letter (C♭4 is B3), so it comes from the chord too.
    fun octave(midi: Int): Int = best?.spelledOctave(midi) ?: NoteNames.octaveOf(midi)
    fun degree(midi: Int): String {
        val pc = ((midi % 12) + 12) % 12
        return when (reading) {
            is ChordReading.Identified -> reading.best.degreeOf(pc)?.label ?: "?"
            is ChordReading.Dyad -> reading.powerChord?.degreeOf(pc)?.label
                ?: ChordTheory.degreeFromBass(pc, ((reading.lowMidi % 12) + 12) % 12)
            is ChordReading.Unnamed -> ChordTheory.degreeFromBass(pc, reading.bass)
            is ChordReading.Single -> "R"
            ChordReading.Empty -> ""
        }
    }
    val degreeLabels = remember(reading, notes) { notes.associateWith { degree(it) } }
    val sortedNotes = remember(notes) { notes.sorted() }

    // The nav bar below is the scaffold's, so only the status bar needs padding here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            ChordHero(reading = reading)

            Spacer(Modifier.height(SECTION_GAP))
            MicStrip(
                listening = listening,
                micEnabled = micEnabled,
                heard = heard,
                amplitude = amplitude,
                listeningState = listeningState,
                micGranted = micGranted,
                micPermanentlyDenied = micPermanentlyDenied,
                onToggleMic = onToggleMic,
                onRequestMic = onRequestMic,
            )

            Spacer(Modifier.height(SECTION_GAP))
            InstrumentCard(
                instrument = instrument,
                litMidi = notes.toSet(),
                labels = degreeLabels,
                glow = glow,
                captureSerial = captureSerial,
                listening = listening,
                onSetInstrument = onSetInstrument,
                onToggleNote = onToggleNote,
            )

            Spacer(Modifier.height(SECTION_GAP))
            NotesStrip(
                notes = sortedNotes,
                spell = ::spell,
                octaveOf = ::octave,
                degreeOf = { degreeLabels[it] ?: "" },
                onRemove = onRemoveNote,
                onClear = onClear,
            )

            Spacer(Modifier.height(SECTION_GAP))
            TipStrip(reading = reading)
            Spacer(Modifier.height(24.dp))
        }

        // Same placement as every other tab: pinned under the scrolling content.
        if (!isAdFree) {
            AdBannerView(modifier = Modifier.fillMaxWidth())
        }
    }
}

private val SECTION_GAP = 12.dp

private fun ChordReading.bestMatch(): ChordMatch? = when (this) {
    is ChordReading.Identified -> best
    is ChordReading.Dyad -> powerChord
    else -> null
}

// ── Instrument card ──────────────────────────────────────────────────────────────

/** Width of one natural on the scrolling keyboard: wide enough to tap a sharp between two. */
private val PIANO_NATURAL_WIDTH = 26.dp
private const val PIANO_OCTAVES = 4
private const val PIANO_LOWEST_MIDI = 36   // C2, under a guitar's low E
private val FRET_WIDTH = 44.dp

/** Both instruments draw at one height so switching them never moves what is below. */
private val INSTRUMENT_HEIGHT = 140.dp

@Composable
private fun InstrumentCard(
    instrument: ChordInstrument,
    litMidi: Set<Int>,
    labels: Map<Int, String>,
    glow: Pair<Int, Float>?,
    captureSerial: Int,
    listening: Boolean,
    onSetInstrument: (ChordInstrument) -> Unit,
    onToggleNote: (Int) -> Unit,
) {
    // Gold while the mic is live, matching the tuner's "this is live" language, so a note
    // played in and a note tapped in light the instrument the same way.
    val accent by animateColorAsState(
        targetValue = if (listening) AppColors.gold else AppColors.mediumPurple,
        animationSpec = tween(260),
        label = "instrumentAccent",
    )

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                // The drone header's idiom: the label states what is loaded, not what the
                // card is. For the guitar that is the tuning, the one fact a drop-D player
                // needs before tapping anything; for the piano, how far it scrolls.
                Text(
                    when (instrument) {
                        ChordInstrument.GUITAR -> "STANDARD TUNING"
                        ChordInstrument.PIANO -> "$PIANO_OCTAVES OCTAVES · ${NoteNames.labelOf(PIANO_LOWEST_MIDI)} TO ${NoteNames.labelOf(PIANO_LOWEST_MIDI + PIANO_OCTAVES * 12 - 1)}"
                    },
                    color = AppColors.textDim,
                    fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                ChordInstrument.entries.forEach { option ->
                    AppFilterChip(
                        selected = option == instrument,
                        onClick = { onSetInstrument(option) },
                        label = option.displayName,
                        endPadding = if (option == ChordInstrument.entries.last()) 0.dp else 6.dp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // The piano opens on C3, the octave a guitar or a voice actually lives in.
            LaunchedEffect(instrument) {
                scroll.scrollTo(
                    if (instrument == ChordInstrument.PIANO) with(density) { (PIANO_NATURAL_WIDTH * 7).roundToPx() } else 0,
                )
            }
            // A note played in may land off screen (low E far left, a high note far right on
            // the piano), where its glow goes unseen. Ease the instrument so the note sits
            // centred; on the guitar, of the positions that sound it, the one nearest the
            // current view, so the neck moves as little as possible.
            LaunchedEffect(captureSerial) {
                val midi = glow?.first ?: return@LaunchedEffect
                if (captureSerial == 0) return@LaunchedEffect
                val contentWidth = with(density) {
                    (if (instrument == ChordInstrument.PIANO) PIANO_NATURAL_WIDTH * 7 * PIANO_OCTAVES else FRET_WIDTH * 13).toPx()
                }
                val inset = with(density) { 16.dp.toPx() }
                val viewport = scroll.viewportSize.toFloat()
                val viewCentre = scroll.value + viewport / 2f
                val fractions = if (instrument == ChordInstrument.PIANO) {
                    listOfNotNull(pianoKeyCentreFraction(midi, PIANO_OCTAVES, PIANO_LOWEST_MIDI))
                } else {
                    fretboardPositionFractions(midi)
                }
                val x = fractions.map { inset + it * contentWidth }
                    .minByOrNull { abs(it - viewCentre) } ?: return@LaunchedEffect
                scroll.animateScrollTo(
                    (x - viewport / 2f).roundToInt().coerceAtLeast(0),
                    animationSpec = tween(SCROLL_TO_NOTE_MS, easing = FastOutSlowInEasing),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Crossfade(targetState = instrument, animationSpec = tween(200), label = "instrument") { which ->
                    when (which) {
                        ChordInstrument.GUITAR -> GuitarFretboard(
                            litMidi = litMidi,
                            accent = accent,
                            onPositionTap = onToggleNote,
                            labels = labels,
                            glow = glow,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .width(FRET_WIDTH * 13)
                                .height(INSTRUMENT_HEIGHT)
                                .semantics { contentDescription = "Guitar fretboard, tap a position to add or remove it" },
                        )
                        ChordInstrument.PIANO -> PianoKeyboard(
                            octaves = PIANO_OCTAVES,
                            lowestMidi = PIANO_LOWEST_MIDI,
                            litMidi = litMidi,
                            accent = accent,
                            onKeyTap = onToggleNote,
                            octaveLabels = true,
                            glow = glow,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .width(PIANO_NATURAL_WIDTH * 7 * PIANO_OCTAVES)
                                .height(INSTRUMENT_HEIGHT)
                                .semantics { contentDescription = "Piano keyboard, tap a key to add or remove it" },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Reserved whether or not the bar is currently shown, so the card's height is fixed.
            // The "scroll" hint is for the keyboard, which is cut off at both edges yet does
            // not look draggable; the fretboard gets it too so the two match.
            Box(modifier = Modifier.fillMaxWidth().height(SCROLLBAR_HINT_HEIGHT).padding(horizontal = 16.dp)) {
                FadingHorizontalScrollbar(
                    scrollState = scroll,
                    hint = "scroll",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Microphone strip ─────────────────────────────────────────────────────────────

/** Width reserved for the heard note, so the badge never shifts as the text changes. */
private val HEARD_NOTE_SLOT = 36.dp

/**
 * The tuner's input line, folded into one row: mic toggle, level meter, the note being
 * heard, and the listening-state badge. Same components as under the tuner's needle, so a
 * user who knows one reads the other.
 */
@Composable
private fun MicStrip(
    listening: Boolean,
    micEnabled: Boolean,
    heard: Tuner.Reading?,
    amplitude: Float,
    listeningState: ListeningState?,
    micGranted: Boolean,
    micPermanentlyDenied: Boolean,
    onToggleMic: () -> Unit,
    onRequestMic: () -> Unit,
) {
    // Until the grant arrives the whole strip is the app's shared ask, same shape and height.
    if (!micGranted) {
        MicAccessStrip(permanentlyDenied = micPermanentlyDenied, onClick = onRequestMic)
        return
    }

    val micTint by animateColorAsState(
        targetValue = if (listening) AppColors.gold else AppColors.textDim,
        animationSpec = tween(220),
        label = "micTint",
    )

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleMic,
                    )
                    .semantics {
                        contentDescription = if (micEnabled) "Pause the microphone" else "Start the microphone"
                    },
            ) {
                Icon(
                    imageVector = if (micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = null,
                    tint = micTint,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(12.dp))

            InputLevelMeter(
                amplitude = if (listening) amplitude else 0f,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))

            // The note being heard, in the same slot the tuner's ambient header uses.
            Box(modifier = Modifier.width(HEARD_NOTE_SLOT), contentAlignment = Alignment.CenterEnd) {
                Text(
                    if (listening && heard != null) "${heard.noteName}${heard.octave}" else "",
                    color = AppColors.gold,
                    fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(6.dp))
            ListeningStateBadge(state = listeningState)
        }
    }
}

// ── Notes strip ──────────────────────────────────────────────────────────────────

/** One chip row, always this tall; more chips than fit scroll sideways. */
private val NOTES_STRIP_HEIGHT = 32.dp

@Composable
private fun NotesStrip(
    notes: List<Int>,
    spell: (Int) -> String,
    octaveOf: (Int) -> Int,
    degreeOf: (Int) -> String,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(NOTES_STRIP_HEIGHT),
    ) {
        if (notes.isEmpty()) {
            Text(
                "No notes yet",
                color = AppColors.textDim,
                fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                notes.forEachIndexed { index, midi ->
                    NoteChip(
                        name = spell(midi),
                        octave = octaveOf(midi),
                        degree = degreeOf(midi),
                        isBass = index == 0,
                        onRemove = { onRemove(midi) },
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        // Always present, dimmed when there is nothing to clear, so the row never changes shape.
        Text(
            "Clear",
            color = AppColors.textMuted,
            fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .alpha(if (notes.isEmpty()) 0.35f else 1f)
                .clickable(
                    enabled = notes.isNotEmpty(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClear,
                ),
        )
    }
}

/**
 * One collected note: its spelled name, its octave, and its degree in the chord. The bass
 * carries a gold rim, because it is the note that decided the name.
 */
@Composable
private fun NoteChip(
    name: String,
    octave: Int,
    degree: String,
    isBass: Boolean,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onRemove,
        color = AppColors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = if (isBass) BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.55f)) else null,
        modifier = Modifier
            .height(NOTES_STRIP_HEIGHT)
            .semantics { contentDescription = "$name$octave, $degree. Tap to remove" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 6.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = AppColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)) {
                        append(name)
                    }
                    withStyle(SpanStyle(color = AppColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                        append(octave.toString())
                    }
                },
                lineHeight = 18.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                degree,
                color = if (degree == "R") AppColors.gold else AppColors.textAccent,
                fontSize = 10.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = AppColors.textDim,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ── Chord card: what the notes are ───────────────────────────────────────────────

@Composable
private fun ChordHero(reading: ChordReading) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
            // The reading changes as a whole, so it crossfades as a whole. Every line below
            // has a pinned line count, so the card is the same height for every reading.
            Crossfade(targetState = reading, animationSpec = tween(220), label = "chordHero") { r ->
                Column {
                    // The eyebrow doubles as the page's only name: "CHORD FINDER" in the
                    // resting state, since no tab carries a heading and a beginner needs the
                    // words somewhere; the reading's own label once there is one.
                    Text(
                        when (r) {
                            ChordReading.Empty -> "CHORD FINDER"
                            is ChordReading.Single -> "ONE NOTE"
                            is ChordReading.Dyad -> "INTERVAL"
                            is ChordReading.Identified -> "CHORD"
                            is ChordReading.Unnamed -> "NO COMMON NAME"
                        },
                        color = AppColors.textDim,
                        fontSize = 10.sp, lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    when (r) {
                        ChordReading.Empty -> {
                            HeroSymbol(root = "", suffix = "Play or tap notes")
                            HeroCaption(
                                name = "Name any chord, one note at a time.",
                                about = "Play it as an arpeggio into the mic, or tap it. The lowest note " +
                                    "is the bass; a note below it starts the next chord.",
                            )
                        }
                        is ChordReading.Single -> {
                            HeroSymbol(root = r.name, suffix = "")
                            HeroCaption(
                                name = NoteNames.labelOf(r.midi),
                                about = "One note. Add another to see the interval, a third to name the chord.",
                            )
                        }
                        is ChordReading.Dyad -> {
                            val power = r.powerChord
                            HeroSymbol(
                                root = power?.rootName ?: NoteNames.nameOf(r.lowMidi),
                                suffix = power?.type?.symbol ?: "",
                                slash = if (power == null) " ${NoteNames.nameOf(r.highMidi)}" else "",
                            )
                            HeroCaption(
                                name = r.intervalName + if (power != null) ", a power chord" else "",
                                about = power?.type?.about
                                    ?: "Two notes make an interval, not yet a chord.",
                            )
                        }
                        is ChordReading.Identified -> {
                            val m = r.best
                            HeroSymbol(
                                root = m.rootName,
                                suffix = m.type.symbol + if (m.omittedFifth) "(no 5)" else "",
                                slash = if (m.isSlash) "/" + m.spell(m.bass) else "",
                            )
                            HeroCaption(name = m.fullName, about = m.type.about)
                        }
                        is ChordReading.Unnamed -> {
                            HeroSymbol(
                                root = NoteNames.nameOf(r.bass),
                                suffix = " " + r.pitchClasses.filter { it != r.bass }
                                    .joinToString(" ") { NoteNames.nameOf(it) },
                            )
                            HeroCaption(
                                name = "From the bass: " + r.pitchClasses
                                    .joinToString(" · ") { ChordTheory.degreeFromBass(it, r.bass) },
                                about = "These notes do not form a chord in the dictionary.",
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tip strip: other readings, or what to do next ────────────────────────────────

/**
 * A small dedicated card under the chord: the other readings of these notes when there
 * are any, otherwise the next step. Its own element rather than the chord card's last
 * row, so it reads as a helper rather than part of the result. Fixed height, the same
 * shape as the mic strip; only the words change.
 */
/** Two lines of 15 sp tip, which also clears the 26 dp icon. */
private val TIP_ROW_HEIGHT = 30.dp

@Composable
private fun TipStrip(reading: ChordReading) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Crossfade(targetState = reading, animationSpec = tween(220), label = "chordTip") { r ->
            val alternatives = (r as? ChordReading.Identified)?.alternatives.orEmpty()
            // Fixed row height with room for two lines of tip, so a longer sentence wraps
            // instead of being cut off and the strip is the same height for every reading.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .height(TIP_ROW_HEIGHT),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .background(AppColors.gold.copy(alpha = 0.15f), CircleShape),
                ) {
                    Icon(
                        imageVector = if (alternatives.isNotEmpty()) Icons.Filled.SwapHoriz else Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = AppColors.gold,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (alternatives.isNotEmpty()) "ALSO" else "NEXT",
                    color = AppColors.gold,
                    fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.width(10.dp))
                if (alternatives.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    ) {
                        alternatives.forEach { alt -> GoldPill(text = alt.symbol) }
                    }
                } else {
                    Text(
                        when (r) {
                            ChordReading.Empty -> "Tap the instrument, or play a chord one note at a time."
                            is ChordReading.Single -> "Add a second note for the interval."
                            is ChordReading.Dyad -> "Add a third note to name the chord."
                            is ChordReading.Identified -> "Next chord: start from its lowest note, or tap Clear."
                            is ChordReading.Unnamed -> "Remove a note, or add the missing one."
                        },
                        color = AppColors.textSecondary,
                        fontSize = 11.sp, lineHeight = 15.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The big name: root in gold, quality and slash smaller beside it, sharing a baseline. */
@Composable
private fun HeroSymbol(root: String, suffix: String, slash: String = "") {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = AppColors.gold, fontSize = 44.sp, fontWeight = FontWeight.Black)) {
                append(root)
            }
            withStyle(SpanStyle(color = AppColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)) {
                append(suffix)
            }
            withStyle(SpanStyle(color = AppColors.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Medium)) {
                append(slash)
            }
        },
        // Pinned to the tallest span, so a suffix-only line (the empty state) is as tall
        // as one with a 44 sp root.
        fontSize = 44.sp,
        lineHeight = 50.sp,
        maxLines = 1,
    )
}

@Composable
private fun HeroCaption(name: String, about: String) {
    Spacer(Modifier.height(2.dp))
    Text(
        name,
        color = AppColors.textSecondary,
        fontSize = 13.sp, lineHeight = 17.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        about,
        color = AppColors.textMuted,
        fontSize = 11.sp, lineHeight = 15.sp,
        minLines = 2, maxLines = 2,
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360, heightDp = 780)
@Composable
private fun ChordFinderIdentifiedPreview() {
    val notes = listOf(48, 52, 55, 57)
    ChordFinderContent(
        notes = notes,
        reading = ChordTheory.identify(notes),
        instrument = ChordInstrument.GUITAR,
        listening = true,
        micEnabled = true,
        heard = null,
        amplitude = 0.08f,
        listeningState = ListeningState.QUIET,
        micGranted = true,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360, heightDp = 780)
@Composable
private fun ChordFinderEmptyPianoPreview() {
    ChordFinderContent(
        notes = emptyList(),
        reading = ChordReading.Empty,
        instrument = ChordInstrument.PIANO,
        listening = false,
        micEnabled = false,
        heard = null,
        amplitude = 0f,
        listeningState = null,
        micGranted = false,
    )
}
