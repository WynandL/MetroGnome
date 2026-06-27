# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug           # Debug APK
./gradlew assembleRelease         # Release APK (requires keystore.properties + metrognome-release.jks)
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumented tests (requires connected device/emulator)
./gradlew lint                    # Static analysis
./gradlew clean                   # Clean build outputs
```

## Architecture Overview

**MetroGnome** is an Android metronome app with a rhythm game. Single-module Kotlin/Compose project targeting SDK 36, min SDK 24.

### Layer Structure

```
UI (Jetpack Compose screens + Canvas)
    ↓
ViewModels (AndroidViewModel + StateFlow)
    ↓
Audio Engine + Game Logic (Coroutines on Dispatchers.Default)
```

### Key Components

**Audio engine layout** — each engine lives in its own sub-package under `audio/`:

- `audio/metronome/MetronomeEngine.kt` — Raw `AudioTrack` in STREAM mode for sample-accurate timing. Pre-generates a click voice for every sound type, keyed by `soundType` index: free (0 Classic, 1 Hi-Hat, 2 Wood, 3 Warm) and premium (4 Bell, 5 Crystal Bowl, 6 Kalimba, 7 Cowbell), each a synthesized buffer. Runs a blocking write loop on `Dispatchers.Default`. The `onBeat` callback fires **before** the audio write (not after) so the UI callback arrives ~16ms early, keeping Compose animations in sync with sound.
- `audio/rhythm/RhythmDetector.kt`: Mic input via `AudioRecord`, used by all three mic features. A `ClapDetector` tells the metronome click apart from a clap by signature (the 1100 Hz Classic click is low-band dominant; a clap is broadband) and emits **only claps**, so an on-beat clap still scores and a leaked click never does, with no time-suppression window. AEC (Acoustic Echo Cancellation) is enabled when available; NoiseSuppressor is intentionally **disabled** (it would filter claps). Onset timing is localised inside each buffer and mapped to `elapsedRealtime` via `FrameClock` + `AudioRecord.getTimestamp`. Because the classifier is tuned to the Classic voices, mic mode forces the Classic **sound type** (`MetronomeViewModel.setMicSoundOverride` / `effectiveSoundType`); the engine keeps its normal 1100 Hz click and normal 1800 Hz downbeat accent, and `ClapDetector` rejects **both** narrowly (a dedicated 1800 Hz accent band alongside the 1100 Hz click band), so the accent stays clearly audible to the player yet the detector never misreads it as a clap. (The Rhythm Game uses its own engine and plays uniform clicks.)
- `audio/rhythm/AmbientLevelMonitor.kt`: Passive observer of `RhythmDetector.amplitude` (never touches capture/detection). An asymmetric-EMA noise floor (fast-down, slow-up) tells brief claps from a sustained noisy room; exposes `noisy: StateFlow` (drives `ui/components/RoomNoiseNudge` during play) plus `floor`/`everNoisy` for per-session telemetry.
- `audio/tuner/` — Tuner engine: `Tuner.kt` (MPM + FFT pitch detection), `TunerCalibrator.kt` (loopback/reference calibration), `AmbientDetector.kt` (room profiling, speech rejection), `TunerFeedbackConfig.kt` + `TunerFeedbackReporter.kt` (Firestore diagnostic submissions).
- `audio/dsp/` — Shared DSP building blocks used by the engines: `FFT.kt`, `PitchDetector.kt`, `ClapDetector.kt` (spectral click-vs-clap, the only onset path mic mode uses), `BiquadFilter.kt`.
- `audio/NoteNames.kt` — Shared music-theory utility (note name ↔ frequency mapping); stays at the `audio` root because all three engines may reference it.

**Premium sounds and instrument affinity.** `billing/PremiumSoundDef.kt` holds `PREMIUM_SOUND_REGISTRY`, the single source for purchasable sounds (product id, display name, description, `soundType` index). Settings renders a chip plus a paywall dialog per entry automatically; billing reconciliation is generic over `BillingManager.SOUND_PRODUCTS`. To add one, follow the 5-step recipe documented at the top of `PremiumSoundDef.kt`. `ui/components/instruments/` is a text-free instrument-affinity nudge with one manager, `InstrumentAffinity` (maps a `soundType` index to the instruments it suits). Icons are Lucide glyphs (ISC, see `THIRD_PARTY_LICENSES.md`) rendered from path data via `PathParser` in `InstrumentGlyphs`. `InstrumentAffinityRow` sits above the Settings sound chips (matching instruments glow gold, the rest dim as the user taps through sounds); `InstrumentAffinityBadges` shows just the relevant instruments inside the premium paywall dialog so an unowned sound still advertises its fit.

**`viewmodel/MetronomeViewModel.kt`** — Manages play state, BPM, time signature, sound type, volume. Tap tempo requires ≥2 taps within 2.5s. Persists settings to SharedPreferences (`metrognome_prefs`).

**`viewmodel/RhythmGameViewModel.kt`** — State machine: IDLE → COUNTDOWN → PLAYING → RESULT. Notes have 2000ms travel time from spawn to hit line. Hit windows: PERFECT ±50ms, GOOD ±100ms, ALMOST ±150ms (scaled by user tolerance setting). Scoring: PERFECT=100, GOOD=70, ALMOST=30. High scores persisted per difficulty in `rhythm_highscores` SharedPreferences.

**`ui/components/GnomeCanvas.kt`** — Custom Compose Canvas drawing the gnome. Animations (pendulum, bounce, flash, twinkle) are synchronized to `BeatEvent` emissions from MetronomeViewModel. The `feature/metro-cosmetics` branch extends this with a `MetroItem` system: `activeItems`/`onItemTapped` params, tap-hit detection, and separate draw passes for background items, body-attached items, and head-attached items (which bob with the head group).

**`ui/components/metro_items/`** — Cosmetic item system (feature branch only).

- `MetroItem` interface: `draw(u, cx, baseY)`, `hitCenter(u)`, `hitRadius(u)`, `isBodyAttached`, `isHeadAttached`, `previewCenter(canvasW, canvasH, u, baseY)`, `previewRadius(u)`. Background items override `previewCenter`/`previewRadius` to define a tight zoom window for the unlock popup preview; body-attached wearables use `hitCenter`/`hitRadius` for the same purpose.
- `METRO_ITEM_REGISTRY` — single source of truth pairing each `MetroItem` with an `UnlockCondition`. Registry order controls background draw layering (back → front); do not reorder without checking visual correctness.
- `MetroItemTracker` — reads/writes SharedPreferences `"metro_cosmetics"`. Counters: `metronome_seconds` (incremented every 10s while playing), `games_completed` (incremented on game end), `first_launch_ms` (set once on first init). Celebration tracking: `celebrated_item_ids` — written only when the user dismisses the popup ("Sweet!" button), not when the unlock is detected. This ensures a popup bug never permanently silences an earned reward.
- `UnlockCelebrationOverlay` — full-screen animated popup (confetti + spring card). Collected via `vm.newlyUnlocked: SharedFlow` into a `mutableStateListOf` queue in each screen; shown one at a time. `markCelebrated` is called in `onDismiss`, not during emit.
- `ItemPreviewCanvas` — 220×170dp canvas inside the overlay. Background items: translate+scale transform using `previewCenter`/`previewRadius`. Body-attached items: translate+scale using `hitCenter`/`hitRadius` with a boosted u (`size.height/5f`).

**Dev tools in `SettingsScreen`** (visible in debug builds only, gated by `BuildConfig.DEBUG`):
- Toggle cheat mode — all items unlocked while active
- Preview popup for any registry item (cycles with index button)
- Show unlock rules — scrollable dialog sorted easiest → hardest, built from registry at runtime
- Reset all progress — wipes `metronome_seconds`, `games_completed`, `first_launch_ms`, `celebrated_item_ids`

### Color System

All colors are centralized in `ui/theme/Color.kt`. Do **not** add raw `Color(0xFF…)` hex literals to screen or component files — always define in the appropriate object first.

- **`GnomeColors`** — gnome character art palette (skin, hat, suit, shoes, hair, baton, FX). Used exclusively inside `GnomeCanvas.kt` draw functions.
- **`AppColors`** — UI chrome colors shared across screens and components: backgrounds (`background`, `surface`, `surfaceVariant`, `surfaceDeep`, `surfaceDim`, `surfaceActive`), text (`textPrimary`, `textSecondary`, `textMuted`, `textSubtle`, `textDim`, `textAccent`, `textMutedBlue`), brand (`gold`, `primaryPurple`, `mediumPurple`, `deepPurple`, `darkPurple`, `danger`), controls, stop-action colors, overlay preview gradient, confetti list, and dev-tool colors.
- **`GameColors`** — rhythm game semantics: hit quality (`good`, `almost`, `miss` — gold = `AppColors.gold` for PERFECT), note tints (`noteAmber`, `notePurple`), beat dots, mic equalizer (`eqQuiet`), hit line idle (`hitLineIdle`), range label blue (`rangeBlue`).
- **`ItemPalette`** — shared cosmetic-item drawing colors: gold trio (`goldLight`, `goldMid`, `goldDark`) used by GoldChain/GoldEarring/LuxuryWatch; wood duo (`woodBrown`, `woodLight`) used by ForestTree/TorchPost.

Item-specific colors that are unique to one file (mushroom teal, firefly glow, flower petals, moon, gem, watch strap, torch flame/grass) remain as `private val` constants inside each item's object — do not extract these unless they become shared.

**`drawable/ic_launcher_foreground.xml`** — Vector launcher icon foreground derived from GnomeCanvas.kt (u=9, cx=54, baseY=164 → 108×108 viewport). All colours match `GnomeColors`. Hat rotated 11° via `<group android:rotation="11">` matching `drawHat`. Do **not** simplify to circles/rects — the paths are intentionally accurate.

### Navigation

`MainActivity` hosts a `NavigationSuiteScaffold` with 3 tabs: Gnome (metronome), Rhythm (game), Settings.

### Dependencies

Versions managed via `gradle/libs.versions.toml`. Key: Compose BOM 2026.03.01, AGP 9.1.0, Kotlin 2.3.20, coroutines 1.10.2, Google Play Ads 25.1.0.

### Release Signing

Release builds require `keystore.properties` at the project root referencing `metrognome-release.jks`.

### Permissions

- `INTERNET` — Google AdMob ads
- `RECORD_AUDIO` — Rhythm game mic input (requested at runtime before game start)
