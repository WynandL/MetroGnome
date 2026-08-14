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

**MetroGnome** is an Android metronome app with a rhythm game. Single-module Kotlin/Compose project targeting SDK 37, min SDK 24.

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
- `audio/selftest/` — Acoustic-loopback mic self-test ("Groove Check"): `MicSelfTest.kt` plays clicks/claps through the speaker, listens back, and grades the device (GOOD_FIT / USABLE / NOT_FIT). Design principle: the test's job is to *calibrate* the device as far as possible, not to disqualify it - a device fails only when nothing short of magic would fix it, never because a shipped default happened to be wrong for it. Discrimination's hard gates are inseparability (both spectral axes overlap, tolerating a `SEPARABILITY_MIN_FRACTION` minority of outlier claps rather than one flaky sample) and `MIN_CLAP_ONSET_RECALL` (claps not registering as onsets at all, before classification); a low classified click-reject/clap-detect rate on an otherwise-separable device is advisory only (`NoteCode.CLICK_LEAK_CALIBRATED`/`CLAP_MISS_CALIBRATED`), not a failure. The scoring phase reclassifies captured onsets against THIS run's own just-derived per-device thresholds (`deviceClapBandRatio`/`deviceClapFlatnessMin`) rather than the shipped defaults capture ran with all along - so a device is graded on what it will actually do once calibrated, not on its out-of-the-box fit. Small-sample recall bars (`MIN_CLAP_ONSET_RECALL`, `OUT_OF_BAND_MIN_RECALL`) tolerate one miss out of ~12 trials rather than demanding a literally perfect run. On PASS, `SelfTestCalibrationStore` persists the latency constant plus per-device `ClapDetector` thresholds for both acceptance axes (`clap_band_ratio`, `clap_flatness_min`); `MicCalibration` is the single read surface all three mic features use. `SelfTestThresholds.GATE_LOGIC_VERSION` (currently 4) stamps stored verdicts - bump it on any gate change that could flip outcomes (a stale FAIL then reads as never-tested and the check is re-offered; a PASS is never invalidated). Diagnostics are `NoteCode` entries (short id + looked-up text, e.g. `D2`/`S1`), never raw sentences, so Firestore rows stay one line; codes exist for calibration offsets, thin-but-passing margins (`THIN_SEPARATION_MARGIN`), and near-limit passes (`NEAR_VOLUME_FLOOR`/`NEAR_NOISE_CEILING`) as well as hard failures. Outcomes report anonymously to Firestore `mic_self_test` via `cloud/MicCheckReporter`, including spectral margins, the tuned thresholds actually used, and a human-readable SAST timestamp alongside the raw epoch.
- `audio/dsp/` — Shared DSP building blocks used by the engines: `FFT.kt`, `PitchDetector.kt`, `ClapDetector.kt` (spectral click-vs-clap, the only onset path mic mode uses; accepts a clap on flatness OR band-ratio, and both thresholds are per-device-calibratable constructor params fed from `MicCalibration`), `BiquadFilter.kt`.

**Groove Check permission recovery** (added 2026-08-10, shipped v5.17): `MicCalibration.isActive` is a SharedPreferences-only check (`enabled && isCalibrated`) — it says nothing about whether the OS still actually holds `RECORD_AUDIO`. That gap matters because an uninstall/reinstall restores SharedPreferences via Android's default app-data backup (nothing in this repo excludes them — see `AndroidManifest.xml`'s `dataExtractionRules`/`fullBackupContent`, both pointed at empty rule files) while Android *never* restores runtime permission grants, so `isActive` can read true with the mic actually inaccessible. Three places now guard against this, all reading `ContextCompat.checkSelfPermission` live rather than trusting the flag:
- `SettingsScreen.kt`'s Groove Check toggle — shows the real state and routes a stale-permission tap straight to a re-request (skipping the self-test re-run, since the device's calibration constant is still valid).
- `ui/components/MicTimingNudge.kt` (the pill in Practice/Speed Trainer/Rhythm Game) — a dedicated "needs microphone access" state instead of either lying ("Groove Check is on") or going silent.
- `startWithMicPermissionCheck` — a local helper duplicated in `MetronomeScreen.kt` (wraps Practice's and Speed Trainer's Start actions) and `GameCard` in `RhythmGameScreen.kt` (wraps each difficulty tap): if the flag says on but the live permission is missing, it fires the real permission dialog immediately at the moment of starting and only calls the actual start function once that resolves, so a granted permission takes effect for that same session, not just the next one.
- `audio/NoteNames.kt` — Shared music-theory utility (note name ↔ frequency mapping); stays at the `audio` root because all three engines may reference it.

