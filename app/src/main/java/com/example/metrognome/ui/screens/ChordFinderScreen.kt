package com.example.metrognome.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import com.example.metrognome.theory.ChordMatch
import com.example.metrognome.theory.ChordReading
import com.example.metrognome.theory.ChordTheory
import com.example.metrognome.ui.components.AppFilterChip
import com.example.metrognome.ui.components.FadingHorizontalScrollbar
import com.example.metrognome.ui.components.GoldPill
import com.example.metrognome.ui.components.GuitarFretboard
import com.example.metrognome.ui.components.PianoKeyboard
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.viewmodel.ChordFinderViewModel
import com.example.metrognome.viewmodel.ChordInstrument

// ── Stateful entry point ─────────────────────────────────────────────────────────

/**
 * The Chord Finder as a full page: play or tap notes, read the chord.
 *
 * Owns the side effects (mic permission, opening and closing the mic with the page, the
 * capture flash); everything drawn lives in the stateless [ChordFinderContent]. Listening
 * starts on entry when the permission is held, matching the tuner, and stops when the page
 * is left, so a chord named here never keeps the mic open behind another screen.
 */
@Composable
fun ChordFinderScreen(
    vm: ChordFinderViewModel,
    onBack: () -> Unit,
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

    DisposableEffect(micGranted) {
        if (micGranted) vm.startListening()
        onDispose { vm.stopListening() }
    }
    BackHandler(onBack = onBack)

    val notes by vm.notes.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    val instrument by vm.instrument.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val heard by vm.heard.collectAsStateWithLifecycle()
    val amplitude by vm.amplitude.collectAsStateWithLifecycle()

    // A captured note flashes on the instrument and ticks in the hand, so a note that
    // arrived from the mic is unmistakably an event rather than a key that changed colour.
    var glowMidi by remember { mutableStateOf<Int?>(null) }
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(vm) {
        // collectLatest: a second note during the fade restarts it on the new key.
        vm.captured.collectLatest { midi ->
            haptics.fire(HapticPattern.TICK)
            glowMidi = midi
            glowAlpha.snapTo(1f)
            glowAlpha.animateTo(0f, tween(GLOW_MS))
        }
    }

    ChordFinderContent(
        notes = notes,
        reading = reading,
        instrument = instrument,
        listening = listening,
        heard = heard,
        amplitude = amplitude,
        micGranted = micGranted,
        micPermanentlyDenied = micPermanentlyDenied,
        glow = glowMidi?.let { it to glowAlpha.value },
        onBack = onBack,
        onToggleNote = vm::toggleNote,
        onRemoveNote = vm::removeNote,
        onClear = vm::clear,
        onSetInstrument = vm::setInstrument,
        onToggleListening = vm::toggleListening,
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
}

private const val GLOW_MS = 700

// ── Stateless content ────────────────────────────────────────────────────────────

/**
 * The whole page as a function of its inputs, so previews and tests can drive every
 * reading. Top to bottom: what the chord is, which notes are in it, an instrument to
 * change them on, and the microphone.
 */
@Composable
internal fun ChordFinderContent(
    notes: List<Int>,
    reading: ChordReading,
    instrument: ChordInstrument,
    listening: Boolean,
    heard: Tuner.Reading?,
    amplitude: Float,
    micGranted: Boolean,
    micPermanentlyDenied: Boolean = false,
    glow: Pair<Int, Float>? = null,
    onBack: () -> Unit = {},
    onToggleNote: (Int) -> Unit = {},
    onRemoveNote: (Int) -> Unit = {},
    onClear: () -> Unit = {},
    onSetInstrument: (ChordInstrument) -> Unit = {},
    onToggleListening: () -> Unit = {},
    onRequestMic: () -> Unit = {},
) {
    // The chord's own reading of each note: spelled from the root, labelled by degree.
    val best = reading.bestMatch()
    fun spell(midi: Int): String {
        val pc = ((midi % 12) + 12) % 12
        return best?.spell(pc) ?: NoteNames.nameOf(pc)
    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 22.dp, top = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.textSecondary,
                )
            }
            Text(
                "Chord Finder",
                color = AppColors.textPrimary,
                fontSize = 16.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (notes.isNotEmpty()) {
                Text(
                    "${notes.size} ${if (notes.size == 1) "NOTE" else "NOTES"}",
                    color = AppColors.textDim,
                    fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            ChordHero(reading = reading)

            AnimatedVisibility(
                visible = notes.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    NotesStrip(
                        notes = sortedNotes,
                        spell = ::spell,
                        degreeOf = { degreeLabels[it] ?: "" },
                        onRemove = onRemoveNote,
                        onClear = onClear,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            InstrumentCard(
                instrument = instrument,
                litMidi = notes.toSet(),
                labels = degreeLabels,
                glow = glow,
                listening = listening,
                onSetInstrument = onSetInstrument,
                onToggleNote = onToggleNote,
            )

            Spacer(Modifier.height(12.dp))
            MicrophoneCard(
                listening = listening,
                heard = heard,
                amplitude = amplitude,
                micGranted = micGranted,
                micPermanentlyDenied = micPermanentlyDenied,
                onToggleListening = onToggleListening,
                onRequestMic = onRequestMic,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun ChordReading.bestMatch(): ChordMatch? = when (this) {
    is ChordReading.Identified -> best
    is ChordReading.Dyad -> powerChord
    else -> null
}

// ── Hero: what the chord is ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordHero(reading: ChordReading) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp)) {
            Text(
                when (reading) {
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
            Spacer(Modifier.height(6.dp))

            // The reading changes as a whole, so it crossfades as a whole; the card's height
            // is pinned by the fixed line counts below so the page never jumps mid-fade.
            Crossfade(targetState = reading, animationSpec = tween(220), label = "chordHero") { r ->
                Column {
                    when (r) {
                        ChordReading.Empty -> {
                            HeroSymbol(root = "Play", suffix = " or tap a few notes")
                            HeroCaption(
                                name = "Name any chord, one note at a time.",
                                about = "Play notes into the microphone or tap them on the instrument below. " +
                                    "The lowest note counts as the bass.",
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
                                    ?: "Two notes make an interval, not yet a chord. Add a third.",
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
                                about = "These notes do not form a chord in the dictionary. " +
                                    "Remove one, or add the note that completes it.",
                            )
                        }
                    }
                }
            }

            // Other readings of the same notes: always a real chord, always worth a look.
            val alternatives = (reading as? ChordReading.Identified)?.alternatives.orEmpty()
            AnimatedVisibility(
                visible = alternatives.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "ALSO READS AS",
                        color = AppColors.textDim,
                        fontSize = 10.sp, lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        alternatives.forEach { alt -> GoldPill(text = alt.symbol) }
                    }
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

// ── Notes strip ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesStrip(
    notes: List<Int>,
    spell: (Int) -> String,
    degreeOf: (Int) -> String,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            notes.forEachIndexed { index, midi ->
                NoteChip(
                    name = spell(midi),
                    octave = NoteNames.octaveOf(midi),
                    degree = degreeOf(midi),
                    isBass = index == 0,
                    onRemove = { onRemove(midi) },
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Clear",
            color = AppColors.textMuted,
            fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(
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
        modifier = Modifier.semantics { contentDescription = "$name$octave, $degree. Tap to remove" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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

// ── Instrument card ──────────────────────────────────────────────────────────────

/** Width of one natural on the scrolling keyboard: wide enough to tap a sharp between two. */
private val PIANO_NATURAL_WIDTH = 26.dp
private const val PIANO_OCTAVES = 4
private const val PIANO_LOWEST_MIDI = 36   // C2, under a guitar's low E
private val PIANO_HEIGHT = 110.dp
private val FRETBOARD_HEIGHT = 158.dp
private val FRET_WIDTH = 44.dp

@Composable
private fun InstrumentCard(
    instrument: ChordInstrument,
    litMidi: Set<Int>,
    labels: Map<Int, String>,
    glow: Pair<Int, Float>?,
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
        Column(modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(
                    "INSTRUMENT",
                    color = AppColors.textDim,
                    fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
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
            Spacer(Modifier.height(10.dp))

            val scroll = rememberScrollState()
            val density = LocalDensity.current
            // Open on the octave a guitar or a voice actually lives in, not the bass end.
            LaunchedEffect(instrument) {
                scroll.scrollTo(
                    if (instrument == ChordInstrument.PIANO) with(density) { (PIANO_NATURAL_WIDTH * 7).roundToPx() } else 0,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Crossfade(targetState = instrument, animationSpec = tween(200), label = "instrument") { which ->
                    when (which) {
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
                                .height(PIANO_HEIGHT)
                                .semantics { contentDescription = "Piano keyboard, tap a key to add or remove it" },
                        )
                        ChordInstrument.GUITAR -> GuitarFretboard(
                            litMidi = litMidi,
                            accent = accent,
                            onPositionTap = onToggleNote,
                            labels = labels,
                            glow = glow,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .width(FRET_WIDTH * 13)
                                .height(FRETBOARD_HEIGHT)
                                .semantics { contentDescription = "Guitar fretboard, tap a position to add or remove it" },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            FadingHorizontalScrollbar(
                scrollState = scroll,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (instrument == ChordInstrument.PIANO)
                    "Tap a key to add or remove a note."
                else
                    "Tap a fret to add or remove a note. Every place a note lives is marked.",
                color = AppColors.textMuted,
                fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

// ── Microphone card ──────────────────────────────────────────────────────────────

@Composable
private fun MicrophoneCard(
    listening: Boolean,
    heard: Tuner.Reading?,
    amplitude: Float,
    micGranted: Boolean,
    micPermanentlyDenied: Boolean,
    onToggleListening: () -> Unit,
    onRequestMic: () -> Unit,
) {
    val keyTint by animateColorAsState(
        targetValue = if (listening) AppColors.gold else AppColors.primaryPurple,
        animationSpec = tween(260),
        label = "micKeyTint",
    )
    val hearing = listening && heard != null

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Text(
                "MICROPHONE",
                color = AppColors.textDim,
                fontSize = 10.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(10.dp))

            if (!micGranted) {
                Text(
                    if (micPermanentlyDenied)
                        "Microphone access was blocked. Enable it in App Settings to play notes in."
                    else
                        "Play notes on your instrument and they appear above. Needs the microphone.",
                    color = AppColors.textSecondary,
                    fontSize = 12.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    if (micPermanentlyDenied) "Open App Settings" else "Grant Microphone Access",
                    onRequestMic,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RaisedControl(
                    onClick = onToggleListening,
                    shape = CircleShape,
                    tint = keyTint,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = if (listening) "Pause the microphone" else "Start the microphone",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val (headline, guidance) = when {
                        hearing -> "Hearing ${heard!!.noteName}${heard.octave}" to
                            "Hold it steady and it joins the chord."
                        listening -> "Listening" to
                            "Play one note at a time, like an arpeggio. A strummed chord cannot be pulled apart yet."
                        else -> "Microphone paused" to
                            "Tap the mic to play notes in. Tapping the instrument still works."
                    }
                    Crossfade(targetState = headline, animationSpec = tween(200), label = "micHeadline") { text ->
                        Text(
                            text,
                            color = if (hearing) AppColors.gold else AppColors.textSecondary,
                            fontSize = 13.sp, lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        guidance,
                        color = AppColors.textMuted,
                        fontSize = 11.sp, lineHeight = 15.sp,
                        minLines = 2, maxLines = 2,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LevelBar(level = if (listening) amplitude else 0f, live = hearing)
        }
    }
}

/** A thin input meter: the tuner's level language, without its gauge. */
@Composable
private fun LevelBar(level: Float, live: Boolean) {
    val frac by animateFloatAsState(level.coerceIn(0f, 1f), animationSpec = tween(90), label = "micLevel")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppColors.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (live) GameColors.good else AppColors.mediumPurple),
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360, heightDp = 780)
@Composable
private fun ChordFinderIdentifiedPreview() {
    val notes = listOf(48, 52, 55, 59)
    ChordFinderContent(
        notes = notes,
        reading = ChordTheory.identify(notes),
        instrument = ChordInstrument.PIANO,
        listening = true,
        heard = null,
        amplitude = 0.3f,
        micGranted = true,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360, heightDp = 780)
@Composable
private fun ChordFinderEmptyGuitarPreview() {
    ChordFinderContent(
        notes = emptyList(),
        reading = ChordReading.Empty,
        instrument = ChordInstrument.GUITAR,
        listening = false,
        heard = null,
        amplitude = 0f,
        micGranted = false,
    )
}
