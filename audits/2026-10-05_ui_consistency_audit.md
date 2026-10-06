# MetroGnome UI consistency audit
Date: 5 October 2026 (SAST)
Branch: `fix/audio-review`
Audited commit: `f39400a93c9868f43e79e8575436ce7addcec1ec`

The shared UI foundation is good, but this branch is not ready for a UI consistency sign-off. The most useful fixes are shared text contrast, accessible actions and states, compact-layout handling, and migration of the remaining legacy buttons/dialog shells.

This is a **source-wide UI audit**, with a systematic inventory and focused inspection of screens, shared controls, dialogs, overlays, rendering and their call sites. It is not a screenshot-by-screenshot or device-tested visual sign-off. No app implementation or audio-engine code was changed.

## Scope and verification

- Inventoried all **113 Kotlin files under ui/**: 5 screens, 50 shared component files, 6 instrument files, 8 cosmetic infrastructure files, 19 cosmetic drawing/helper files, 15 dialog files, 7 overlay files and 3 theme files.
- Also scanned MainActivity and 10 developer UI files: **124 source files / 29,603 source lines** in the companion inventory. Inventory counts include comments, imports and previews.
- Hashed all **20 Android resource files**, including launcher variants, notification artwork, colors, theme, strings and backup rules.
- Reviewed navigation, ad/no-ad variants, permission/recovery presentation, selection/disabled states, tuner idle/detected/drone states, chord empty/identified/instrument states, rhythm idle/countdown/play/result, Practice and Speed Trainer setup/HUD/results, purchases, currency, collection and feature/unlock announcements.
- `adb devices` returned no connected device or emulator. No runtime screenshot, font-scale, TalkBack, rotation, split-screen, frame-time or allocation measurements were performed.
- Attempted `gradlew.bat lintDebug --offline --console=plain`. The sandboxed attempt failed while fetching the wrapper; the escalated retry failed with `java.io.IOException: Unable to establish loopback connection`. **Lint did not run; no passing build/lint claim is made.**
- Companion CSVs are lexical inventories, not compiler analysis or proof that every runtime state passed.

Severity: **P1** prevents a supported form of UI interaction; **P2** should be fixed before UI sign-off; **P3** consistency/maintenance improvement. Evidence below distinguishes source-confirmed gaps from runtime validation candidates.

## Confirmed UI findings

### U01 — P2: Shared small-text colors are too dim

Evidence: [AppCard.kt:79](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AppCard.kt:79), [Color.kt:109](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/theme/Color.kt:109), [LabelValueBadge.kt:21](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/LabelValueBadge.kt:21), [LoyaltyMilestonePath.kt:183](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/LoyaltyMilestonePath.kt:183).

The shared 12 sp bold CardHeader uses `textDim`. The same color appears in Tuner/Chords headings, helper text, the preset-delete hint, restore-purchase links and trainer status. This affects necessary information, not just decorative lines.

Calculated sRGB contrast from the source colors:

| Foreground | On card #13102A | On surface #1E1B3A | On surfaceVariant #2A2550 |
|---|---:|---:|---:|
| textDim #6060AA | 3.30:1 | 2.95:1 | 2.54:1 |
| textSubtle #7070AA | 4.04:1 | 3.60:1 | 3.10:1 |
| textMuted #8080AA | 4.92:1 | 4.39:1 | 3.78:1 |
| textSecondary #CCCCEE | 11.84:1 | 10.56:1 | 9.09:1 |
| GameColors.miss #CC4444 | 3.94:1 | 3.52:1 | 3.03:1 |

Additional alpha makes text dimmer: future loyalty names use `textSubtle.copy(alpha = 0.5f)`; the preset hint uses reduced-alpha textDim. A 12 sp bold heading is still small text. Use 4.5:1 as the small-text readability benchmark, preserving subdued colors for decoration and legitimately disabled controls. [W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

Recommendation: introduce readable heading/caption/secondary tokens for each intended surface. Check actual composited backgrounds before adjusting all colors. Do not brighten every decorative/inactive mark indiscriminately.

### U02 — P1: Drawn instruments have no accessible note-selection actions

Evidence: [PianoKeyboard.kt:77](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PianoKeyboard.kt:77), [GuitarFretboard.kt:65](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/GuitarFretboard.kt:65), [DronePanel.kt:164](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/DronePanel.kt:164), [ChordFinderScreen.kt:517](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/ChordFinderScreen.kt:517).

The piano and fretboard perform hit-testing only in `pointerInput / detectTapGestures`. The caller adds a description of the whole drawing, but there are no per-note semantics nodes, click/custom actions, or keyboard selection path. A screen reader can hear “tap a key” without having an action to select that key. This affects both Chords and the Drone picker.

Recommendation: expose selectable notes/positions through semantic children or an equivalent accessible picker. Announce current selection and selected notes; preserve the existing artwork. [Android custom-component semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

### U03 — P1: Saved presets cannot be activated or deleted through accessibility actions

Evidence: [PresetChipsRow.kt:59](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PresetChipsRow.kt:59).

Preset pills use a non-clickable Surface with raw pointer gestures for tap and long press. Their text is readable, but the preset exposes neither an accessibility click nor a long-click/delete action. Active state is visual only.

Recommendation: use combinedClickable (with action labels and selected semantics), or explicit semantic click/delete actions. Keep the horizontal scrollbar and long-press hint.

### U04 — P2: Disabled shared controls are visually dimmed but semantically enabled

Evidence: [PrimaryButton.kt:37](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PrimaryButton.kt:37), [PlayStopKey.kt:47](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PlayStopKey.kt:47), [RaisedControl.kt:74](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/RaisedControl.kt:74), [ChordFinderScreen.kt:1012](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/ChordFinderScreen.kt:1012), [DronePanel.kt:170](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/DronePanel.kt:170).

PrimaryButton and PlayStopKey guard the callback and reduce alpha, but RaisedControl never receives `enabled`; its clickable Surface remains enabled. Chords pace chips similarly stay clickable when there are no notes. Drone octave controls dim at their limits but retain enabled click behavior.

This does not bypass the business guard, but it advertises an available action that does nothing. Secondary ActionButtons already use Surface.enabled correctly.

Recommendation: add enabled support to RaisedControl, AppFilterChip and CircleButton and propagate it to the interactive modifier/Surface.

### U05 — P2: Selection state is missing from chips and accent controls

Evidence: [AppFilterChip.kt:50](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AppFilterChip.kt:50), [TimeSignaturePicker.kt:230](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/TimeSignaturePicker.kt:230).

AppFilterChip takes `selected` but uses only `clickable(role = Role.Button)`. It never publishes selection state. AccentCell similarly changes fill and text weight without exposing accented/not-accented. This affects sound, timbre, blend, time-signature, instrument, engine and playback-pace choices.

Recommendation: use selectable/selected semantics for exclusive choices and toggleable/state semantics for individual beat accents. Mark exclusive groups appropriately.

### U06 — P2: Switches and increment buttons lack contextual accessible labels

Evidence: [AppSwitch.kt:35](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AppSwitch.kt:35), [CircleButton.kt:28](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/CircleButton.kt:28), [SettingsScreen.kt:708](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/SettingsScreen.kt:708), [SpeedTrainerDialog.kt:435](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/SpeedTrainerDialog.kt:435), [TimeSignaturePicker.kt:198](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/TimeSignaturePicker.kt:198).

AppSwitch exposes a switch role and checked state, which is good, but no label. Settings puts the label in a sibling Column without merging it into the switch. Multiple CircleButtons expose only “+” or “−”, without naming start tempo, target tempo, reference pitch, octave, etc. Time-signature steppers have the same problem.

Recommendation: supply labels such as “Flash on Beat”, “Notifications”, “Increase target tempo” and “Decrease reference pitch”; provide current value/state where useful.

### U07 — P2: Trainer HUD action targets are packed too closely

Evidence: [SpeedTrainerHud.kt:232](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/SpeedTrainerHud.kt:232).

Retreat, skip and cancel are 16 dp icons separated by only 6 and 8 dp. Their centers are 22 and 24 dp apart. Compose can automatically expand small clickable touch targets, so visual size alone does not establish a 16 dp touch region; however, these controls cannot each have a distinct non-overlapping 48 dp target within the current row.

Recommendation: reserve actual target space, place icons inside properly sized IconButtons, and reflow less-used actions on compact widths. Check Practice's small cancel control and the 26 dp chord mic toggle at the same time. [Android touch-target guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

### U08 — P2: Ambient detail values stop refreshing when listening state stays the same

Evidence: [TunerScreen.kt:807](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/TunerScreen.kt:807), [TunerScreen.kt:963](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/TunerScreen.kt:963).

`shown` is updated only inside `LaunchedEffect(report.state)`. A new report with the same state does not refresh the displayed noise floor, stability, hum or header candidate. The rail uses the current report, while the detail panel can keep an old snapshot. The 900 ms delayed assignment also uses the captured report from the beginning of that state.

Recommendation: debounce the narration/state label separately from live measured values, and read the latest report when committing a settled state. This is a UI presentation issue; it does not require changing detection logic.

### U09 — P2: Result/announcement overlays do not establish a modal boundary

Evidence: [PracticeCompleteOverlay.kt:74](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/PracticeCompleteOverlay.kt:74), [SpeedTrainerResultOverlay.kt:70](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/SpeedTrainerResultOverlay.kt:70), [UnlockCelebrationOverlay.kt:155](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/UnlockCelebrationOverlay.kt:155), [WhatsNewOverlay.kt:165](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/WhatsNewOverlay.kt:165), [MainActivity.kt:289](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/MainActivity.kt:289).

These overlays are ordinary screen-local Boxes with a dark background. They have no scrim input consumer, Dialog boundary, modal accessibility isolation or BackHandler. The screen-local overlay also excludes the scaffold navigation. The dimmed backing screen is not isolated as an actual modal surface.

Recommendation: decide explicitly whether these cards are modal. For the existing “dismiss to continue” presentation, use one reusable modal overlay shell that blocks background actions, isolates accessibility focus, handles Back and spans the intended app area. Preserve ad-before-dismiss callbacks.

Runtime validation is still needed to observe exact pointer routing and TalkBack behavior; absence of a modal boundary is confirmed in source.

### U10 — P2: Transient banners have independent hosts at the same coordinates

Evidence: [MainActivity.kt:370](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/MainActivity.kt:370), [PointsEarnedBanner.kt:29](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PointsEarnedBanner.kt:29), [PointsEarnedBanner.kt:53](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PointsEarnedBanner.kt:53), [AdBreakBanner.kt:26](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AdBreakBanner.kt:26).

Earned points, loyalty milestones and ad notices each manage their own visibility and delay, but all three hosts occupy TopCenter with identical insets. Concurrent events have no shared scheduling/priority rule and can paint over one another.

Recommendation: one banner host with a shared queue or an explicit priority/stacking rule. Reuse BannerModel/BannerPill, which already provide a good shared asset.

### U11 — P2: Adaptive ad size uses display width instead of available content width

Evidence: [AdBannerView.kt:19](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AdBannerView.kt:19), [AdBannerView.kt:30](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AdBannerView.kt:30), [MainActivity.kt:293](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/MainActivity.kt:293).

The banner requests a size using displayMetrics.widthPixels, while the actual AndroidView is constrained by the screen content. On the navigation-rail layout, the rail takes part of that width. Split-screen/resizing can also leave the request out of agreement with the current container. No update block recalculates the size when constraints change.

Recommendation: determine width from the actual content constraints and update/recreate the request only when its supported size changes. Validate paid/free layouts, rail mode and resizing.

### U12 — P2: Primary/secondary action styles still diverge from the shared system

Evidence: [MetronomeScreen.kt:1067](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/MetronomeScreen.kt:1067), [RhythmGameScreen.kt:648](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/RhythmGameScreen.kt:648), [RhythmGameScreen.kt:680](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/RhythmGameScreen.kt:680), [RhythmGameScreen.kt:1119](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/RhythmGameScreen.kt:1119), [ActionButtons.kt:20](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/ActionButtons.kt:20), [FeedbackCard.kt:201](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/FeedbackCard.kt:201).

Practice's Start uses a flat 52 dp purple Surface with ExtraBold/1.5 sp tracking; PrimaryButton uses the raised face, 46 dp height and Bold/1 sp tracking. Rhythm Play Again/Done use card-shaped 20 dp corners; shared dialog actions use 14 dp corners. Rhythm's TAP circle and Stop action also use their own chrome. Feedback buttons are another local 42 dp implementation.

Recommendation: migrate equivalent confirm/dismiss actions to PrimaryButton/GhostButton/DangerButton. Keep deliberate interaction-specific geometry (for example, a large rhythm TAP target), while sharing its face/colors/type rather than forcing every button to the same size.

### U13 — P2: Several dialogs/results cannot scroll when available height shrinks

Evidence: [AppDialog.kt:46](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/AppDialog.kt:46), [MetronomeScreen.kt:1023](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/MetronomeScreen.kt:1023), [SavePresetDialog.kt:81](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/SavePresetDialog.kt:81), [CalibrationDialog.kt:85](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/CalibrationDialog.kt:85), [InstrumentCalibrationDialog.kt:79](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/InstrumentCalibrationDialog.kt:79), [MicCheckOverlay.kt:177](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/MicCheckOverlay.kt:177), [PracticeCompleteOverlay.kt:100](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/PracticeCompleteOverlay.kt:100), [SpeedTrainerResultOverlay.kt:90](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/SpeedTrainerResultOverlay.kt:90).

Practice setup uses AppDialog with scrolling off. Save Preset, calibration flows, Mic Check and both session-result cards have fixed content columns without a scroll path. Save Preset is especially exposed when the keyboard is open; Mic Check and scored results contain substantial explanatory/reward content. SpeedTrainerDialog already opts into scrolling, showing a suitable precedent.

Recommendation: allow these surfaces to adapt to the available window/IME height with bounded scrollable content and reachable actions. Prefer a pinned action area where practical. Exact clipping thresholds require runtime tests.

## Layout candidates requiring rendered verification

### U14 — P2 candidate: Chords instrument header is over-constrained

Evidence: [ChordFinderScreen.kt:440](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/ChordFinderScreen.kt:440).

InstrumentCard keeps AppCard's default 18 dp horizontal content padding, then adds another 16 dp inside its header/instrument/scrollbar. With the page's 22 dp margins, a 360 dp viewport gives the header just **248 dp**. That header contains the unweighted “STANDARD TUNING” or longer piano-range text plus two instrument chips; there is no wrapping strategy or weighted label.

Recommendation: use zero horizontal AppCard padding for this edge-managed card, or remove its nested inset, then split/reflow the heading and selector as necessary. Render guitar and piano at 320/360 dp and larger font sizes. The redundant padding and rigid row are confirmed; the resulting clipping/squeezing has not been screenshot-verified.

### U15 — P2 candidate: Fixed dp controls fight large text

Evidence: [AppFilterChip.kt:68](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AppFilterChip.kt:68), [SpeedTrainerDialog.kt:406](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/SpeedTrainerDialog.kt:406), [MetronomeScreen.kt:847](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/MetronomeScreen.kt:847), [SpeedTrainerHud.kt:106](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/SpeedTrainerHud.kt:106), [ChordFinderScreen.kt:1128](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/ChordFinderScreen.kt:1128).

Chips are fixed at 34 dp; Practice/trainer bars at 38 dp; trainer configuration tiles at 80 dp. ConfigTile places a center value in the same horizontal space as two edge-mounted 32 dp steppers. Chords pins names/captions to one/two lines, and its mic/tip/notes rows use fixed dp heights. Chords PinnedSlot correctly scales its own height from sp, but its fixed line count still truncates longer content at narrower effective text widths.

Recommendation: use minimum heights, reserve stepper/value columns, and allow explanatory captions to grow or reveal their full content. Do not remove all stable musical readout geometry; verify representative long chord names, accidentals, high values and font scale 1.3/1.5/2.0.

### U16 — P2 candidate: Home and active Rhythm have no compact-height fallback

Evidence: [MetronomeScreen.kt:247](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/MetronomeScreen.kt:247), [MetronomeScreen.kt:261](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/MetronomeScreen.kt:261), [RhythmGameScreen.kt:596](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/screens/RhythmGameScreen.kt:596).

Home allocates two control rows plus optional HUD/streak/presets/ad in a non-scrollable Column, leaving the character/BPM overlay in its weighted remainder. Active Rhythm similarly reserves a 110 dp TAP circle, score/feedback/actions and optional mic equalizer. Neither defines a compact-height layout. Landscape or a small resized window can starve the important visualization/readout.

Home additionally reserves a hard-coded 48 dp at the top, while the other four pages use statusBarsPadding. That is not a device-specific inset.

Recommendation: use real system insets plus a separate visual gap, and add a compact/landscape arrangement with clear priorities. Test with presets, scored HUDs and ads visible.

## Reuse and efficiency improvements

### U17 — P3: Typography is mostly local and inherits inconsistent line metrics

Evidence: [Type.kt:10](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/theme/Type.kt:10), [AppCard.kt:79](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/AppCard.kt:79), [DialogTitle.kt:20](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/DialogTitle.kt:20), [LabelValueBadge.kt:21](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/LabelValueBadge.kt:21), [MicTimingNudge.kt:222](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/MicTimingNudge.kt:222).

Only bodyLarge is customized in Typography; most text independently specifies font size, weight and tracking. Many smaller Text calls omit lineHeight, inheriting the ambient 24 sp lineHeight. Other components define explicit compact line heights. The repository already documents this exact wrapping problem inside MicTimingNudge.

Recommendation: define a small semantic type scale (card heading, secondary heading, body, caption, action, numeric readout) including family/weight/lineHeight/tracking, and use those styles in shared components. Keep intentional large note/BPM/score displays and the monospaced timer. No evidence of an accidental custom font-family mix was found.

### U18 — P3: Dialog shells and theme constants remain duplicated

Evidence: [AppDialog.kt:42](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/AppDialog.kt:42), [CalibrationDialog.kt:73](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/CalibrationDialog.kt:73), [SavePresetDialog.kt:70](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/SavePresetDialog.kt:70), [InstrumentCalibrationDialog.kt:61](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/InstrumentCalibrationDialog.kt:61), [WhatsNewOverlay.kt:141](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/overlays/WhatsNewOverlay.kt:141), [Theme.kt:8](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/theme/Theme.kt:8).

Several dialogs copy the spring/scale/alpha/surface/padding machinery instead of AppDialog. Feature announcements repeat a similar overlay shell four times. Theme.kt duplicates literal colors already defined in AppColors. Rhythm dashboard card gaps are 6/10 dp even though CardGap is documented as the app-wide 12 dp token.

Recommendation: shared dialog and modal-overlay shells with configurable header/body/footer slots; derive Material colorScheme from AppColors; either apply CardGap or document intentional dense groups. Do not treat every different illustration color or control size as an inconsistency.

### U19 — P3: Save Preset's “how it'll look” preview is a separate, mismatched asset

Evidence: [SavePresetDialog.kt:185](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/dialogs/SavePresetDialog.kt:185), [PresetChipsRow.kt:59](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/PresetChipsRow.kt:59).

The preview uses 13 sp bold text and horizontal/vertical padding of 14/7 dp, with no fixed height. The actual active pill uses 12 sp bold, 12 dp horizontal padding and fixed 30 dp height. The preview is labeled as the actual appearance but uses a different recipe.

Recommendation: extract a single non-interactive preset-pill face and reuse it for preview and interactive presets.

### U20 — P3 candidate: Cache static drawing work and limit animation-driven recomposition

Evidence: [GnomeCanvas.kt:151](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/GnomeCanvas.kt:151), [GnomeCanvas.kt:189](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/GnomeCanvas.kt:189), [GnomeCanvas.kt:576](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/GnomeCanvas.kt:576), [GoldChain.kt:65](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/app/src/main/java/com/example/metrognome/ui/components/metro_items/items/GoldChain.kt:65).

GnomeCanvas reads animated pendulum/breath state outside the draw lambda and redraws extensive path/brush geometry. It filters active items into multiple lists every draw, then filters body attachments again. Several drawing helpers reconstruct static paths/lists per frame.

Recommendation: first profile an idle/playing Home with many items and a collection grid. Then partition stable item lists once, cache size-dependent static geometry, and move suitable animation reads into draw/layer phases. Preserve current continuous item animation. This is an optimization candidate, **not measured jank or an asserted performance regression**.

## Page and surface coverage

| Page/surface | Items and states included in source inspection | Relevant findings |
|---|---|---|
| App shell | Five tab labels/icons; bar/rail choice; edge-to-edge; global banner hosts; purchases/errors; notification opt-in | U06, U09–U11, U18 |
| Gnome/Home | Beat dots; character/items/fireworks; BPM display; step/play/TAP keys; mute/screen/preset/practice/trainer actions; currency pill; streak; preset carousel; room warning; poll; ad | U01, U03, U07, U09–U10, U12, U16–U20 |
| Practice | Duration/slider/help/nudge/start; progress/cancel; result/streak/groove reward/dismiss; stop confirmation | U01, U04, U07, U09, U12–U13, U15 |
| Speed Trainer | Ramp/start/target/step/mode/bar configuration; swap and steppers; mic nudge; countdown/HUD/retreat/skip/cancel; scored and unscored result | U01, U06–U07, U09, U13, U15, U17 |
| Tuner | Permission strip; reference morph/slider/steppers/reset; nudge; gauge/note/octave/cents/status/frequency/input level; ambient states/rail/suppression/details; drone keyboard/voice/blend/level/play; calibration states/confirmation/clear; feedback; ad | U01–U02, U04–U06, U08, U11, U13, U15, U17–U18 |
| Chords | Empty/single/dyad/identified/unnamed hero; guitar/piano chooser/drawing/scroll hint; mic/permission/engine; collected notes/remove/clear; pace/play; tips/alternatives; ad | U01–U02, U04–U06, U11, U14–U15, U17 |
| Rhythm | Currency/daily bonus/loyalty/streak/collection; difficulty scores/stars; daily target; mic nudge; metronome-conflict dialog; countdown; score/combo/remaining; beat dots/equalizer/highway/quality; TAP/stop; results/replay/done; ad | U01, U09–U12, U15–U18 |
| Settings | Tempo/time-signature/classification/accents/reset; Groove Check toggle/info; sound/affinity/premium chips; volume; flash; notifications; items; ads purchase; about/build; developer section | U01, U04–U06, U11, U17–U18 |
| Purchase/currency/collection dialogs | Loading/unavailable/owned/purchasing actions; preview/restore; premium audio/item showcase; currency/rules/bonus; locked/unlocked collection/progress/item previews | U01, U04, U10, U13, U15, U17–U20 |
| Announcements/unlocks | Feature dispatcher and all four intro designs; unlocked-item artwork/text/dismiss; reusable avatar developer dialog | U01, U09, U13, U17–U18, U20 |
| Developer UI | Settings actions plus mic/timing/tuner/chord/profile overlays and running pills; source scan only | Shared issues apply; diagnostic monospace/extra colors are intentional |

## What is already consistent and worth preserving

- AppCard/AppInset and the card surface alias establish shared fill, rim and corners.
- RaisedControl/raisedFace, PlayStopKey, AppFilterChip, AppSwitch and GoldSlider provide reusable building blocks.
- PremiumChip deliberately preserves the premium star after purchase; do not reinterpret it as a lock icon.
- Tuner/Chords share piano rendering and listening/input-level components.
- Currency/reward and cosmetic registry/preview rendering reuse real assets instead of separately drawn mockups.
- Instrument glyph path parsing is cached, with a shared render/stroke recipe.
- There is no separate bundled font asset system to consolidate; the default family is consistent, with an intentional monospaced timer and developer diagnostics.
- Launcher assets contain six byte-identical regular/round groups (five bitmap densities and adaptive XML). These are launcher compatibility aliases, not six distinct visual designs. Consolidation is optional and low priority; do not delete references blindly.
- Cosmetic/art palette differences are intentional illustration colors, not automatically UI-token violations.

## Recommended repair order and verification

1. Shared contrast and accessible selection/disabled/label semantics (U01–U07).
2. Correct stale ambient UI and unify modal/banner boundaries (U08–U10).
3. Constrained ad sizing and remaining primary action styles (U11–U12).
4. Make dialogs/results scroll-safe; verify compact Chords, text scale and landscape layouts (U13–U16).
5. Centralize semantic text styles, shells and preview faces; profile before drawing optimizations (U17–U20).

Minimum rendered pass still outstanding: 320/360/412 dp portrait; compact landscape; rail-width window; split-screen; font scale 1.0/1.3/1.5/2.0; Save Preset with keyboard; all denied/granted permissions; free/ad-free; paid/locked/loading/error/purchasing states; active practice/trainer with presets; long chord names; all result/announcement surfaces. Run TalkBack and keyboard/Switch Access over every actionable element, then measure frames/allocations on representative hardware.

Use a small meaningful Compose test set for real disabled/selected semantics and accessible actions once fixes exist, plus representative rendered checks. A source-only audit cannot substitute for that final visual pass.

## Deliverables

- This report.
- [Source inventory](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/audits/2026-10-05_ui_source_inventory.csv): every scoped Kotlin file, functions, literal font-size inventory, line-height/semantics/animation/dialog/card indicators.
- [Resource inventory](C:/Users/wlambrechts/AndroidStudioProjects/MetroGnome/audits/2026-10-05_ui_resource_inventory.csv): every Android resource, byte count and SHA-256.

Only audit artifacts were added. No source fixes, commits, branch changes or audio changes were made.