**Premium sounds and instrument affinity.** `billing/PremiumSoundDef.kt` holds `PREMIUM_SOUND_REGISTRY`, the single source for purchasable sounds (product id, display name, description, `soundType` index). Settings renders a chip plus a paywall dialog per entry automatically; billing reconciliation is generic over `BillingManager.SOUND_PRODUCTS`. To add one, follow the 5-step recipe documented at the top of `PremiumSoundDef.kt`. `ui/components/instruments/` is a text-free instrument-affinity nudge with one manager, `InstrumentAffinity` (maps a `soundType` index to the instruments it suits). Icons are Lucide glyphs (ISC, see `THIRD_PARTY_LICENSES.md`) rendered from path data via `PathParser` in `InstrumentGlyphs`. `InstrumentAffinityRow` sits above the Settings sound chips (matching instruments glow gold, the rest dim as the user taps through sounds); `InstrumentAffinityBadges` shows just the relevant instruments inside the premium paywall dialog so an unowned sound still advertises its fit.

**`theory/Meter.kt`** — Single source of truth for time-signature music theory (pure Kotlin, no Android deps, shared by the engine/ViewModel/Settings UI). Classifies a `Meter(top, bottom)` as SIMPLE (top 2-4), COMPOUND (top a multiple of 3, ≥6), or IRREGULAR (everything else: 5, 7, 8, 10, 11...) — classification depends only on the top number, never the bottom (3/8 is simple, not compound, despite the 8). `label()` renders this for display: "Simple quadruple", "Compound duple", and for IRREGULAR either **"Odd"** (when the top number is actually odd — 5, 7, 11, 13) or **"Irregular"** (when it isn't — 8, 10, 14, 16). Do not collapse this back to a single "Odd" label for the whole IRREGULAR class: "odd meter" specifically means an odd number of beats, and mislabeling an even one is a factual music-theory error a musician would catch (caught and fixed 2026-08-14, see `MeterTheoryTest`'s parity test cases). `description()` gives the plain-English feel ("Felt in 2+2+3" for 7/8, "Felt in 2, beats split in three" for 6/8), rendered by `ui/components/TimeSignaturePicker.kt` above the accent-cell grid it explains. `beatGrouping()`/`defaultAccents()` derive the natural accent pattern from the same classification (irregular meters default to twos with a trailing three, e.g. 7 → [2,2,3]) — the Settings UI lets the user override any accent, since the "one true" grouping for an odd meter is genuinely contested.

**`viewmodel/MetronomeViewModel.kt`** — Manages play state, BPM, time signature, sound type, volume. Tap tempo requires ≥2 taps within 2.5s. Persists settings to SharedPreferences (`metrognome_prefs`).

**`viewmodel/RhythmGameViewModel.kt`** — State machine: IDLE → COUNTDOWN → PLAYING → RESULT. Notes have 2000ms travel time from spawn to hit line. Hit windows: PERFECT ±50ms, GOOD ±100ms, ALMOST ±150ms (scaled by user tolerance setting). Scoring: PERFECT=100, GOOD=70, ALMOST=30. High scores persisted per difficulty in `rhythm_highscores` SharedPreferences.

