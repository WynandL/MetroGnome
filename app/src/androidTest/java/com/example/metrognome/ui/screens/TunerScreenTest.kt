package com.example.metrognome.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.metrognome.audio.tuner.AmbientLevel
import com.example.metrognome.audio.tuner.AmbientReport
import com.example.metrognome.audio.tuner.AmbientTuning
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.audio.tuner.TunerCalibrator
import com.example.metrognome.ui.theme.MetroGnomeTheme
import com.example.metrognome.viewmodel.CalibrationInfo
import com.example.metrognome.viewmodel.CalibrationMode
import com.example.metrognome.viewmodel.CalibrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI tests for [TunerScreenContent] — the stateless tuner UI.
 *
 * Each test renders the content with a fixed set of inputs and asserts on the
 * resulting UI or on the callbacks fired by interaction. Requires a connected
 * device or emulator.
 */
class TunerScreenTest {

    @get:Rule
    val rule = createComposeRule()

    /** Render [TunerScreenContent] with sensible defaults; override per test. */
    private fun render(
        reading: Tuner.Reading? = null,
        ambient: AmbientReport = AmbientReport.Idle,
        calibration: CalibrationState = CalibrationState.Idle,
        calibrationInfo: CalibrationInfo = CalibrationInfo(1f, Float.NaN, calibrated = false),
        micGranted: Boolean = true,
        keepScreenOn: Boolean = false,
        ambientLevel: AmbientTuning.Level = AmbientTuning.Level.MAX,
        onRequestMic: () -> Unit = {},
        onNudgeReference: (Float) -> Unit = {},
        onSetReferenceHz: (Float) -> Unit = {},
        onLoopbackCalibrate: () -> Unit = {},
        onReferenceCalibrate: () -> Unit = {},
        onDismissCalibration: () -> Unit = {},
        onClearCalibration: () -> Unit = {},
        onCalibrateToNote: (Tuner.Reading) -> Unit = {},
        onToggleScreenOn: (Boolean) -> Unit = {},
        onSetAmbientLevel: (AmbientTuning.Level) -> Unit = {},
    ) {
        rule.setContent {
            MetroGnomeTheme {
                TunerScreenContent(
                    reading = reading,
                    amplitude = 0.1f,
                    ambient = ambient,
                    referenceHz = 440f,
                    calibration = calibration,
                    calibrationInfo = calibrationInfo,
                    micGranted = micGranted,
                    keepScreenOn = keepScreenOn,
                    onRequestMic = onRequestMic,
                    onNudgeReference = onNudgeReference,
                    onSetReferenceHz = onSetReferenceHz,
                    onLoopbackCalibrate = onLoopbackCalibrate,
                    onReferenceCalibrate = onReferenceCalibrate,
                    onDismissCalibration = onDismissCalibration,
                    onClearCalibration = onClearCalibration,
                    onCalibrateToNote = onCalibrateToNote,
                    onToggleScreenOn = onToggleScreenOn,
                    ambientLevel = ambientLevel,
                    onSetAmbientLevel = onSetAmbientLevel,
                )
            }
        }
    }

    private fun reading(cents: Float, inTune: Boolean) = Tuner.Reading(
        frequency = 440f, noteName = "A", octave = 4,
        cents = cents, clarity = 0.97f, inTune = inTune,
    )

    // ── Gauge ────────────────────────────────────────────────────────────────────

    @Test
    fun noSignal_showsPlaceholder() {
        render(reading = null)
        rule.onNodeWithText("-").assertIsDisplayed()
    }

    @Test
    fun reading_showsNoteName() {
        render(reading = reading(cents = 9f, inTune = false))
        rule.onNodeWithText("A").assertIsDisplayed()
    }

    @Test
    fun reading_relatesDetectedFrequencyToTheNote() {
        render(reading = reading(cents = 9f, inTune = false))
        rule.onNodeWithText("DETECTED").assertIsDisplayed()
        rule.onNodeWithText("SITS AT", substring = true).assertIsDisplayed()
    }

    @Test
    fun inTuneReading_showsInTuneStatus() {
        render(reading = reading(cents = 0.4f, inTune = true))
        rule.onNodeWithText("IN TUNE").assertIsDisplayed()
    }

    @Test
    fun flatReading_showsTuneUp() {
        render(reading = reading(cents = -12f, inTune = false))
        rule.onNodeWithText("TUNE UP", substring = true).assertIsDisplayed()
    }

    @Test
    fun sharpReading_showsTuneDown() {
        render(reading = reading(cents = 14f, inTune = false))
        rule.onNodeWithText("TUNE DOWN", substring = true).assertIsDisplayed()
    }

    // ── Reference pitch ──────────────────────────────────────────────────────────

