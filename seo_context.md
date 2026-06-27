# Metro Gnome - SEO & Growth Context

## Open Action Items

| Item | Priority | Due / Status |
|---|---|---|
| Musician Wave "8 Best Rhythm Training Apps" outreach | Low | OVERDUE 4 days. UNBLOCKED (v5.9 public) - send; Groove Check is the rhythm-training hook |
| Orchestra Central "5 Best Tuner Apps" follow-up | Low | OVERDUE 4 days. UNBLOCKED (v5.9 public) - send; noisy-room tuner now live |
| BeatIt.tv "Top 5 Metronome Apps" follow-up | Low | OVERDUE 5 days. UNBLOCKED (v5.9 public) - send; drummer hook = new Cowbell sound + Groove Check |
| Reddit: find correct subreddit for Gnotes/app post | Medium | r/WeAreTheMusicMakers ruled out. Draft ready. Candidates: r/androidapps, r/androiddev, r/musicproduction. r/androidapps rules not confirmable via web search (2026-06-12); check the subreddit sidebar/wiki directly (many subs restrict self-promo to a weekly thread) |
| Code: Surface installed-days loyalty bonus to users | Medium | Not done |
| r/drums re-entry | Medium | Eligible (13+ days clear since 2026-05-22) |
| r/learnmusic first post | Medium | Never posted |
| Musician Wave "10 Best Metronome Apps" follow-up | Low | OVERDUE 11 days. Lead with the article-age angle (last updated July 6, 2023). UNBLOCKED (v5.9 now public) - send |
| RELEASE GATE: promote v5.9 to production | Done 2026-06-23 | v5.9 / versionCode 55 published public 2026-06-23. Gate cleared; all outreach hooks now live |
| Android Authority follow-up | Done | Follow-up sent 2026-06-17 (confirmed by dev 2026-06-23). Awaiting response |
| GSC: pull impression/query data from Search Console | High | Indexing confirmed June 6. Now baseline-critical after the 2026-06-12 canonical fix + on-page overhaul. Data requested in 2026-06-12 report |
| GSC: Validate Fix on homepage "Duplicate, Google chose different canonical" | High | Fixed 2026-06-12 (vercel.app now 301s to metrognome.co.za). Submit Validate Fix and re-inspect the homepage; expect Google-selected canonical to flip over coming crawls |
| Content: flip "How to Improve Your Timing" blog to present tense | Done 2026-06-12 | Done on master (commit 09ec535): Groove Check now public per dev. NOTE: confirm Play Store versionCode/date and update the "v5.6 NOT in production" lines in the App + Core features sections to match |
| Vercel: confirm 2026-06-11 master deploy landed | Done 2026-06-12 | Blog post verified live; webhook had missed it, fixed via empty trigger commit. Webhook misses recur intermittently (remedy: empty commit). Website config lives in landing/vercel.json (Root Directory = landing) |

---

## Website Indexing Status (as of 2026-06-12)
- metrognome.co.za: **INDEXED** - Google email confirmed impressions started June 6, 2026
- **Canonical issue found + fixed 2026-06-12:** GSC reported the homepage as "Duplicate, Google chose different canonical than user", Google-selected canonical = `https://metro-gnome-gilt.vercel.app/`. The canonical TAG was correct, but the .vercel.app default domain was fully indexable and served identical content, so Google overrode it. Fix: host-conditional 301/308 from metro-gnome-gilt.vercel.app to metrognome.co.za (plus /index.html, /blog, /blog/index.html normalisation), placed in **landing/vercel.json** (root-level vercel.json is ignored; Root Directory = landing). All redirects verified live. GSC "Validate Fix" still to submit.
- **On-page SEO overhaul 2026-06-12 (live):** showcase rebuilt with 6 current screenshots (4-tab nav); features expanded to 6 pillars (adds Speed Trainer, Practice & Streaks, Rewards & Collectibles); FAQPage structured data added (was none); SoftwareApplication featureList added; meta + OG descriptions refreshed; Twitter Card tags added; new visible speed-trainer FAQ. Widened coverage of the speed-trainer / practice / rewards keyword clusters.
- Fix applied: GSC Request Indexing submitted for all 8 pages on 2026-06-03 - worked within 3 days
- GSC baseline captured 2026-06-17: 17 impressions / 0 clicks over 28 days; top query "is google metronome accurate" (5 impr), drumming queries 8 of 17. Zero clicks expected at <2 weeks indexed (positions 20+).
- CONTENT IDEA (caution): "Is Google's Metronome Accurate?" targets the top query. Build ONLY if scoped tightly to the branded Google-built-in-metronome comparison and cross-linked to the two existing accuracy posts (how-accurate-is-your-metronome-app.html, why-your-metronome-app-might-be-lying-to-you.html). A generic accuracy post would cannibalise both - do not write that.