**`ui/components/GnomeCanvas.kt`** — Custom Compose Canvas drawing the gnome. Animations (pendulum, bounce, flash, twinkle) are synchronized to `BeatEvent` emissions from MetronomeViewModel. The `feature/metro-cosmetics` branch extends this with a `MetroItem` system: `activeItems`/`onItemTapped` params, tap-hit detection, and separate draw passes for background items, body-attached items, and head-attached items (which bob with the head group).

**`ui/components/Seal.kt`** — The app-wide "earned and verified" mark: a scalloped rosette with a check, used wherever something is certified (Groove Check pass, achieved streak days, unlocked collection items). Three layers: `SealStyle` (all visual knobs: colors, alpha, scallop count/depth, check stroke; presets `Emblem`, `Badge`, `SealStyle.halo()`), `DrawScope.drawSeal` (the core, for seals inside a larger Canvas), and the `Seal` composable (self-contained element with slow drift rotation on the shared `SEAL_DRIFT_PERIOD_MS` clock, optional one-shot stamp `entrance`). Only the rosette rotates - the check always stays upright. New uses must adapt a `SealStyle`, never redraw the silhouette.

**`ui/components/metro_items/`** — Cosmetic item system (feature branch only).

- `MetroItem` interface: `draw(u, cx, baseY)`, `hitCenter(u)`, `hitRadius(u)`, `isBodyAttached`, `isHeadAttached`, `previewCenter(canvasW, canvasH, u, baseY)`, `previewRadius(u)`. Background items override `previewCenter`/`previewRadius` to define a tight zoom window for the unlock popup preview; body-attached wearables use `hitCenter`/`hitRadius` for the same purpose.
- `METRO_ITEM_REGISTRY` — single source of truth pairing each `MetroItem` with an `UnlockCondition`. Registry order controls background draw layering (back → front); do not reorder without checking visual correctness.
- `MetroItemTracker` — reads/writes SharedPreferences `"metro_cosmetics"`. Counters: `metronome_seconds` (incremented every 10s while playing), `games_completed` (incremented on game end), `first_launch_ms` (set once on first init). Celebration tracking: `celebrated_item_ids` — written only when the user dismisses the popup ("Sweet!" button), not when the unlock is detected. This ensures a popup bug never permanently silences an earned reward. `markCelebrated` also emits on a companion-object `celebrationDismissed: SharedFlow` — a single app-wide signal (MetronomeScreen/RhythmGameScreen/SettingsScreen each hold their own tracker instance over the same prefs file, so this is how one shared collector in `MainActivity` hears a celebration from any of them; currently used to fire the one-time notification-permission ask, see Notifications below).
- `UnlockCelebrationOverlay` — full-screen animated popup (confetti + spring card). Collected via `vm.newlyUnlocked: SharedFlow` into a `mutableStateListOf` queue in each screen; shown one at a time. `markCelebrated` is called in `onDismiss`, not during emit.
- `ItemPreviewCanvas` — 220×170dp canvas inside the overlay. Background items: translate+scale transform using `previewCenter`/`previewRadius`. Body-attached items: translate+scale using `hitCenter`/`hitRadius` with a boosted u (`size.height/5f`).

**Dev tools in `SettingsScreen`** (visible in debug builds only, gated by `BuildConfig.DEBUG`):
- Toggle cheat mode — all items unlocked while active
- Preview popup for any registry item (cycles with index button)
- Show unlock rules — scrollable dialog sorted easiest → hardest, built from registry at runtime
- Reset all progress — wipes `metronome_seconds`, `games_completed`, `first_launch_ms`, `celebrated_item_ids`

### Notifications

`notifications/` holds all FCM/permission logic — nothing outside this package calls a Firebase Messaging API directly:

