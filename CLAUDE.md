# CLAUDE.md

Guidance for Claude Code in this repository. Rules here are the ones that block a plausible wrong fix; history and measurements live in git (the full pre-trim file is commit `c22a109`) and in code KDoc.

## Build Commands

```bash
./gradlew assembleDebug           # Debug APK
./gradlew assembleRelease         # Release APK (requires keystore.properties + metrognome-release.jks)
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumented tests (device/emulator)
./gradlew lint                    # Static analysis
./gradlew clean
```

## Project basics

**MetroGnome** is an Android metronome app with a rhythm game, tuner, drone and chord finder. Single-module Kotlin/Compose, target SDK 37, min SDK 24.

- **Branches:** technical work happens on `v7.0` (latest production release v7.2/101, 2026-11-21). Tag `v7.0-pre-sound-stage` (5e06338) is the revert point. `v6.0` is finished: touch it only for a fix the dev explicitly asks to ship on the old line. Website work stays on `master`.
- **Package vs applicationId:** source lives under `com.example.metrognome` (the `namespace`), so paths are `app/src/main/java/com/example/metrognome/...`; a search under `com/wynandl/` finds nothing. The published `applicationId`, Play Store id, Firebase config and every external reference use `com.wynandl.metrognome`.
- **Layers:** Compose UI (screens + Canvas) -> ViewModels (`AndroidViewModel` + `StateFlow`) -> audio engines and game logic (coroutines on `Dispatchers.Default`).
- **Navigation:** `MainActivity` hosts a `NavigationSuiteScaffold` with 5 tabs (`AppTab`: Gnome, Tuner, Chords, Rhythm, Settings). No `NavController`; `currentTab` is one `rememberSaveable` enum in `MetroGnomeApp`. Five is the bottom bar's maximum, so labels stay one word. **All tab switching, deep links included, goes through `switchTab(tab)` in `MetroGnomeApp`**; any tab-switch side effect (stopping the rhythm game, stopping the drone on Chords, review-prompt triggers) belongs there.
- **FCM deep links:** an `openTab` data key (an `AppTab` name, case-insensitive) opens that tab via `MainActivity.EXTRA_OPEN_TAB`. `MainActivity` is `singleTop` so a tap reuses the instance via `onNewIntent` instead of killing running sessions. `MetroGnomeApp(openTab, onOpenTabConsumed)` consumes it so a repeat tap re-triggers.
- **Keep screen on is one app-wide setting:** `MetronomeViewModel.keepScreenOn` (prefs `keep_screen_on`), toggled only on Home, applied by one `DisposableEffect` in `MetroGnomeApp`. Never add the flag or the button to a screen.

## Audio engines (`audio/`)

### Metronome (`audio/metronome/MetronomeEngine.kt`)