## App
- Name: Metro Gnome: Metronome & Tuner
- Package: com.wynandl.metrognome
- Play Store: https://play.google.com/store/apps/details?id=com.wynandl.metrognome
- Website: http://www.metrognome.co.za
- Category: Music & Audio
- Current installs: 1,000+
- Last updated (production / Play Store): **2026-06-23, v5.9 / versionCode 55** (public). Bundles v5.6 -> v5.9: Groove Check, time signatures w/ accents, noisy-room tuner + frequency rail, Rhythm-page gamification + poll, Italian tempo labels, Kalimba + Cowbell premium sounds, instrument-affinity icons, ad/review-prompt rework.
- Live site + how-to-improve-your-timing.html now MATCH production: Groove Check present-tense copy is accurate, and the outreach hooks (Groove Check, noisy-room tuner, Cowbell) all reference live features. Outreach follow-ups are unblocked.
- seo_context last updated: 2026-06-23
- Rating: not yet established at scale

## Core features (all free)
- Hardware-timed precision metronome (sample-accurate, drift-free)
- Smart chromatic tuner with ambient noise detection - locks on fast, rejects harmonics
- Rhythm game (5 difficulty levels, tap or clap input)
- Speed Trainer - structured BPM progression (set start/target/step, ascending or descending, fixed or % increment, bars per step, repeat count, live progress bar, swap button)
- Groove Check (NEW, shipping v5.6, NOT yet in production) - opt-in microphone timing feedback for Practice and Speed Trainer. The app listens to your claps/playing, separates them from the click by frequency (spectral, not loudness), corrects for device audio latency with a one-time check, and scores how closely you land on the beat, rewarding good timing with a graded "Timing Bonus" in Gnotes. A very accurate clap also triggers a celebratory firework behind Metro. Runs entirely on-device, private, no account. The rhythm game uses the same listening engine to score clapping. This is the public face of what was the hidden "mic accuracy" code.
- Gnotes practice currency - earned by metronome play, tuner note locks, rhythm game score, speed trainer, practice sessions, daily streaks, loyalty (daily open), installed-days bonus (1pt/day per install day)
- Ad-free reward - hitting the daily Gnote goal grants 3 days ad-free automatically, no purchase needed
- Item catalog - all cosmetic items browsable with real-time Gnote unlock requirements
- Adjustable reference pitch (A=415-466 Hz) for period or alternate tuning standards
- Practice streak tracker - builds daily habit, shown in MetronomeScreen streak pill
- Tap tempo
- Multiple sound styles: click, hi-hat, woodblock, warm (free); premium sounds: bell, crystal bowl, kalimba (calming), cowbell (drummer-targeted). Instrument-affinity icons show which instruments each sound suits.
- Animated gnome character (Metro) with cosmetic item catalog unlockable via Gnotes
- Background play

## Monetisation
- Contains ads (removable) - interstitials capped: every 3rd game + max once per 5 minutes
- One-time purchase to remove ads
- Optional cosmetic in-app purchases (items for Metro and his world)
- Core experience is fully free - no subscription, ever

## Target audiences
- Guitarists, drummers, pianists, bassists, vocalists
- Music teachers (classroom and private)
- Music students
- Jazz musicians (underserved, low competition keyword)

## Developer
- Solo developer, South Africa
- Electronics engineering background
- Also built: fAIth app and Please Call Me SA (Access Comms developer name)
- Reddit account: wynand_dev (created 2026-05-22, first active day)

## Reddit Activity (do not re-post to same subreddit same day)
- r/WeAreTheMusicMakers - DO NOT post - no products, services, or self-promotion allowed in posts or comments, ever
- r/Guitar or r/guitarlessons - "Unholy Confessions metronome" thread - posted 2026-05-22
- r/drums - "metronome hi-hat click sound" thread (evergreen) - posted 2026-05-22
- r/guitarlessons - "can't subdivide the beat" thread - queued for 2026-05-23

