package com.example.metrognome.ui.meter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.theory.Meter
import com.example.metrognome.viewmodel.MetronomeViewModel

/**
 * The meter stage bound to the metronome: the one place that maps [MetronomeViewModel] onto
 * [MeterStageOverlay], and the only way to change the time signature (it used to live in a
 * Settings card).
 *
 * [sessionOpen] is true while a Practice or Speed Trainer session is open, playing or paused:
 * the Speed Trainer counts bars from the meter it started with, so the meter is held until
 * the session ends. Accents may still change (they do not change the bar's length).
 */
@Composable
fun MetronomeMeterStage(
    vm: MetronomeViewModel,
    sessionOpen: Boolean,
    /** The Home play key's own toggle (it fires the manual-stop hook), never a bare togglePlay. */
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
) {
    val top by vm.timeSig.collectAsStateWithLifecycle()
    val bottom by vm.timeSigDenom.collectAsStateWithLifecycle()
    val accents by vm.accentBeats.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.markMeterStageSeen() }

    val inUse = Meter(top, bottom)
    MeterStageOverlay(
        state = MeterStageState(inUse = inUse, accents = accents, playing = playing, bpm = bpm, locked = sessionOpen),
        beats = vm.beatEvents,
        // setMeter on the meter in use resets its accents, so choosing it again is refused here
        // as well as in the stage: only Reset may do that.
        onUse = { m -> if (m != inUse && !sessionOpen) vm.setMeter(m.top, m.bottom) },
        onToggleAccent = { vm.toggleAccent(it) },
        onResetAccents = { vm.setMeter(top, bottom) },
        onPlayToggle = onTogglePlay,
        onClose = onClose,
    )
}
