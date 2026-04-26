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

**`audio/MetronomeEngine.kt`** — Raw `AudioTrack` in STREAM mode for sample-accurate timing. Pre-generates click buffers (click, hi-hat, woodblock). Runs a blocking write loop on `Dispatchers.Default`. The `onBeat` callback fires **before** the audio write (not after) so the UI callback arrives ~16ms early, keeping Compose animations in sync with sound.

**`audio/RhythmDetector.kt`** — Mic input via `AudioRecord`. Onset detection requires both an absolute amplitude threshold AND a 2.5× RMS spike vs. the previous frame — this rejects sustained noise. AEC (Acoustic Echo Cancellation) is enabled to strip the metronome click from mic input; NoiseSuppressor is intentionally **disabled** (it would filter claps). A 60ms suppression window after each click prevents auto-scoring.

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