## Roundup Outreach Log
- Musician Wave "10 Best Metronome Apps" - emailed hello@musicianwave.com - 2026-05-29 - awaiting response (allow until 2026-06-12 before follow-up). Note: article last updated July 6, 2023 - very stale; follow-up should acknowledge article age
- Musician Wave "8 Best Rhythm Training Apps" - outreach planned for 2026-06-05 (Friday)
- Android Authority "Best Metronome Apps for Android" - emailed joseph.hindy@androidauthority.com - 2026-06-02 - awaiting response (allow until 2026-06-16 before follow-up)
- Melodics "Best Metronome Apps for Drummers" - DO NOT contact - they have their own built-in metronome; they are a competitor, not an independent reviewer
- Practis Blog (pract.is) - DO NOT contact - Practis is a competitor app ("Music Practice Tracker, Timer & Metronome"); their roundup articles drive traffic to their own product, not independent reviews
- BeatIt.tv "Top 5 Metronome Apps" - drummer-focused editorial site, independent reviewer. Metro Gnome absent. Outreach sent 2026-06-04 to info@beatit.tv - allow until 2026-06-18 before follow-up
- Orchestra Central "5 Best Tuner Apps in 2026" (orchestracentral.com/best-tuner-apps/) - author Bobby Fisco (bobbyfisco@gmail.com). Independent site, covers iPhone and Android. Audience: strings, brass, woodwinds. Apps listed: TonalEnergy ($3.99), iStroboSoft ($9.99), Tunable ($3.99), BOSS Tuner (free), Pano Tuner (free). Metro Gnome absent. Outreach sent 2026-06-05 - lead angles: ambient noise detection (unique in his list), reference pitch 415-466Hz (period tuning), free. Allow until 2026-06-19 before follow-up.
- American Songwriter "The Best Guitar Tuner Apps, Tested and Reviewed [2026]" (americansongwriter.com/best-guitar-tuner-apps/) - author Nick Stockton. Covers Android. Apps listed: GuitarTuna, Simply Tune, Fender Tune, BOSS Tuner, Positive Grid Bias FX 2, Pitched Tuner, Chordify. Metro Gnome absent. No direct author email on page - check americansongwriter.com/contact before outreach. Lower priority than Orchestra Central.
- colindorman.com "Apps for Musicians: 25+ Tuners, Metronomes, and more" - found 2026-06-12. Music educator's mega-list, 25+ entries, teacher/student audience. Metro Gnome absent. BEST new target: a 25+ list has a low bar to inclusion. Full email drafted in 2026-06-12 report; need contact (check site footer / contact page). Angle: free all-in-one (metronome + tuner + speed trainer + rhythm game), no account, offline.
- guitarmetrics.com "Best Free Guitar Tuner Apps for Beginners in 2026" - found 2026-06-12. Tuner-focused, beginner audience. Metro Gnome absent. Angle: free chromatic tuner that works in noisy rooms, adjustable reference pitch.
- androidally.com "13 Best Piano Tuner Apps for Android" - found 2026-06-12. Lower priority; Metro Gnome's chromatic tuner could fit a piano-tuner list.

## Competitors
- Metronome Beats (Stonekick): 26M installs, 4.8 stars - no tuner, ad-heavy, has a free speed trainer (BPM ramp, v7.1.4 June 3 2026, added MIDI control + preset backup/sync via sign-in) - NOT uncontested; Metro Gnome differentiates on structured steps + descending ramp + mic accuracy (coming) + all-in-one free app + no account/sign-in required
- Pro Metronome (EUMLab): 7.3M installs, 3.84 stars - angry users over subscription bait-and-switch, outdated Android port, last Android updates: Jan 12, 2026 and April 14, 2026 (bug fixes: Stage mode scroll + Android 15 compat - no new features); tempo trainer is paywalled and buggy
- Music Tempo Trainer (musicutils): dedicated tempo training app, no tuner, no rhythm game, updated March 2026. Modes: Constant, Increase/Decrease, Step Training (80-160 BPM), session tracking.
- Soundbrenner: 10M+ installs, latest **v1.33.0 (June 8 2026)** - routine maintenance bump, no new headline feature surfaced. Earlier June 3 build added a playback counter (auto-pause after N bars/duration). Has paid practice tracking (~$6/mo Premium). Metro Gnome's practice tools (streak, Gnotes, timer) are entirely free - direct differentiator.
- Takt: surfaced in 2026 metronome roundups (found 2026-06-12), "recommended first" in one as "a metronome that does more than count time". Not yet scanned - research installs/features/price/last-update on next session.

## SEO decisions already made (do not revisit)
- App name stays as-is (30 char limit, already maxed)
- Short description finalised (80 chars, targets guitarists/drummers/teachers)
- No direct competitor callouts in listing copy
- No mention of developer's degree in listing
- Tags: Music & audio, Musical instrument, Guitar, Education, Entertainment
  (no better options exist in Play Store taxonomy)

## Priority keywords
- free metronome app android (8,100/mo, diff 32)
- metronome for guitar practice (4,400/mo, diff 28)
- metronome app for drummers (2,900/mo, diff 25)
- smart tuner app android (2,200/mo, diff 30)
- jazz metronome app (1,800/mo, diff 19) - uncontested
- metronome app music teacher (1,200/mo, diff 15) - uncontested
- rhythm trainer app (1,100/mo, diff 22)
- tempo trainer app android (est. 1,400/mo, diff ~15) - NOT uncontested (Metronome Beats has free BPM ramp); Metro Gnome differentiates on structured steps (fixed bars per tempo, configurable step size, ascending+descending swap) + all-in-one free app + Groove Check timing feedback (shipping v5.6); do NOT claim "only free"
- BPM trainer for musicians (est. 800/mo, diff ~10) - low competition; hook: structured step training + deceleration angle; Groove Check mic timing feedback now public-facing in v5.6
- improve your timing / how to improve timing (est. 1,000/mo, diff ~18) - NEW angle. Blog post LIVE: how-to-improve-your-timing.html (objective mic timing feedback, knowledge-of-results loop, Groove Check teaser). Complements the tempo-trainer cluster and cross-links the increasing-BPM + metronome-accuracy posts. Do NOT propose a duplicate post on this topic.
- chromatic tuner noisy room (480/mo, diff 14) - unique to Metro Gnome
