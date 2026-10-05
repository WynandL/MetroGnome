package com.example.metrognome.audio.metronome

/**
 * When a beat will actually come out of the speaker, on the boot clock
 * (`SystemClock.elapsedRealtime`) the microphone's onsets are stamped on.
 *
 * The engine's `onBeat` fires before the beat's buffer is written, which is right for the
 * animation but is not when the click is heard: on a Samsung S926B the click reached the
 * mic 250-275 ms after the callback at every tempo (the output pipeline), while Groove
 * Check's latency, measured from the hardware presentation timestamp, was ~70 ms. Scoring
 * from the callback therefore read an on-time clap ~190 ms late (audio review A05).
 *
 * The fix is the reference Groove Check already uses: AudioTrack.getTimestamp reports the
 * moment a known frame was presented, and frames are continuous, so the presentation of the
 * beat's first frame follows exactly. Then `onset - presented - latency` is centred.
 */
internal object BeatPresentation {

    /**
     * Boot-clock ms at which frame [beatFrame] (counted from play()) is presented, given an
     * output timestamp: [anchorFrame] was presented at [anchorBootNanos].
     */
    fun fromTimestamp(beatFrame: Long, anchorFrame: Long, anchorBootNanos: Long, sampleRate: Int): Long =
        ((anchorBootNanos + (beatFrame - anchorFrame) * 1_000_000_000.0 / sampleRate) / 1_000_000.0).toLong()

    /**
     * Fallback before the track reports a timestamp (the first beat or two): the frames
     * still queued ahead of [beatFrame] from [nowMs]. It leaves out the device's fixed
     * output latency, so it runs early by that much until a real timestamp arrives.
     */
    fun fromQueue(beatFrame: Long, playbackHeadFrame: Long, nowMs: Long, sampleRate: Int): Long =
        nowMs + ((beatFrame - playbackHeadFrame).coerceAtLeast(0L) * 1000L) / sampleRate
}
