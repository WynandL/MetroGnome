# Audio probe

Drives known test sounds through the phone's own speaker while the app listens, then reads back what the engines decided. It was built for the October 2026 audio review (branch `fix/audio-review`). Phone results from that work are summarised in the commit messages and in CLAUDE.md.

**Pieces:**

- **App side, debug builds only:**
  - `debug/tuner/TunerFrameTrace` records the tuner's decision every hop.
  - `src/debug/.../debug/probe/AudioProbeReceiver` takes these adb commands:
    - `play` a WAV from the app's files dir;
    - `trace_start` / `trace_dump` (the tuner trace);
    - `mic_dump` (the last Practice or Speed Trainer mic log).
- **PC side, here:**
  - `p.sh`: adb helpers. Source it from Git Bash: `. tools/audio-probe/p.sh`.
  - `gen.py`: generates the test WAVs (needs numpy): `python tools/audio-probe/gen.py tools/audio-probe/work/wav`.
  - `an.py`, `tl.py`, `bl.py`, `lat.py`: tuner trace analysis.
  - `timing.py`: the beat-to-click delay (A05).
  - Each script takes the `work/` dir first, e.g. `python tl.py "$S" fix octave_replace`.

**Setup each time:**

1. `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. `adb shell cmd media_session volume --stream 3 --set 15`. Remember the old value and restore it afterwards.
3. `adb shell pm grant com.wynandl.metrognome android.permission.RECORD_AUDIO`.
4. Dev mode on (Settings, About card, Build line). Without it the mic diagnostics log stays empty.

## Next session: tests that settle open review items

### T1. Groove Check repeatability (15 ms between two runs, 81.9 vs 66.7)

```
groovecheck 6
```

- Runs six checks back to back from a cleared calibration. Saves `work/res/gc_<i>.xml` and the mic start/stop log `gc_rec.txt`, and prints each latency.
- **Reading it:** a spread of a few ms means the 15 ms was a one-off, so leave it. A spread of 10 ms or more is systematic, and the fix is to average several latency passes in `MicSelfTest.runLatencyPhase`.
- **Also confirm** that every "rec stop" in `gc_rec.txt` lands at its verdict (the A14 cleanup).

### T2. Timing origin (A05): does the latency match the beat-to-click delay?

Scoring computes `onset - beat callback - Groove Check latency`. That is centred only if the mic hears the click about one latency after the callback. Measure it directly; no claps are needed.

1. Make sure the mic is on after T1: `mic_mode_enabled` should be true. If not, turn the Groove Check toggle on in Settings.
2. Make sure the metronome is not muted: the Gnome tab's mute button reads "Unmute" (content-desc) while muted, and a mic log with beats but no "click" rows means it was muted.
3. Gnome tab at 120 BPM: tap "Practice session", then "START PRACTICE". Let it run about 40 s without clapping (the log keeps the last 200 events), then run `micdump practice120`. Change tempo with the "+5" / "-5" buttons; the big number at the top is the BPM. To end: "Cancel practice", then confirm "Stop".
4. Repeat at 200 BPM: `micdump practice200`.
5. Run a Speed Trainer session for about 40 s from the Gnome tab's "Speed Trainer" button, then `micdump trainer`. Its flow has not been driven over adb yet, so read the screen with `ui` first.
6. `python tools/audio-probe/timing.py "$S" practice120 <latency>` for each, using `latency_ms` from `shared_prefs/mic_selftest_calibration.xml` (the value scoring actually subtracts).

**Result before the fix (2026-10-05, S926B):** the click was heard 250-275 ms after the beat callback at 60, 120 and 200 BPM, against a Groove Check latency of ~70 ms, so on-time claps read ~190 ms late. Beats are now logged at their presented time (`onBeatTimed`), so **after the fix** `timing.py` should report the click at about one Groove Check latency after the beat, i.e. "an on-time clap scores about" within ±15 ms of 0, at every tempo and in both Practice and Speed Trainer. It is loud: run it when noise is fine. Each beat should also appear once in the log (they used to be logged twice).

### T3. A06 route pill, only if earbuds or headphones are to hand

Connect them and open Practice, Speed Trainer and the Rhythm Game. Each should show "Groove Check needs the phone speaker", with "Disconnect Bluetooth audio..." or "Unplug headphones...". It should switch back to "Groove Check is on" live when they are disconnected, and a session started while connected should score no claps.

### Tuner regression (optional, about 5 minutes)

```
python tools/audio-probe/gen.py tools/audio-probe/work/wav
pushwav high octave_replace weak_fund_pure burst overlap_dies
tab Tuner
run octave_replace 16 now   # then: python tools/audio-probe/tl.py "$S" now octave_replace
```

**Expected:**

- `high`: every tone within 1 cent.
- `octave_replace`: A4 released about 1.6 s after A5 starts, then A5 locked.
- `weak_fund_pure`: A4 held for the whole 8 s.
- `burst`: A4 held, A5 never locked.
- `overlap_dies`: A4 held while it rings, then A5.