    @Test
    fun referenceCard_showsCurrentReference() {
        render()
        rule.onNodeWithText("A4 = 440 Hz").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun referenceCard_hasASlider() {
        render()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertExists()
    }

    @Test
    fun referenceCard_plusButtonNudgesUp() {
        var delta = 0f
        render(onNudgeReference = { delta = it })
        rule.onNodeWithText("+").performScrollTo().performClick()
        assertEquals(1f, delta, 0.001f)
    }

    @Test
    fun referenceCard_minusButtonNudgesDown() {
        var delta = 0f
        render(onNudgeReference = { delta = it })
        rule.onNodeWithText("−").performScrollTo().performClick()
        assertEquals(-1f, delta, 0.001f)
    }

    // ── Frequency rail ─────────────────────────────────────────────────────────

    @Test
    fun frequencyRail_showsOctaveAnchors() {
        render(
            ambient = AmbientReport(
                state = ListeningState.PROFILING,
                headline = "", guidance = "",
                ambientLevel = AmbientLevel.QUIET,
                candidateHz = null,
                stabilityCents = Float.NaN,
                locked = false,
            )
        )
        // The rail always paints its octave anchor labels; A2/A6 are unique on screen.
        rule.onNodeWithText("A2").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("A6").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun frequencyRail_showsCandidateNoteInHeader() {
        render(
            ambient = AmbientReport(
                state = ListeningState.ACQUIRING,
                headline = "", guidance = "",
                ambientLevel = AmbientLevel.QUIET,
                candidateHz = 261.63f,   // C4 — not an octave-anchor label, so unambiguous
                stabilityCents = 5f,
                locked = false,
            )
        )
        rule.onNodeWithText("C4").performScrollTo().assertIsDisplayed()
    }

    // ── Ambient suppression ──────────────────────────────────────────────────────

    @Test
    fun ambientSuppression_cyclesAndFiresCallback() {
        var picked: AmbientTuning.Level? = null
        render(ambientLevel = AmbientTuning.Level.MAX, onSetAmbientLevel = { picked = it })
        rule.onNodeWithText("AMBIENT SUPPRESSION").performScrollTo().assertIsDisplayed()
        // The bars-only control carries the level in its contentDescription and
        // advances one step per tap, wrapping Max → Standard.
        rule.onNodeWithContentDescription("Ambient suppression", substring = true)
            .performScrollTo().performClick()
        assertEquals(AmbientTuning.Level.STANDARD, picked)
    }

    // ── Calibration — idle ───────────────────────────────────────────────────────

    @Test
    fun idleCalibration_micCheckButtonInvokesCallback() {
        var called = false
        render(onLoopbackCalibrate = { called = true })
        rule.onNodeWithText("Microphone Check").performScrollTo().performClick()
        assertTrue(called)
    }

    @Test
    fun idleCalibration_tuningForkButtonInvokesCallback() {
        var called = false
        render(onReferenceCalibrate = { called = true })
        rule.onNodeWithText("Tuning Fork").performScrollTo().performClick()
        assertTrue(called)
    }

    @Test
    fun calibratedInfo_showsClearButtonThatInvokesCallback() {
        var cleared = false
        render(
            calibrationInfo = CalibrationInfo(1.0006f, 0.4f, calibrated = true),
            onClearCalibration = { cleared = true },
        )
        rule.onNodeWithText("Clear Calibration").performScrollTo().performClick()
        assertTrue(cleared)
    }

    // ── Calibration — running ────────────────────────────────────────────────────

    @Test
    fun runningLoopback_showsStayQuietPrompt() {
        render(calibration = CalibrationState.Running(CalibrationMode.LOOPBACK, 0.3f))
        rule.onNodeWithText("Stay quiet", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun runningReference_showsForkPrompt() {
        render(calibration = CalibrationState.Running(CalibrationMode.REFERENCE, 0.3f))
        rule.onNodeWithText("Strike your fork", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    // ── Calibration — results ────────────────────────────────────────────────────

    @Test
    fun doneReference_confidentShowsResultAndDismisses() {
        var dismissed = false
        render(
            calibration = CalibrationState.DoneReference(
                TunerCalibrator.ReferenceResult(
                    correctionFactor = 1.0006f, referenceHz = 440f, measuredHz = 439.7f,
                    spreadCents = 0.4f, sampleCount = 24, confident = true,
                )
            ),
            onDismissCalibration = { dismissed = true },
        )
        rule.onNodeWithText("calibration set", substring = true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Done").performScrollTo().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun doneLoopback_confidentShowsVerifiedResult() {
        render(
            calibration = CalibrationState.DoneLoopback(
                TunerCalibrator.Result(
                    correctionFactor = 1.0002f,
                    accuracyCents = 0.31f,
                    confident = true,
                    perTone = listOf(
                        TunerCalibrator.ToneError(523f, 0.12f, 0.99f),
                    ),
                )
            ),
        )
        rule.onNodeWithText("verified", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun calibrationFailed_showsMessage() {
        render(calibration = CalibrationState.Failed("Microphone permission is required."))
        rule.onNodeWithText("Microphone permission is required.")
            .performScrollTo().assertIsDisplayed()
    }

    // ── Microphone permission ────────────────────────────────────────────────────

    @Test
    fun micNotGranted_showsPromptThatRequestsPermission() {
        var requested = false
        render(micGranted = false, onRequestMic = { requested = true })
        rule.onNodeWithText("Grant Microphone Access").performClick()
        assertTrue(requested)
    }
}