- `MetroFcmService` — `FirebaseMessagingService` subclass; builds and posts the notification (guarded by a live `POST_NOTIFICATIONS` check, since a granted-at-send-time assumption can be stale). Overrides `onRegistered(installationId)`, not `onNewToken(token)`, to re-subscribe to the broadcast topic on every Firebase Installation re-registration — confirmed 2026-08-14 directly against the resolved `firebase-messaging` bytecode that `onNewToken` is `@Deprecated` in this version, superseded by the `onRegistered`/`onUnregistered` pair. Lint's built-in `MissingFirebaseInstanceTokenRefresh` check doesn't know about that replacement (it only recognises `onNewToken` by name) and is suppressed on the class with an explanation - do not "fix" that finding by adding `onNewToken` back.
- `NotificationChannels` — the single `"general"` channel, id must match `default_notification_channel_id` in `AndroidManifest.xml`. Created idempotently in `MetroGnomeApplication.onCreate`.
- `NotificationTopics` — broadcast model: every consenting device subscribes to one topic, `"all_users"`. No per-device tokens are collected or stored — a message sent to that topic from the Firebase Console (or a Firestore-triggered Cloud Function via the Admin SDK) reaches everyone with zero backend. `MetroGnomeApplication.onCreate` syncs subscription state with the live permission on every launch in both directions (subscribe if granted, **unsubscribe if not**, added 2026-08-14) - the unsubscribe half matters because revoking notification access happens entirely in system Settings, outside any in-app callback, so without it a device that revoked access would stay subscribed (and keep waking for pushes `MetroFcmService` already silently drops) indefinitely.
- `NotificationPermissionState` / `rememberNotificationPermissionState()` — live `POST_NOTIFICATIONS` state (always `true` pre-API 33) plus request/open-settings actions, mirroring the mic-permission pattern in `TunerScreen.kt`. Owned once in `MainActivity`, passed down to `SettingsScreen`.
- `NotificationOptInTracker` — one-shot flag for whether the contextual soft-ask (below) has ever been shown.

