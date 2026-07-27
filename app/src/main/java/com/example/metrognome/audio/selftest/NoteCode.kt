package com.example.metrognome.audio.selftest

/**
 * Every diagnostic the self-test can raise, as a stable short [id] plus its full
 * [text]. Firestore ([com.example.metrognome.cloud.MicCheckReporter]) stores only the
 * id (e.g. "D2") so rows stay one line and comparable side by side in a spreadsheet -
 * look up what it means here rather than reading a sentence per row. The dev
 * diagnostics overlay ([com.example.metrognome.debug.mic.MicDiagnosticsOverlay]) and
 * the user-facing retry copy ([com.example.metrognome.ui.overlays.MicCheckOverlay])
 * both resolve codes back to [text] for display.
 *
 * Prefix groups by phase: E = environment, L = speaker path / latency, D =
 * discrimination, S = scoring. Never renumber or reuse an id once shipped - old
 * Firestore rows keep whatever id they were written with.
 */
enum class NoteCode(val id: String, val text: String) {
    MIC_PERMISSION_DENIED("E1", "Microphone permission not granted."),
    MIC_UNAVAILABLE("E2", "Microphone unavailable (busy or unsupported)."),
    NOISY_ROOM("E3", "Too much background noise. Find a quieter spot and retry."),
    BLUETOOTH_NOISE_CONFOUND("E4", "Ambient floor measured over Bluetooth: codec noise/hiss can inflate the reading independent of the room."),
    VOLUME_TOO_LOW("E5", "Media volume is too low. Turn it up and retry."),
    NON_SPEAKER_ROUTE("E6", "Calibrated on a non-speaker route; built-in speaker is recommended."),
    NEAR_VOLUME_FLOOR("E7", "Media volume cleared the minimum but with little headroom; a bit louder would make the loopback more reliable."),
    NEAR_NOISE_CEILING("E8", "Ambient noise passed but is close to the ceiling this test allows; a quieter room would give more margin."),

    NO_OUTPUT_TIMESTAMP("L1", "Output timestamp unavailable. Can't measure timing on this device (expected on emulators)."),
    LATENCY_JITTERY("L2", "Speaker/mic timing is jittery, likely the audio route or codec."),
    LATENCY_DRIFTED("L3", "Latency drifted across the run: not reproducible enough to trust."),
    LATENCY_NEAR_CEILING("L4", "Latency is close to the ceiling this app supports; timing may be marginal."),

    INSEPARABLE("D1", "Click and clap spectrally inseparable on both axes: no threshold reliably tells them apart."),
    CLICK_LEAK_CALIBRATED("D2", "Click leaked past the default thresholds but separates cleanly here: a calibration offset, not an unfit device."),
    CLAP_MISS_CALIBRATED("D3", "Claps missed the default thresholds but separate cleanly here: a calibration offset, not an unfit device."),
    CLAP_UNDETECTED("D4", "Some claps never registered as any onset at all (not just misclassified): a genuine detection gap, not calibratable."),
    THIN_SEPARATION_MARGIN("D5", "Click and clap separate on both axes, but by a narrow margin on each; a noisier session could occasionally misclassify one."),

    ON_BEAT_MASKING("S1", "Claps within the masking window can sit on top of the click and be masked: a physical limit, not a failure."),
    EXTRA_ONSETS("S2", "Heard extra onset(s) the test did not emit; a few are normal, many would point to room noise or echo."),
    CAPTURE_OVERRUN("S3", "Capture buffer overran during the test. Timing may degrade under load."),
}
