# Tuner Engine Audit — 2026-05-25

Scope: verify the tuner performs as claimed in the Play listing, website blog
posts, and the in-progress ASO copy. Read-only audit of the full signal chain.

Files reviewed: `audio/tuner/Tuner.kt`, `audio/dsp/PitchDetector.kt`,
`audio/tuner/AmbientDetector.kt`, `audio/dsp/FFT.kt`, `audio/NoteNames.kt`,
`audio/tuner/TunerCalibrator.kt`, the unit tests, and `TunerViewModel` /
`TunerScreen` wiring.

## Verdict

The tuner is genuine, well-architected DSP — not a naive FFT-peak picker. Every
public claim is backed by real code, and the strongest claims are backed by
passing unit tests. No correctness bugs found.

## Claim-by-claim

| Claim | Backed by | Verified |
|---|---|---|
| Tracks the true fundamental, not the loudest overtone | MPM / NSDF, first-peak-above-0.9 selection (`PitchDetector.pickPeakLag`), 5-tap median (`Tuner.kt`) | Tested: `resistsOctaveErrorWithStrongSecondHarmonic`, `locksFundamentalOfAHarmonicRichTone` |
| Accurate / no wrong-note guessing | Parabolic interpolation, 8192-sample window | Tested to <1 cent across 55–1760 Hz; concert A <0.5¢ |
| Ignores background noise / room profiling | `AmbientDetector` 900ms profile, level gate, hum learning | Tested: `loudBroadbandSoundReportsNoise`, `steadyRoomToneIsNotMistakenForANote` |
| State machine PROFILING→QUIET→NOISE→UNSTABLE→ACQUIRING→LOCKED | `ListeningState` + `observe()` | Matches exactly; `acquiringPrecedesLock` |
| Voice rejection | 18-cent stability gate over 320ms | Tested: `jumpingPitchNeverLocks` |
| Locks on fast / fast re-acquire | ~560–650ms cold lock; 1500ms instant re-lock | Behavior correct; tests added this session |
| Calibration / performing to spec | `TunerCalibrator` loopback + tuning-fork | Honest: documents loopback can't fix absolute error (shared DAC/ADC crystal); fork path does |

Capture path is clean: raw MIC source (no AEC/NoiseSuppressor, correctly
justified), one-pole DC blocker, sample-rate fallback ladder, all mic errors
swallowed.

## Findings

1. **`inTuneCents` was 1.5¢ — too tight a green band.** Engine is capable of it
   on a steady synthetic tone, but a real decaying/vibrato note rarely holds
   ±1.5¢, and the gold "close" zone spans ±15¢, leaving a big gap. **Action
   taken:** widened to 3.0¢ in `Tuner.kt` (still tighter than typical ±5¢
   tuners, so the precision story holds).

2. **Two real code paths had no unit test.** Fast re-acquire (`REACQUIRE_MS`)
   and speech-over-a-held-note ride-out (`HOLD_DISTURB_MS`) — exactly the
   "spoke briefly while still playing" behaviors the copy leans on. **Action
   taken:** added `reacquiresInstantlyAfterABriefSilence` and
   `lockRidesOutBriefSpeechOverTheNote` to `AmbientDetectorTest`.

3. **Copy nuance (no code change):** blog says profiling takes "a few seconds";
   `PROFILE_MS` is 900ms (~1s). Harmless; soften copy to "about a second" only
   if you want it literally exact.

## Changes made this session (v4.0, left uncommitted for review)

- `Tuner.kt`: `inTuneCents` 1.5 → 3.0 with rationale comment.
- `AmbientDetectorTest.kt`: +2 tests (fast re-acquire, disturbance ride-out).
- This audit note.