**Permission ask, two paths, deliberately not more:**
1. **Contextual, once ever** — fired the first time the app is opened on a 2nd distinct calendar day (`UsageDayTracker.distinctDaysCount() >= 2`, checked in `MainActivity`'s `ON_RESUME` effect - the same counter `LoyaltyDays` items use). Chosen 2026-08-14 over the original first-item-unlock trigger: that one only reached users who engaged with the cosmetic-item system, so plain-metronome users and anyone who already owned every item before this feature shipped could never be asked. Shows `ui/dialogs/NotificationOptInDialog.kt` (a plain "Enable / Not now" card) before ever spending the real system permission dialog. Never re-shown automatically after that.
2. **Self-serve, always available** — the "Notifications" row in `SettingsScreen`, always reading the live OS permission (never a stored flag — tapping while granted opens the system per-app notification screen, since an app can't revoke its own grant).

### What's New

`whatsnew/` — `AppWhatsNew.ALL` is the registry of major-version popup keys (`v3`, `v4`, `v5`); only major versions get one, never minor/patch. `WhatsNewTracker` persists which keys a device has confirmed and hands `MetronomeViewModel` the single next-pending key via `pendingKey()`; `MetronomeScreen` shows it via `WhatsNewOverlayDispatcher` (`ui/overlays/WhatsNewOverlay.kt`) ahead of the item-unlock queue but behind a pending practice result. **Brand-new installs never see any What's New popup** (changed 2026-08-14): the fresh-install guard in `WhatsNewTracker.init` pre-marks every key, including the latest, as already shown. Previously it left the latest key open so new installs got the last major version's popup as a de facto feature ad - removed because "NEW IN VERSION X" copy has no meaning to someone with no "before," and first cold open is the worst point in the funnel to spend a blocking modal advertising one feature out of several the user doesn't know about yet. An upgrading existing user is unaffected either way, since their prefs are never empty at that check.

### Color System

All colors are centralized in `ui/theme/Color.kt`. Do **not** add raw `Color(0xFF…)` hex literals to screen or component files — always define in the appropriate object first.

- **`GnomeColors`** — gnome character art palette (skin, hat, suit, shoes, hair, baton, FX). Used exclusively inside `GnomeCanvas.kt` draw functions.
- **`AppColors`** — UI chrome colors shared across screens and components: backgrounds (`background`, `surface`, `surfaceVariant`, `surfaceDeep`, `surfaceDim`, `surfaceActive`), text (`textPrimary`, `textSecondary`, `textMuted`, `textSubtle`, `textDim`, `textAccent`, `textMutedBlue`), brand (`gold`, `primaryPurple`, `mediumPurple`, `deepPurple`, `darkPurple`, `danger`), controls, stop-action colors, overlay preview gradient, confetti list, and dev-tool colors.
- **`GameColors`** — rhythm game semantics: hit quality (`good`, `almost`, `miss` — gold = `AppColors.gold` for PERFECT), note tints (`noteAmber`, `notePurple`), beat dots, mic equalizer (`eqQuiet`), hit line idle (`hitLineIdle`), range label blue (`rangeBlue`).
- **`ItemPalette`** — shared cosmetic-item drawing colors: gold trio (`goldLight`, `goldMid`, `goldDark`) used by GoldChain/GoldEarring/LuxuryWatch; wood duo (`woodBrown`, `woodLight`) used by ForestTree/TorchPost.

Item-specific colors that are unique to one file (mushroom teal, firefly glow, flower petals, moon, gem, watch strap, torch flame/grass) remain as `private val` constants inside each item's object — do not extract these unless they become shared.

**`drawable/ic_launcher_foreground.xml`** — Vector launcher icon foreground derived from GnomeCanvas.kt (u=9, cx=54, baseY=164 → 108×108 viewport). All colours match `GnomeColors`. Hat rotated 11° via `<group android:rotation="11">` matching `drawHat`. Do **not** simplify to circles/rects — the paths are intentionally accurate.

### Navigation

`MainActivity` hosts a `NavigationSuiteScaffold` with 4 tabs (`AppTab`): Gnome (metronome), Tuner, Rhythm (game), Settings. No `NavController` - `currentTab` is a single `rememberSaveable` enum in `MetroGnomeApp`.

**FCM deep links** (added 2026-08-14): an `openTab` data key on an FCM message (value = an `AppTab` name, e.g. `"rhythm"`, case-insensitive) opens straight to that tab on notification tap. `MainActivity.EXTRA_OPEN_TAB` is the intent-extra key; `MetroFcmService` attaches it to the `PendingIntent` when it builds the notification itself, and for the case where the system posts the notification directly instead (background app, notification+data payload - see that file's kdoc), the FCM SDK copies the data payload onto the launch intent automatically, so no separate handling is needed there either. `MainActivity` is `launchMode="singleTop"` specifically so a tapped notification while the app is already running reuses the instance via `onNewIntent` rather than tearing down and recreating every ViewModel (which would kill an in-progress metronome/tuner/rhythm session). `MetroGnomeApp(openTab, onOpenTabConsumed)` applies it via a `LaunchedEffect` and immediately consumes it (resets to null) so a second notification tap for the same tab still re-triggers. **All tab switching, including this deep link, goes through one `switchTab(tab)` local function** in `MetroGnomeApp` (extracted 2026-08-14 during a pre-publish audit) - the deep-link `LaunchedEffect` originally set `currentTab` directly, silently skipping the RHYTHM-leave guard (`rhythmVm.stopGame()`) and the Tuner/Settings review-prompt trigger that the nav bar's `onClick` already had. Any future tab-switch side effect belongs in `switchTab`, not duplicated at each call site.

### Dependencies

Versions managed via `gradle/libs.versions.toml`. Key: Compose BOM 2026.08.00, AGP 9.3.1, Kotlin 2.4.10, coroutines 1.11.0, Google Play Ads 25.4.0. Note: `implementation(platform(bom))` and `androidTestImplementation(platform(bom))` are both required (BOMs pin per configuration) — the IDE's duplicate-dependency warning on this is a false positive.

### Release Signing

Release builds require `keystore.properties` at the project root referencing `metrognome-release.jks`.

### Permissions

- `INTERNET` — Google AdMob ads
- `RECORD_AUDIO` — Rhythm game mic input (requested at runtime before game start)