- Raw `AudioTrack` in STREAM mode, blocking write loop on `Dispatchers.Default`. Beat lengths come from `BeatFrameClock`, which carries the fractional frame left over each beat (rounding each beat alone drifts).
- Pre-generates a voice per `soundType` index, all looked up through `voiceFor` (live beat and both previews): free 0 Classic, 1 Hi-Hat, 2 Wood, 3 Warm; premium 4 Bell, 5 Crystal Bowl, 6 Kalimba, 7 Cowbell, 8 Metal Kick, 9 Studio Hi-Hat (`HiHatVoice`: closed on the beat, open on the accent, so the accent is heard by length).
- **A voice must survive a phone speaker**, which puts out almost nothing below ~300 Hz. The Metal Kick gets through by saturation (an asymmetric `tanh` driving a low body toward a square wave, whose harmonics carry the thump). Each hit normalises to its own `volume` with a 1 ms fade-in.
- **Every envelope must reach zero.** `(1 - p)^n` envelopes do by construction; any exponential envelope needs an explicit fade-out (the Metal Kick fades its last 60 ms). `buildBeatBuffer` fades a voice cut at the beat boundary over 20 ms.
- **Any new voice gets a test** shaped like `MetalKickTest` / `HiHatVoiceTest` (reads the engine's private buffers on the JVM; asserts its own bars, e.g. RMS vs peak, level through a simulated 350 Hz high-pass speaker, silent tail).
- The stopped preview is one bar with its accent first.
- **Timing callbacks:** `onBeat` fires before the audio write, a whole output buffer (~250 ms) ahead of the sound. **Anything timed against the mic or the screen uses `onBeatTimed` / `BeatEvent.presentedMs`** (boot-clock time the beat's first frame is presented, from `AudioTrack.getTimestamp` + frames written, `BeatPresentation`), the same reference Groove Check's latency uses, so `onset - presented - latency` is centred. Practice and Speed Trainer stamp beats this way and hand `SessionAnalyzer` latency-corrected onsets.
- `practice_completed` / `speed_trainer_completed` carry `clap_offset_ms` (`SessionAnalyzer.medianGridOffsetMs`) and `clap_count`, sent only when the rhythm was confident: the field check that the timing reference centres near 0 on every phone model.

### Mic input (`audio/rhythm/`)

- `RhythmDetector.kt`: `AudioRecord`, used by Practice, Speed Trainer and the Rhythm Game. `ClapDetector` tells click from clap by spectrum and emits only claps (no time-suppression window). AEC on when available; **NoiseSuppressor deliberately disabled** (it filters claps). Onset times map to `elapsedRealtime` via `FrameClock` + `AudioRecord.getTimestamp`.
- Mic mode forces the Classic sound type (`MetronomeViewModel.setMicSoundOverride` / `effectiveSoundType`), because the classifier is tuned to the 1100 Hz click and 1800 Hz accent; `ClapDetector` rejects both bands narrowly so the accent stays audible.
- `RoomNoiseMonitor.kt`: passive observer of `RhythmDetector.amplitude` (asymmetric-EMA floor), exposes `noisy` for `RoomNoiseIndicator`. Its floor is private; nothing is wired to telemetry.

### Tuner (`audio/tuner/`)

`Tuner.kt` (MPM + FFT), `TunerCalibrator.kt`, `AmbientDetector.kt`, `HarmonicPresenceGuard.kt`, `TunerFeedbackConfig.kt` + `TunerFeedbackReporter.kt` (Firestore).

- **A lock follows the note physically sounding.** The presence probe cannot tell a held note from its integer multiple, so when the pitch is a clear 2x/3x/4x of the lock, `HarmonicPresenceGuard` confirms only if `PitchDetector.partialEvidence` finds the old note's non-shared partials 20 dB above the floor. Below ~36 Hz `partialEvidence` returns NaN and the guard keeps the probe's answer: **"cannot judge" must never be read as "absent"**. `HarmonicPresenceTest` covers it.
- `AmbientDetector` bounds the total unconfirmed time (`absentRun`) whatever mix of silence and wrong notes makes it up, and never locks on an outlier frame (current pitch must agree with the ring's median).

**Drone / mic exclusivity.** `TunerViewModel.resumeListening()` is the only place `tuner.start()` is called outside the initial entry. It re-checks every holder (screen away via `listenIntent`, calibration run, drone, drone voice preview) plus the live permission; every path that ends one of those calls back into it. Starting the drone closes the mic (the tuner would lock on the app's own tone); `DronePanel` and `AmbientPanel(listeningPaused)` say so on screen. `runCalibration` stops the drone. Leaving the Tuner tab (`stopListening()`) does **not** stop the drone; it ends on explicit stop or `onCleared`. `DroneState.playing` is never persisted.

### Drone (`audio/drone/`)

Four files, one job each: `DroneVoice.kt` (pure data: `DroneTimbre`, `DroneBlend`, `buildVoice()` -> `VoiceLayout`), `DroneRenderer.kt` (pure-Kotlin synthesis), `DroneEngine.kt` (AudioTrack + coroutine), `DroneState.kt` (persisted settings, stores a MIDI number never Hz).

- **Centre-strand invariant:** every timbre is a stack of detuned, panned, LFO-breathing strands with exactly one undetuned, unmoving, centred strand (`DroneVoiceTest`). That is what keeps it an exact pitch reference.
- **Timbres must be harmonic.** Inharmonic partials make the app's own tuner misread the drone (`DroneRendererTest`). The Crystal Bowl click may be inharmonic; the drone may not.
- **Blend intervals are just** (3:2, 2:1). Root + just fifth correctly reads as the octave below in a period detector (missing fundamental), so blend tests measure each tone on its own frequency.
- **Nothing steps:** raised-cosine start/stop, log-frequency glides, one-pole level, and timbre/blend changes crossfade **linearly** (the voices are correlated; equal-power bulges). `DroneRendererTest.noTransitionProducesADiscontinuity` guards it.
- Sub-cent accuracy is measured by phase difference, not `PitchDetector` (which has a few cents of bias); `PitchDetector` is used at a 5-cent bar only.
- `NoteNames.frequencyOf(midi, referenceHz)` is the single place a note becomes a frequency (tuner and drone share the reference pitch). `audio/NoteNames.kt` stays at the `audio` root.
- **UI:** the card shows the keyboard (`DroneKeyboard`, no letters: light naturals `AppColors.keyNatural`, near-black sharps; the contrast is load-bearing; it carries a `contentDescription`), octave, a voice pill (`GoldPill(secondaryText =)`) and the play key. The pill opens `ui/sounds/DroneSoundStage.kt` (TIMBRE / BLEND tabs). Only volume sits behind the chevron, and the header shows its level. `TunerViewModel.droneExpanded` (prefs `drone_panel_expanded`). The note readout is pinned to the widest note and Hz measured with `rememberTextMeasurer`, so the row fits at 360 dp with 1.3x fonts.
- **Usage:** drone time is its own counter (`MetroItemTracker.addDroneSeconds`, `drone_seconds`), credited in 10 s slices by `TunerViewModel.startDroneTimer`, separate from `tuner_seconds`. It earns 1 Gnote/min capped at 30/day (lowest in the app, since it can run beside the metronome unattended). A paywall audition never counts. Reward: `items/DroneBee.kt` at `UnlockCondition.DroneSeconds(1_800)`.
- **Paid voices:** Reed, String (timbres) and Fifth (blend); Pure, Warm, Root, Octave are free. `billing/PremiumDroneDef.kt` is the only place that knows which voices cost money. See Billing below for the shared rules.
- **Audition:** `TunerViewModel.previewDroneVoice` drives the real drone at the chosen note (the mic stands aside); over a sounding drone the preview crossfades in and back out, and **replaces** the live voice rather than layering (`DroneRendererTest.anAuditionReplacesTheLiveVoiceRatherThanLayeringOverIt`). The stage reads "Sounding <voice> on its own" in gold. A silent-drone audition floors level at `MIN_PREVIEW_VOLUME` (0.35); over a sounding drone the level is untouched. The restore runs in `finally` under `NonCancellable`.
- **Analytics:** `drone_started` (note label like "A3", timbre, blend, reference_hz), `drone_stopped` (duration_seconds, explicit stop only), `drone_voice_changed`, `drone_voice_previewed` (product_id).

### Groove Check (`audio/selftest/`)

`MicSelfTest.kt` plays clicks/claps through the speaker, listens back and grades GOOD_FIT / USABLE / NOT_FIT.

- **Principle: calibrate, don't disqualify.** A device fails only when nothing short of magic would fix it. Hard gates: inseparability (both spectral axes overlap, tolerating a `SEPARABILITY_MIN_FRACTION` minority) and `MIN_CLAP_ONSET_RECALL`. A low classified reject/detect rate on a separable device is advisory (`NoteCode.CLICK_LEAK_CALIBRATED` / `CLAP_MISS_CALIBRATED`). Scoring reclassifies onsets against this run's own derived thresholds (`deviceClapBandRatio` / `deviceClapFlatnessMin`). Small-sample recall bars tolerate one miss in ~12.
- On PASS, `SelfTestCalibrationStore` persists latency + per-device `clap_band_ratio` / `clap_flatness_min`; `MicCalibration` is the single read surface for all mic features.
- **Bump `SelfTestThresholds.GATE_LOGIC_VERSION` (currently 5) on any gate change that could flip outcomes.** A stale FAIL then reads as never-tested; a PASS is never invalidated.
- Diagnostics are `NoteCode` entries (short ids like `D2`, `S1`), never raw sentences. Reported anonymously to Firestore `mic_self_test` via `cloud/MicCheckReporter` (margins, thresholds used, SAST timestamp + epoch).
- **Valid only on the route it was measured on.** `MicCalibration.isUsable` (= `isActive` AND current route == stored route) gates every scoring session (Practice, Speed Trainer, Rhythm Game, the Classic override); off-route the session runs unscored and `MicTimingNudge` shows "Groove Check needs the phone speaker". `isActive` alone drives the Settings toggle and permission recovery. Route = `AudioRouteMonitor`'s heuristic; mid-session route changes are not handled. Supporting other headsets is deliberately out of scope.
- **Route pre-flight:** `MicCheckOverlay` polls `AudioRouteMonitor.currentRoute()` and disables Start off the speaker. A FAIL with `NoteCode.NO_OUTPUT_TIMESTAMP` (L1) off-speaker offers "switch to the speaker and try again"; L1 on the speaker is a terminal "Not available on this phone" with no retry. The dev-tools "Mic self-test" opens `MicDiagnosticsOverlay`, a different overlay.

**Permission recovery.** `MicCalibration.isActive` is prefs-only, and an uninstall/reinstall restores prefs (default backup) but never runtime grants. `ui/components/MicPermissionRecovery.kt` (`rememberMicPermissionRecovery()`) is the single shared source, reading `ContextCompat.checkSelfPermission` live:
- `startWithMicPermissionCheck(start)` wraps session starts (Practice/Speed Trainer Start, each Rhythm `GameCard` difficulty); if the permission is missing it asks first and starts once resolved.
- `fixPermission()` is what `MicTimingNudge`'s "needs microphone access" pill and Settings' Groove Check toggle call on a calibrated device: a lightweight re-request (or App Settings if permanently denied), never the full Groove Check.

### Shared DSP (`audio/dsp/`)

`FFT.kt`, `PitchDetector.kt`, `ClapDetector.kt` (accepts a clap on flatness OR band ratio, both per-device params from `MicCalibration`), `BiquadFilter.kt`, `OnsetDetector.kt` (SuperFlux, see Chord Finder).

## Monetisation

### Billing

- **There is exactly one `BillingManager`**, owned by `MetronomeViewModel`. `TunerViewModel` never makes its own. `MainActivity` gathers state into a `billing/PurchaseStore` snapshot for `TunerScreen` and pushes owned ids into `TunerViewModel.setOwnedProducts`, which reverts a locked timbre/blend to its free fallback (crossfading). **That guard must not skip when the set is unchanged** (it starts empty, so an early return would leave a paid voice from a previous install playable).
- `BillingManager.SOUND_PRODUCTS` means "things you buy that make a sound": click voices and drone voices share it, and reconciliation is generic over it.
- `BillingManager.purchaseError: StateFlow<String?>` is set on any code outside `OK` / `USER_CANCELED` / `ITEM_ALREADY_OWNED`, including every early return in `launchPurchaseFlowFor`, via `errorMessageFor`. The failed-purchase Toast lives in `MetroGnomeApp` (not a screen), since several screens sell. A Buy tap while disconnected shows the error and reconnects.
- `PurchasableItemDef.unlockAlternative` (the free-earn path) renders **only** inside the purchase dialog (`PurchasableItemDialog`'s `belowDescription`), never in the Settings Items row. `PremiumPurchaseDialog` sells only items (Glissie).
- **Analytics:** `iap_started` (product_id), `iap_result` (product_id, response: purchased, pending, cancelled, already_owned, error_<code>, error_not_ready, error_no_details). Named `iap_*` to avoid Firebase's reserved purchase events.

### Click sounds and the sound stage

- `billing/ClickSounds.kt` holds `CLICK_SOUNDS`, the single list of every click voice (index, stable `slug` sent as `sound_type`, name, tagline, description = the Play Console product description verbatim under 200 chars, product id or null). `PREMIUM_SOUND_REGISTRY` (`billing/PremiumSoundDef.kt`) is derived from it. **To add a sound, follow the recipe at the top of `ClickSounds.kt`** (the dev creates the product in Play Console by hand; the website needs updating too, see Website). `SoundGlyphsTest` keeps the list, glyphs, `SOUND_PRODUCTS` and `InstrumentAffinity` in step. Products: `sound_bell`, `sound_bowl`, `sound_kalimba`, `sound_cowbell`, `sound_metal_kick`, `sound_studio_hihat`.
- **The sound stage is the only place to choose a click** (Settings' Sound card is Groove Check and Click Volume). Home's top-right `GoldPill` names the click with its glyph and opens it; the Gnote pill beside it is mark + number only. Both pills are capped to the width beside the BPM box and ellipsize.
- `ui/sounds/SoundStage.kt` is stateless (`StageSound`, `SoundStageState`, callbacks; `tabs`/`tabIndex` for the drone's TIMBRE/BLEND). `MetronomeSoundStage.kt` and `DroneSoundStage.kt` are its two bindings. Every text slot is pinned so nothing moves between sounds. Status reads IN USE / FREE / YOURS / PREMIUM; the gold star marks premium and stays after purchase.
- **A preview over a running metronome replaces the click, never layers**: `MetronomeEngine.playPreview` makes the play loop sound the previewed voice for 4 beats (or 2.4 s) at the live tempo, then hands back; stopped or muted it uses a one-shot track. `engine.previewing` drives "Sounding <sound> on its own". During a mic-scored session the button reads "Preview after your session" and does nothing.
- Buying is `MetronomeViewModel.buySound`, which selects the voice when the purchase lands; a voice no longer owned drops to Classic.
- Glyphs are `AppIcons.Sound*` (rows in `tools/icons/gen_icons.py`), looked up by slug in `ui/sounds/SoundGlyphs.kt`; drone glyphs via `DroneGlyphs.kt` (exhaustive `when`). Medallion glyphs draw with a 2 dp line. `ui/components/instruments/`: `InstrumentAffinity` maps a sound to instruments; `InstrumentAffinityBadges` shows them (Lucide glyphs, ISC, see `THIRD_PARTY_LICENSES.md`).
- `ui/scene/NightScene.kt` (`drawNightSky`, `drawPineForest`, `starField`) and `ui/scene/StageSpotlight.kt` (`drawStageSpotlight`, `drawStageDisc`, `StagePageDots`) are shared by every stage. `ui/components/PinnedText.kt` holds `PinnedSlot` / `PinnedLines`.
- **Analytics:** `sound_stage_opened` (source), `sound_previewed` (sound_type, product_id or "free", source), `sound_changed` (sound_type, source: stage/purchase/ownership/dev; `settings` rows predate the stage), `metronome_started.sound_type` as a slug.

### Ads and ad-free

- **Ad-free has three sources; `MetronomeViewModel.isAdFree` is their OR:** Remove Ads bought (`isAdFreePurchased`), daily-Gnotes ad-free days (`RewardManager`), the ad-free trial (`adFreeTrial.active`). Every ad decision reads `isAdFree`; anything saying *why* reads the sources. Only a purchase shows "You're ad-free"; the others show `ui/components/AdFreeTimeLeft` above a purchase still on offer.
- **Timed ad-free runs on one clock, `adfree/AdFreeWindow`** (pure, `AdFreeWindowTest`): within a boot `elapsedRealtime`, after a reboot the wall clock since start, a clock before start ends it, consumption only ratchets up. **The anchor lives in `ad_free_anchors.xml`, excluded from backup and device transfer in both `res/xml` rule files**; keep it excluded (a restored anchor could be misread as the same boot). Start, duration, used time and declines stay in backed-up prefs so a reinstall cannot re-arm the trial. User actions write with `commit`, the ratchet with `apply`. Expiry is always recomputed (`refreshAdFree()` every 20 s via `AD_FREE_TICK_MS`, and on resume), never one long `delay`.
- **Trial:** `adfree/AdFreeTrialPolicy` (pure, tested) + `adfree/AdFreeTrialManager` (owned by `MetronomeViewModel`). 24 h, offered from the 3rd distinct usage day, never to owners or anyone on Gnotes days, once per install; "Not now" re-offers once 3 days later. Buying ends it silently. The "ads are back" card shows once, only if ads really are back; its button calls `purchaseRemoveAdsFromTrial`. Cards (`ui/overlays/AdFreeTrialOverlay.kt`) are hosted once in `MetroGnomeApp` and wait for a calm moment held 1.5 s (nothing sounding, no session, no poll card, no modal, resumed, no interstitial in 10 s). They use `CelebrationCard` (shared with the unlock popup). The review prompt stays quiet in a session that showed a trial card. Analytics: `ad_free_trial_offered` / `_accepted` / `_declined` (offer_number), `_ended` (reason), `_buy` (source).
- **`ModalPresence` (`ui/overlays/ModalPresence.kt`)** knows whether any modal is up. Open every Compose dialog through `AppWindowDialog` (never `androidx.compose.ui.window.Dialog` directly) and give every full-screen overlay `Modifier.modalScrim()`.
- `AdManager.maybeShow` commits an ad 3 s before showing and re-reads `AdManager.adFreeNow` at show time. It calls `preload()` on every opportunity, so it self-heals.
- **`ads/RewardedAdManager.kt` needs its own retry**: nothing in the UI re-triggers a load while no ad is loaded, so `onAdFailedToLoad` retries via `Handler.postDelayed` from 30 s doubling to 5 min, reset on success. Any future ad loader not re-triggered by the UI needs the same backoff.
- Tuner and other calm tabs get no interstitials; rewarded ads are never `isAdFree`-gated.

## Gnotes and items

### Gnotes

- `points/PointsManager.getSnapshot()` never caches. **The trap is `MetronomeViewModel.gnoteCount`**, a cached `StateFlow` that both the Home pill and the Rhythm `PointsCard` read; it updates only on a `PointsBannerQueue` event, `recordUsageDay()`, or an explicit `_gnoteCount.value = pointsManager.getSnapshot().total`. **Any path that credits Gnotes without a banner must post `PointsBannerQueue.postSilentEarn()`** (or, inside `MetronomeViewModel`, refresh directly as the 10 s metronome loop does).
- `ui/components/GnoteTickFlair.kt`: wrap any live total; fires a spark burst on a rise after mounting, never on first composition.
- `ui/components/DailyBonusCta.kt` (+ `DailyBonusConfirmDialog`) is the one rewarded-ad row, used by `GnotesInfoDialog` and `PointsCard`. Its pulsing ring is drawn outside the badge, never as a border on the gold fill.
- Never call Gnotes or items "rewards" in user-facing copy (the ad-free days are the only legitimate use).

### Cosmetic items (`ui/components/metro_items/`)

- `MetroItem`: `draw(u, cx, baseY)`, `hitCenter(u)`, `hitRadius(u)`, `isBodyAttached`, `isHeadAttached`, `previewCenter(...)`, `previewRadius(u)`; `hitCenterOnCanvas` / `previewCenterOnCanvas` for items placed by canvas width or dp (GnomeCanvas taps and the unlock preview call those).
- `METRO_ITEM_REGISTRY` pairs each item with an `UnlockCondition`; its order is background draw order. Do not reorder without checking.
- `MetroItemTracker` (prefs `metro_cosmetics`): counters (`metronome_seconds`, `games_completed`, `first_launch_ms`, ...) and `celebrated_item_ids`, written **only when the popup is dismissed**, so a popup bug never silences a reward. `markCelebrated` also emits the companion `celebrationDismissed` SharedFlow.
- `UnlockCelebrationOverlay` shows one unlock at a time from a queue; `ItemPreviewCanvas` crops around `previewCenter` with a u-relative `previewRadius`.
- **A moving item's motion must be in `u`, and `previewRadius` must contain the whole path** (span + half the item); canvas-fraction motion escapes the crop on other aspect ratios.
- **An item worn on the hat clips to the hat:** use `GnomeCanvas.hatConePath(u)`, `HAT_BASE_Y`, `HAT_TILT_DEG`. Head-attached items do not follow the hat's beat bob.
- **`item_unlocked` is logged only through `AnalyticsTracker.logItemsUnlocked(tracker, entries)`** (once per install via `MetroItemTracker.claimUnreported`, prefs `reported_unlock_ids`). Any screen queuing celebrations calls it, never `logEvent`. Counts before 2026-11-19 are not users.

**Adding an `UnlockCondition` variant (four files):**
1. `MetroItemTracker`: counter writer, reader, `isUnlocked` branch, `resetAllProgress` line.
2. `displayText()` branch.
3. `points/ConditionPoints.kt` (two functions).
4. `ui/components/metro_items/UnlockProgress.kt` (two functions). **This one gets missed**; both 3 and 4 are exhaustive `when`s, so expect and welcome the build errors. The checklist is repeated in `UnlockCondition.kt`'s KDoc.

**A new progress counter is not done until it is in** `usage/ActivitySummary`, the logger, the restorer's apply and merge, and the round-trip overlay's three row lists.

- Practice items count **days**: `PracticeSessionsCompleted` counts distinct practice days (a finished Speed Trainer session records one too); the Racing Goggles use `SpeedTrainerDays`. `SpeedTrainingSessionsCompleted` remains for raw counts. Tuner feedback counts every rating, thumbs-down included, and checks unlocks at once.

## Metronome UI

- **`viewmodel/MetronomeViewModel.kt`:** play state, BPM, time signature, sound, volume; prefs `metrognome_prefs`. Tap tempo needs at least 2 taps within 2.5 s.
- **`ui/components/GnomeCanvas.kt`** draws Metro. Animations (pendulum, bounce, flash, twinkle) follow `BeatEvent`s; items draw in separate passes for background, body-attached and head-attached (bobbing with the head).
- **Home moves when the click is heard, not on the callback.** `BeatPulse` (gems, both stages) holds each beat until `BeatEvent.presentedMs` and draws at predicted on-glass time (`PresentationLead`); `GnomeCanvas` waits for `presentedMs` less two refresh periods, one coroutine per beat, dropping pending beats on stop. **This is visual only and must stay so**: the engine, `BeatFrameClock` and `onBeatTimed` are untouched, collectors never suspend, and the audio thread never waits on a visual.
- `theory/Meter.kt`: single source of time-signature theory (pure Kotlin). SIMPLE (top 2-4), COMPOUND (top a multiple of 3, at least 6), IRREGULAR (others); classification depends only on the top number (3/8 is simple). `label()` says **"Odd" only when the top is actually odd** (5, 7, 11) and "Irregular" otherwise (8, 10); do not collapse them (`MeterTheoryTest`). `description()` ("Felt in 2+2+3"), `beatGrouping()` / `defaultAccents()` (irregular defaults to twos with a trailing three).

### The meter stage (`ui/meter/`)

- The beats above Metro are the time signature's front door and only editor. **Settings has no time signature and must not get a link to the stage**; its first card is "Tempo".
- `MeterStrip` (Home, 88 dp) draws beat gems (`BeatGem.kt`, `drawBeatGem`) and a stacked fraction at the left that flashes gold on the downbeat. No clip and no ripple on the tap area (a clip cuts the halos).
- `MeterLayout` (pure, `MeterLayoutTest`) opens a gap before every accent in use, on strip, wheel and editor; on the wheel the wrap gap is added only for a bar of more than one group.
- `MeterStage` (stateless; `MetronomeMeterStage` binds it): a carousel of beat wheels (`MeterMedallion`) over the nine `COMMON_METERS` plus a "your own" page fed by fraction steppers; a step landing on an everyday meter jumps to its page. The card has label, status, feel, accent editor, steppers, `PlayStopKey` (Home's own toggle, so a stop fires `onBeforeManualStop`) and Use.
- **Rules:** Use is inert on the meter in use (`setMeter` resets accents). The meter is held while a Practice or Speed Trainer session is open (`SpeedTrainerViewModel.beginSession` reads `timeSig` once); accents may still change.
- **The invitation:** Home is idle after `METER_IDLE_MS` (4 s) untouched with nothing going on (stopped, no session, no modal, no poll card, resumed); a touch ends it (watched in the Final pass). While idle (up to `METER_INVITE_MS`, 24 s) and not acknowledged, a glow breathes and a light runs along the bar, and once per launch (`meterHintCardShown`) `MeterHintCard` shows. Acknowledged for good (`meterHintDone`, prefs `meter_hint_done`, commit) by Got it or by opening the stage. Keep the card text short (it covers the BPM at 1.3x fonts otherwise).
- No meter analytics yet (offered to the dev: `meter_stage_opened`, `meter_changed`).

## Rhythm game

- `viewmodel/RhythmGameViewModel.kt`: IDLE -> COUNTDOWN -> PLAYING -> RESULT. Notes travel 2000 ms. Windows PERFECT +-50, GOOD +-100, ALMOST +-150 ms, times a fixed 1.5 tolerance; mic mode uses its own wider tempo-clamped windows. Scoring 100/70/30. High scores per difficulty in `rhythm_highscores`.
- `ui/game/`: `RhythmStage.kt` (layout, controls), `RhythmLane.kt` (every pixel of the lane), `RhythmResult.kt`, `StageClock.kt`; `RhythmGameScreen.kt` is only the dashboard. The game uses its own engine instance with uniform clicks.
- **One grid, the engine's:** `viewmodel/RhythmTimeline.kt` (pure, `RhythmTimelineTest`). The engine starts **muted at the countdown**; the origin is the **latest** `presented - index * beat` seen (a max, never an average, because early beats come from a fallback that reads early). `beginPlay` freezes the grid, puts the first note on the first beat leaving a full lane, accents it, and un-mutes one beat before it.
- **Taps score on touch-down** with the pointer event's `uptimeMillis` (boot clock), every finger; `onClick` only for clicks with no touch (screen readers).
- **Notes are positioned per display frame** from `RhythmChart` and `StageClock.displayMs` (vsync + `expectedPresentationTimeNanos` on API 33+, two refresh periods below). The game loop owns state, not position.
- Mic scoring is still uncorrected against the wide windows (open question that needs the phone).
- **Graphics: information is exact, decoration never looks like a cue.** Note height is linear in time (only size changes with depth); the gold band is the PERFECT window to scale, green the GOOD; ring pulse and moon halo are the beat grid. Judgement word sits under the ring. Stop is a round red-rimmed key on TAP's line at the left edge. Hit bursts are two side fans of the shared `Particle` / `drawParticles`; every tenth hit is confetti. **No haptics during play** (the mic hears them).

## Chord Finder (Chords tab)

- **`theory/Chord.kt`** (pure, sibling of `Meter.kt`): `ChordTheory.identify(midiNotes)` -> `ChordReading` (`Empty`/`Single`/`Dyad`/`Identified`/`Unnamed`). Types are defined by **degrees** ("1 b3 5 b7"), not semitones. **The bass decides** between readings with the same pitch classes (C6 vs Am7), so callers pass real MIDI notes. A seventh may drop its fifth ("C7(no 5)") only with a third and a seventh; triads, sus and added-tone chords never lose the fifth. `ChordTheoryTest` covers naming, ranking, spelling, dictionary integrity.
- **Spelling:** a tone's letter is the root's letter advanced by the degree, its accidental whatever closes the gap, so **C#7 contains E# and Db7 contains Cb; these are correct, do not "fix" them**. Root spelling (`ChordTheory.rootName`): flats for major-type roots on pitch classes 1, 3, 8, 10; sharps for minor-type on 1 and 8; F# always. Rootless readings use `NoteNames`' sharps. **Everything goes through `ChordMatch.spell`; never build a second name table.** Octave numbers follow the spelled letter: **`ChordMatch.spelledOctave(midi)`** is the only correct source beside a spelled name (Cb4 sounds as B3).
- **The "about" lines are checked facts.** Verify any music-theory claim against a reference before writing it; the dev cannot check theory. Keep each under 86 characters (two lines at 11 sp on 280 dp).
- **`viewmodel/ChordFinderViewModel.kt`** owns its own `Tuner()` (not `TunerViewModel`'s) and reads reference pitch and calibration from `tuner_prefs`. Notes arrive one at a time from taps or the mic (a mic note needs `CAPTURE_FRAMES` readings and is not re-added until the reading goes null). **A new arpeggio starts from the bottom:** a mic note below the bass, once the set holds `RESTART_MIN_NOTES` (3), starts a new set; taps are exempt. No silence timer (rejected by the dev). Strummed chords are out of scope (MPM is monophonic). The VM's `init` block sits **below every property** (collectors run synchronously in `init`). Instrument, notes, mic choice, engine, pace and per-instrument scroll persist in `chord_finder_prefs`.
- **Two listening engines**, Fast (Brossier, default) and Steady (McLeod), on the Listening card (`engine` pref, `ChordEngine.DEFAULT`); code names stay McLeod/Brossier, `displayName`/`caption` are the player's words. Exactly one runs; both feed one `admit(midi)`.
  - **McLeod** is the tuner's pipeline unchanged; its hold is slow for note sequences. **Do not "fix" the tuner for this**; its hold is what tuning needs.
  - **Brossier** is `audio/chords/NoteAnalyzer.kt` + `NoteCapture.kt` (own `AudioRecord`): onset (`OnsetDetector`, SuperFlux with a relative peak criterion), skip attack, median pitch, no hold; window 4096 / hop 512. Overlapping consonant notes read as their common period, so it uses `PitchDetector.detectAbove(window, reference)` (spectral subtraction of the pre-onset spectrum, then MPM on the residual).
  - `NoteTracker` rules worth keeping: rise-from-silence and steady-pitch fallbacks (steady refused for sub-harmonics of recent notes; held to 0.85 clarity, `STEADY_MS` 300); two-tier decision (`STRONG_RMS` + `STRONG_CLARITY` fast, otherwise `PATIENT_*` stability); `NoteAnalyzer.NEW_POWER_MIN` drops onsets that bring nothing new (`PitchDetector.lastResidualPowerFraction` under 5%) and flux windows are judged on the residual only; 900 ms room profiling with `HUM_*`; floors in both `AmbientDetector` and `NoteTracker` ratchet up by `FLOOR_RISE`. `forget()` on Clear and after hear-it. Every `Note` carries `Source` and `patient`. Tests: `NoteAnalyzerTest`, `OnsetDetectorTest`, `NoteTrackerTest`, `PitchDetectorResidualTest`.
- **Hear it** (`audio/chords/`): `ChordVoice.kt` (pure synthesis), `ChordPlaybackPlan.kt` (SLOW/QUICK), `ChordPlayer.kt`. The dev tools' test tone uses the same synthesis (`ChordTimbre.TEST_TONE`); **add a `ChordTimbre` or plan, never a third chord tone generator**. Hear-it does not close the mic; `captureFromMic` ignores readings while `captureMuted` (phrase + `PLAYBACK_MUTE_TAIL_MS`). `ChordVoiceTest`.
- **Screen** (`ui/screens/ChordFinderScreen.kt`, no heading): chord card, tip strip, instrument, notes strip, Listening card, Hear It, 12 dp apart, banner ad pinned below (no interstitials). The empty chord card's eyebrow "CHORD FINDER" is the only place the feature is named. **No-jump rule:** every element a reading changes has a density-sized height (`PinnedSlot`), nothing appears or disappears. Guitar is the default instrument. Landing on Chords stops a sounding drone (in `switchTab`).
  - `ui/components/PianoKeyboard.kt` (multi-octave, `litMidi`, glow; C2..B5 opening at C3) and `ui/components/GuitarFretboard.kt` (standard tuning, 12 frets) draw at one height. A mic-captured note eases the instrument to centre it (keyed on a per-capture serial; geometry from `pianoKeyCentreFraction` / `fretboardPositionFractions`); taps never scroll. Each instrument's `ScrollState` is **constructed** at the saved position (prefs `scroll_dp_GUITAR` / `scroll_dp_PIANO`); restoring with `scrollTo` after composition shows a jump. `FadingHorizontalScrollbar(hint =)` shows "scroll".
  - **The Listening card's grammar is the page's reference** for any new card (header with right-aligned value that says only what the chips do not; pinned captions). Mic row from `ui/components/ListeningIndicators.kt` (`InputLevelMeter`, `ListeningStateIcons`, `ListeningStateBadge(quiet =)`, `listeningStateColor`), shared with the tuner. The grant is re-read on `ON_RESUME`. The note chips all look alike; the gold "R" marks the root.
  - Notes strip throws a spark fountain per arriving note (not for restored notes or removals). Hear It's caption sits on the header line.
- **`ui/components/MicAccessStrip.kt` is the one way a screen asks for mic access** (crossed-out mic, "tap to grant" / "tap to open App Settings"). `MicCheckOverlay` and `MicTimingNudge` keep their own copy.
- **Vocabulary:** the feature is the "Chord Finder"; "arpeggio" is the instruction, never the name. Nav label "Chords". Tab icon `ui/components/ChordGridIcon.kt`.
- **Item and Gnotes:** `items/AcousticGuitar.kt` at `UnlockCondition.ChordsDiscovered(25)`, a **set** of distinct symbols (root + quality) recorded in the same `NAME_HOLD_MS` (1.5 s) hold as `chord_named`; `ConditionPoints` returns 0 for it. Gnotes: `PointsConfig.PER_CHORD_NAMED` (5) up to `PointsLimits.CHORDS_NAMED_PER_DAY` (9), with `postSilentEarn()`. `MetroItemTracker.recordChordNamed(fromMic)` keeps a mic/tap split (unused yet). The row exists in `EARN_RULES`, `PointsCalculator`, `DailyActivity`/`DailyActivityLog`, `PointsManager`; `RewardConfig.MAX_DAILY_BEATS` derives from the rules. The VM has its own `unlockQueue`/`checkForNewUnlocks`/`markCelebrated`. Guitar art: keep the bout/waist control points vertical; propped at 0.6 scale against the forest tree.
- **Analytics:** `chords_session_started` (instrument, mic, engine), `chords_session_ended` (duration_seconds, tap_notes, mic_notes, chords_named) from the screen's `DisposableEffect`; `chord_named` (symbol, note_count, source) after the 1.5 s hold, only while `sessionActive`, with a restored chord pre-marked; `chords_instrument_changed`, `chords_engine_changed`, `chord_played` (symbol, note_count, pace).
- **Chord Loop dev tool** (`debug/chords/`): one entry, the Settings dev button "Chord Test UI" (becomes "Cancel Chord Test" while running), opening `ChordLoopDiagnosticOverlay`. Run waits on `ChordFinderViewModel.listening`, plays three chords, classifies misses, turns one knob per signature (every adjustment moves one way under clamps, so it converges), checks the chord name too, and saves timings in `chord_test_prefs`. Watchers live in their own scope, released in `finally`. Modes: Spaced, Legato (per-engine PASS/FAIL), Listen (records the room). `diagnosticMode` makes a run earn and log nothing. `ChordLoopRunningPill` on the Chords tab shows progress. Each run writes `files/chord_loop_last.json` (read with `adb shell run-as com.wynandl.metrognome cat files/chord_loop_last.json`); "Save Log" exports via `CreateDocument`. This file is the workflow for any engine change.

## Other features

- **Polls** (`poll/PollConfig.kt` `ALL_POLLS`, `poll/PollManager.kt`, `cloud/PollReporter.kt`): on the Gnome tab only, state in `MetronomeViewModel`. Visible only at a genuine pause (stopped, no session or overlay). **One answer and one appearance per session:** `MetronomeViewModel.claimPollResponse()` (atomic) gates every outcome, `PollBanner` ignores taps once its step leaves RATING, and anything hiding a shown card calls `markPollDone()`. No timer: a show is counted (`recordShown`, 24 h cooldown, `poll_shown`), and after `MAX_SHOWS` (5) the poll retires and `retireIgnored()` writes one `"ignored"` row. Outcomes `up`, `down`, `dismissed`, `ignored`; `poll_answered`.
- **`ui/components/FeedbackCard.kt` is the one asset for asking the user a question** (container + `FeedbackQuestion` + `FeedbackThanks`); `PollBanner` and `TunerFeedbackCard` use it. The X is `QuietCloseButton`. Any new question uses it under the two poll rules above.
- **Notifications** (`notifications/`, nothing else calls Firebase Messaging): `MetroFcmService` overrides **`onRegistered`, not the deprecated `onNewToken`**; lint's `MissingFirebaseInstanceTokenRefresh` is suppressed on purpose, do not add `onNewToken` back. Posting re-checks `POST_NOTIFICATIONS` live. One channel `"general"` (`NotificationChannels`, matches the manifest). Broadcast topic `"all_users"` (`NotificationTopics`), no tokens stored; `MetroGnomeApplication.onCreate` subscribes if granted and **unsubscribes if not**. Two ask paths only: a contextual soft-ask once ever on the 2nd distinct usage day (`NotificationOptInDialog`, `NotificationOptInTracker`, checked in `MainActivity`'s `ON_RESUME`), and the always-live Settings row. `NotificationPermissionState` is owned once in `MainActivity`.
- **What's New** (`whatsnew/`): major versions only (`AppWhatsNew.ALL`: v3..v6). `WhatsNewTracker.pendingKey()`; shown by `WhatsNewOverlayDispatcher` ahead of unlocks, behind a practice result. **Fresh installs never see one** (the init guard pre-marks every key). The dismiss button is an acknowledgement that only closes, never a navigation.
- **Audio probe** (`tools/audio-probe/`, debug builds): drives WAVs through the phone speaker from adb and reads `TunerFrameTrace` / `AudioProbeReceiver` (`play`, `trace_start`/`trace_dump`, `mic_dump`). `README.md` there is the runbook; use it to check any tuner change.
- **Dev tools in `SettingsScreen`** (`BuildConfig.DEBUG` only): cheat mode, unlock popup preview, unlock rules, reset progress, diagnostic logs ("Mic Timing Log", "Tuner Lock Log" fill themselves; "Tuner Reading Log" records only while "Record Readings" is on, following `TunerReadingLog.isRecording`), ad-free trial previews/reset/2-min run, Chord Test UI. Labels stay three or four words with a 9 sp caption stating the rule. **A dev capture running on another tab gets a pill on that tab** (`ChordLoopRunningPill`, `TunerReadingRecordingPill`). All dev buttons are `DevButton` (`DevButtonKind`).

## Design system

### Components (one shared asset per pattern)

- **Cards:** `AppCard` (20 dp, gold hairline, 18/16 padding), `CardHeader`, `CardHeaderLabel` / `SectionLabel` (the two caps tiers as components), `AppInset`, `CardGap` (12 dp). Settings sections are `SettingsCard`s. Never draw a card as a bare `Surface`. `AppColors.card` (= `surfaceDeep`; `surfaceDim` is an alias).
- **Gold:** a wash and a hairline, via `goldTint`, `goldTintActive`, `goldBorder`, `goldBorderStrong`; `AppColors.goldSoft` for quiet gold text and marks. Never `gold.copy(alpha = ...)` for a fill or border. Gold is for a value that matters now, never a header.
- **Controls:** `RaisedControl` + `Modifier.raisedFace(shape, tint)` (gradient face, top-bright/bottom-dark hairline rim, no shadow or glow, colours derived from `tint`). `AppFilterChip`, `PrimaryButton`, `AppSwitch` (replaces Material `Switch`) use it. `GoldSlider` replaces `Slider` everywhere except `SettingsSliderRow`. `PlayStopKey` is the one "start a sound" key. `GoldPill`. `MorphingPill` (pill that morphs into a panel over later siblings via `zIndex`, never moving the page; must be a direct child of the column it covers).
- **Dialogs:** `AppDialog` or `AppCardDefaults.DialogShape` + `Border`; `DialogTitle`; `PrimaryButton` for one action, `GhostButton` + `GoldButton` (`DangerButton` for destructive) for a pair (`ActionButtons.kt`); `OverlayEyebrow`; `CelebrationCard` for any "something good happened" card. Never a Material `AlertDialog`/`Button` or a hand-rolled button.
- **Chips:** `CHIP_HEIGHT` (34 dp), wrapped rows set `CHIP_ROW_GAP` (8 dp) themselves.
- **Text field:** `ui/components/AppTextField.kt` is the only one.
- **Progress:** `ItemProgressBar` is the only progress bar. `StarGlyph`, `SparkleGlyph`, `StarRating` instead of typed star/sparkle characters.
- **Seal** (`ui/components/Seal.kt`): the "earned and verified" mark. New uses adapt a `SealStyle`, never redraw the silhouette; only the rosette rotates.
- **`TapDelight.kt`** (`Modifier.tapDelight(shape)`): observes touches only; its draw block must read the frame-clock state before any early return. On the About card, with `DevTapTarget` inside it on the Build line only.
- **Radii:** `ui/theme/Shape.kt` `AppRadius` (Dialog 24, Card 20, Panel 18, Button 14, Inset 12, Chip 10, Small 8, Bar 2, Pill). Never write `RoundedCornerShape(n.dp)` in a screen or component.
- Still deliberately on their own: the practice-timer bar and the Metro art.

### Icons

- **Every small icon is an `AppIcon` from `ui/components/AppIcons.kt`; Material's icon library is not a dependency.** `AppIcons.kt` is **generated** by `tools/icons/gen_icons.py` (commands in its docstring): add a row there, never paste path data. Drawing code (`AppGlyph`, `AppIcon`, `drawGlyph`) is hand-written in `AppIcon.kt`. Lines are 1.5 dp at every size (1.25 under 14 dp), absolute, never scaled; only Play/Pause/Stop are filled.
- **Every multi-part glyph is drawn in one ink** (`OneInk.kt`: parts opaque inside `oneInk`, faint parts in a nested `oneInk(FAINT_INK)`), or alpha stacks at the joints.
- A small icon beside text is a bare single-colour glyph; disc badges only for large hero icons in dialogs.
- **A feature has one icon everywhere:** `TunerNeedleIcon`, `ChordGridIcon`, `RhythmPulseIcon`, `AppIcons.MusicNote` (metronome), `DroneWaveIcon` (drone), `Mic` (Groove Check), `AppIcons.Gnote` (any Gnote count; a note wearing Metro's hat; brand copy `marketing/brand/gnote.svg`), `MetroCollectionIcon` (Collection). The bolt means Speed Trainer only. `AppIcons.TuningFork` for fork calibration. New hand-drawn icons follow `ChordGridIcon`.
- **Never type a note character (U+2669 and kin) into a string**; use `AppIcons.MusicNote`. An unnamed BPM preset stores a blank name and `PresetPill` draws the mark.

### Colors (`ui/theme/Color.kt`)

No raw `Color(0xFF...)` in screens or components; define in the right object first.
- `GnomeColors`: character art only (inside `GnomeCanvas.kt`), mostly lit/base/shadow triples feeding the lighting ramps.
- `AppColors`: UI chrome (backgrounds, text tiers, brand, controls, `amber`/`amberLight`/`amberDark` for streak/loyalty, dev colours).
- `GameColors`: rhythm game semantics.
- `ItemPalette`: shared item colours (gold trio, wood duo). One-file item colours stay `private val` in the item.

### Typography

- **`Typography.bodyLarge` sets `lineHeight = 24.sp`**, so a `Text` overriding only `fontSize` keeps 24 sp lines (invisible until it wraps). **Every `Text` under 16 sp that sets `fontSize` sets `lineHeight`.** For a 12 sp headline over 10 sp subtext use `lineHeight = 16.sp`. Do not apply a global `1.5.em` fix (larger text depends on the current 24 sp).
- **A layout that must not move with its text takes its height from the density** (`sp.toDp()` on a `Box`, i.e. `PinnedSlot`), never from the text: glyph ink changes a `Text`'s measured height even with `Trim.None`. `PinnedSlot` measures its content without the slot's height limit.
- **A pinned two-line caption needs all three:** `style = PinnedLines`, `minLines = 2, maxLines = 2`, and a `PinnedSlot` of exactly two lines.
- **Caps labels have two tiers:** card header 12 sp bold, 1 sp tracking, 16 sp line height, `textDim`; section label 10 sp bold, 1 sp tracking, 14 sp line height, `textDim`. Nothing at 11 or 9 sp. Use `CardHeaderLabel` / `SectionLabel`, never the recipe as a `Text`. The "✦" in eyebrows is deliberate.

### Metro's lighting model (`GnomeCanvas.kt`)

One key light from the upper-left: `KEY_LIGHT_X/Z` + `rollLambert(nx)` (peaks near nx = -0.6, falls off at -1), applied with `rollGradient(fromX, toX, strength)` and per-feature ramps (`coneFeltColor`, `beardRollColor`, `skinSphereColor` / `skinFalloffAt`, etc.).
- `strength` scales with the element (0.4 to 0.55 for small ones).
- One object, one gradient; two objects, two (lapels, bow tie, sunglasses span the pair with one).
- Cast shadows are clipped to what they land on (`hatShadowSurface(u)` = head + hair + ears).
- Never stroke a specular rim onto a flat shape.
- Union coincident shapes (`cone + brim`, the moustache wings) instead of abutting them; any repaint beside shaded pixels leaves a 1 px seam.

**Launcher icon** is the flat vector Metro (`drawable/ic_launcher_foreground.xml`, legacy `mipmap-*` webp) and must match the in-app Metro. `app/src/main/ic_launcher-playstore.png` stays the 3D render for marketing (history in commits `fd9c689` to `a0ebf5b`).

## Website (`landing/`, master branch only)

metrognome.co.za lives only on `master` (Vercel, Root Directory = landing); never read or edit it from a version branch (stale copy). Read `seo_context.md` on master before any SEO work. **App changes that need a master-side update:**
- `theory/Chord.kt` -> `landing/tools/chord-finder.html` (verbatim JS port; re-run the `ChordTheoryTest` expectations in Node).
- `theory/Meter.kt` -> `landing/tools/odd-meter-practice-tool.html` and `landing/js/metronome.js` (the shared online metronome widget on the homepage and `tools/online-metronome.html`).
- `audio/drone/DroneVoice.kt` -> `landing/tools/tuning-drone.html` (free voices only).
- `MetronomeScreen.kt` `tempoLabel()` -> `tap-tempo-bpm-finder.html` and `js/metronome.js`; `MetronomeViewModel` BPM range (20 to 300) and tap rules (2.5 s reset, last 6 taps) -> `js/metronome.js`; `GrooveScorer.kt` jitter constants -> `rhythm-timing-test.html`.
- A new click sound, tab or hero feature -> homepage app panel, `featureList`, FAQ and the sound counts in the posts.

After editing a tool, run `landing/embed/build-embeds.sh` and commit the output. Site rules (since the 2026-11-22 AdSense redo, live on master from `a223ed5`; revert tag `site-pre-redo-2026-11-22`; status and open items in `seo_context.md`): the site is tools first and the app second; posts are by **Wynand Lambrechts** (Person schema, author box), but the name is never advertised: it reads as the surrounding text (`a.name-link`, no bold, no gold, no underline until hover), with about one app mention per page plus the closing box; time signatures live in one guide (`blog/time-signatures-explained.html`, anchors `#time-7-8`, `#chart`), the old per-meter pages 301 there and must not come back; tool captures in `tools/img` are made whole with a simulated player (`tools/site-capture/` on master), never cropped mid-element. Navigation (2026-11-22): the top bar on every page is Metro's face + "Home", then Tools, Guides, About (no single tool gets a top link); `aria-current` marks the section you are in. Page tiers: the homepage has its own hero; the three top-bar pages (Tools, Guides, About) share `.section-hero` (eyebrow = the nav label, Guides reads "Guides · Blog"); everything below them (a tool, a guide, Contact, Privacy) uses breadcrumb + `article-header`. The Guides list is newest `datePublished` first (a new post goes on top; an edit never moves a card), and the homepage "Latest guides" shows its top three, so a new post updates both. A last-updated date is bookkeeping: never on cards or in a page header, only the dim `.page-updated` line at the end of a guide, About, Contact and Privacy. **Gold outline or fill means "selected/current" only**: a link or CTA is never gold by default, even the first or most popular one. Vercel only builds production (Ignored Build Step), so a branch gets no preview unless that setting is relaxed for the test and restored after; previews sit behind Vercel login. No em or en dashes, never "rewards".

## Dependencies, signing, permissions

- Versions in `gradle/libs.versions.toml`: Compose BOM 2026.11.01 (Material3 1.5, needs compileSdk 37.1 via `compileSdk { version = release(37) { minorApiLevel = 1 } }`; `GoldSlider` drives a `SliderState`), AGP 9.5.2, Kotlin 2.4.21, coroutines 1.11.0, Play Ads 25.5.0, Firebase BOM 35.2.0, Google Services 4.6.0, Gradle 9.9.1. Both `implementation(platform(bom))` and `androidTestImplementation(platform(bom))` are required; the duplicate warning is a false positive.
- Release builds need `keystore.properties` at the root referencing `metrognome-release.jks`.
- Permissions: `INTERNET` (ads, Firebase); `RECORD_AUDIO` (tuner, chord finder, rhythm game claps, Practice/Speed Trainer, Groove Check; Groove Check paths go through `MicPermissionRecovery`, Chords and Tuner use their own flow; the drone gives the mic up); `VIBRATE` (`haptics/HapticEngine.kt`); `POST_NOTIFICATIONS` (API 33+, via `NotificationPermissionState`).
