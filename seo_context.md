# Metro Gnome - SEO & Growth Context

## Open Action Items

| Item | Priority | Due / Status |
|---|---|---|
| Musician Wave "8 Best Rhythm Training Apps" outreach | Done | Sent 2026-07-03. Awaiting response |
| Orchestra Central "5 Best Tuner Apps" follow-up | Done | Sent 2026-07-03. Awaiting response |
| BeatIt.tv "Top 5 Metronome Apps" follow-up | Done | Sent 2026-07-03. Awaiting response |
| Reddit: find correct subreddit for Gnotes/app post | Medium | r/WeAreTheMusicMakers ruled out. Draft ready. Candidates: r/androidapps, r/androiddev, r/musicproduction. r/androidapps rules not confirmable via web search (2026-06-12); check the subreddit sidebar/wiki directly (many subs restrict self-promo to a weekly thread) |
| Code: Surface installed-days loyalty bonus to users | Medium | Not done |
| r/drums re-entry | Low | Eligible (42+ days clear since 2026-05-22) but user is not doing Reddit outreach for now (2026-07-03) - revisit later, do not propose again unless asked |
| r/learnmusic first post | Medium | Never posted |
| Musician Wave "10 Best Metronome Apps" follow-up | Done | Sent 2026-07-03. Awaiting response |
| Colin Dorman outreach | High | Drafted 2026-06-12 (not 07-03 as previously logged - that date was a reconfirmation, not the original draft). **32 days idle as of 2026-07-14, the oldest open item in the queue.** Zero cost, no code, ready to submit now. No public email - contact is via form at colindorman.com/biography/contact-me/ (Name/Email/Topic/Subject/Message). Full text in seo_reports/2026-07-14.md. Not yet submitted. |
| Build: "Groove Score Challenge" shareable result card | High | NEW 2026-07-03. Add a share button to `PracticeCompleteOverlay.kt` (~line 144) and `SpeedTrainerResultOverlay.kt` next to the dismiss button, gated on `grooveScore > 0`. Renders a `Bitmap` card (score + Metro + Seal stamp from `Seal.kt`) via `Intent.ACTION_SEND`. Fixed 90 BPM / 4/4 / 20s challenge preset for comparability. Rationale: matches the currently-trending "only 1% can stay on beat" TikTok/Reddit rhythm-challenge template with zero new detection code - reuses `GrooveScorer`/`SessionAnalyzer` as-is. See seo_reports/2026-07-03.md for full spec. No share-intent code exists in the app yet. |
| Risk: brand-name search collision | Medium | REVISED 2026-07-03 with real GSC data (see below): the app IS indexed and appearing for "metrognome" (14 impr in the top-queries pull), so this is NOT an indexing/visibility miss as first suspected from a generic web search. It's a ranking-position problem shared across every query (avg position 14.6, see Website Indexing Status) - two older namesake apps likely hold the higher slots. Fix is the same as the CTR problem below: backlinks/authority, not a separate technical fix. The share-card idea (above) is parked per dev - not pursuing now. |
| GSC: pull fresh impression/query data from Search Console | Done 2026-07-03 | Pulled 3-month Performance snapshot: 340 impressions, 1 click, 0.3% CTR, avg position 14.6. Top queries: drum metronome app (27), metrognome (14), is google metronome accurate (9), best metronome app for drummers (9). Confirms real impression growth (~4.5x+ vs the 06-17 baseline of 17) but conversion is blocked by position, not indexing. See Website Indexing Status. |
| GSC: Validate Fix on homepage "Duplicate, Google chose different canonical" | Done 2026-07-03 | Validate Fix submitted and passed; canonical issue resolved in GSC |
| Colin Dorman outreach | Low | Still not sent as of 2026-07-13 (drafted 2026-07-03, 10 days idle). Zero-cost, no code needed - submit via colindorman.com/biography/contact-me/ |
| Geekflare "11 Best Metronome Apps" outreach | Medium | NEW 2026-07-13. Metro Gnome absent from geekflare.com/consumer-tech/best-metronome-apps/ (11 apps listed, no usual exclusions). No editorial email published - contact via geekflare.com/contact/ form or LinkedIn to author Dhruv Parmar (linkedin.com/in/parmar-dhruv/). Full email drafted in seo_reports/2026-07-13.md |
| GSC: get average position summary | Low | NEW 2026-07-13. 2026-07-13 screenshot had queries/impressions/clicks but not the avg-position chip; was 14.6 as of 2026-07-03. Needed to confirm whether new clicks reflect a real ranking move. Still open 2026-07-14 - not re-asked daily since it's an unchanged, already-logged gap, not a new one |
| Android Authority follow-up (2nd) | Medium | NEW 2026-07-14. Original follow-up sent 06-17, now 27 days with no reply - next outreach action due after Colin Dorman is sent |
| Reddit `site:reddit.com` search tooling gap | Low | NEW 2026-07-14. Identical empty result 4 consecutive sessions (06-23, 07-03, 07-13, 07-14). Confirmed tool limitation, not evidence Reddit is quiet - stop re-running this exact daily check until tooling changes or a manual pass is requested |
| Bulletproof Musician "Five Best Metronome Apps" | N/A | Checked 2026-07-14 and rejected - stale, non-Android-specific list (winner "Metronome Plus," runners-up include discontinued/iOS-only apps). Not a viable outreach target, do not re-propose |
| GSC: confirm indexing of new /tools/ pages | Medium | NEW 2026-07-14. New Tools section shipped and deployed to master same day (commit 2c4b47f): `https://metrognome.co.za/tools/` and `https://metrognome.co.za/tools/tap-tempo-bpm-finder.html` (Tap Tempo & BPM Finder tool). Both submitted via GSC URL Inspection > Request Indexing on 2026-07-14; sitemap.xml (updated same day with both URLs) also resubmitted. Awaiting confirmation - check URL Inspection or Performance > Pages filtered to /tools/ in a few days, same pattern as the 2026-06-03 batch which indexed within 3 days |
| RELEASE GATE: promote v5.9 to production | Done 2026-06-23 | v5.9 / versionCode 55 published public 2026-06-23. Gate cleared; all outreach hooks now live. Production has since advanced to v5.12 / versionCode 62 (2026-07-03) |
| Android Authority follow-up | Done | Follow-up sent 2026-06-17 (confirmed by dev 2026-06-23). Awaiting response |
| Content: flip "How to Improve Your Timing" blog to present tense | Done 2026-06-12 | Done on master (commit 09ec535): Groove Check now public per dev. NOTE: confirm Play Store versionCode/date and update the "v5.6 NOT in production" lines in the App + Core features sections to match |
| Vercel: confirm 2026-06-11 master deploy landed | Done 2026-06-12 | Blog post verified live; webhook had missed it, fixed via empty trigger commit. Webhook misses recur intermittently (remedy: empty commit). Website config lives in landing/vercel.json (Root Directory = landing) |

---

## Website Indexing Status (as of 2026-06-12)
- metrognome.co.za: **INDEXED** - Google email confirmed impressions started June 6, 2026
- **Canonical issue found + fixed 2026-06-12:** GSC reported the homepage as "Duplicate, Google chose different canonical than user", Google-selected canonical = `https://metro-gnome-gilt.vercel.app/`. The canonical TAG was correct, but the .vercel.app default domain was fully indexable and served identical content, so Google overrode it. Fix: host-conditional 301/308 from metro-gnome-gilt.vercel.app to metrognome.co.za (plus /index.html, /blog, /blog/index.html normalisation), placed in **landing/vercel.json** (root-level vercel.json is ignored; Root Directory = landing). All redirects verified live. GSC "Validate Fix" still to submit.
- **On-page SEO overhaul 2026-06-12 (live):** showcase rebuilt with 6 current screenshots (4-tab nav); features expanded to 6 pillars (adds Speed Trainer, Practice & Streaks, Rewards & Collectibles); FAQPage structured data added (was none); SoftwareApplication featureList added; meta + OG descriptions refreshed; Twitter Card tags added; new visible speed-trainer FAQ. Widened coverage of the speed-trainer / practice / rewards keyword clusters.
- Fix applied: GSC Request Indexing submitted for all 8 pages on 2026-06-03 - worked within 3 days
- GSC baseline captured 2026-06-17: 17 impressions / 0 clicks over 28 days; top query "is google metronome accurate" (5 impr), drumming queries 8 of 17. Zero clicks expected at <2 weeks indexed (positions 20+).
- **GSC snapshot 2026-07-03 (3-month window, effectively ~4 weeks of real data since indexing started June 6):** 340 total impressions, 1 total click, 0.3% avg CTR, **14.6 avg position**. Top queries: drum metronome app (27 impr), metrognome (14 impr, brand term - confirms the site IS appearing for its own name, just not on page 1), is google metronome accurate (9), best metronome app for drummers (9), best metronome for drummers (6), drummer metronome app (3), metronome drum app / best drum metronome app / best metronome app / best metronome apps for drummers (2 each). All queries 0 clicks. Diagnosis: impressions are growing strongly (~4.5x+ since 06-17) and clustering correctly around the drummer-intent keyword set, but avg position ~14-15 (bottom of page 2) means near-zero CTR is expected and not a snippet/technical problem - the single lever that moves this is backlink authority (the queued roundup outreach), which raises position, which is what converts impressions to clicks. Re-check position specifically (not just impressions) on the next pull to see if outreach links are moving the needle.
- **GSC snapshot 2026-07-13 (3-month window, Queries tab, user-supplied screenshot; avg position not visible this pull):** total clicks across all 37 queries = 2 (up from 1 on 07-03). Top by clicks: metro gnome (1 click, 27 impr - two-word exact brand name), best metronome app for drummers (1 click, 18 impr - first-ever click on a commercial priority keyword, not just the brand term), metrognome (0 clicks, 45 impr, up from 14), drum metronome app (0 clicks, 41 impr, up from 27), is google metronome accurate (9), best metronome for drummers (7), odd meters (5, NEW query), gnome metronome (3), metronome gnome (3), best drum metronome app (3). Two findings refine prior diagnosis: (1) the brand-collision risk below is specifically on the one-word "metrognome" query (still 0 clicks, two namesake apps compete there) - the two-word "metro gnome" query is converting fine on its own, so the collision is narrower than first framed; (2) "odd meters" appearing as a brand-new query is the first direct confirmation that the "Time Signatures Explained" post (published 2026-06-27) is what drove the impressions climb starting 06-28 noted below - not just a timing coincidence.
- **Impressions timeline (chart reviewed 2026-07-03):** flat at ~0 until indexing kicked in ~03/06, fluctuated 5-27/day through mid-late June, then a clear sustained climb from ~28/06 to 03/07 (roughly 20 -> 35+/day). The climb start (28/06) lines up one day after the "Time Signatures Explained" blog post went live on master (`3901f9b`, 2026-06-27) - plausible driver: new indexable content adding query-matching surface area. The single click (of 1 total, 3-month window) also lands at the very end of this range, not spread out - n=1, don't over-read it, but directionally consistent with position slowly improving. Takeaway: publishing content visibly moves impressions within about a week; worth doing more of it, and the outreach emails should go out now while the curve is climbing rather than after it plateaus.
- CONTENT IDEA (caution): "Is Google's Metronome Accurate?" targets the top query. Build ONLY if scoped tightly to the branded Google-built-in-metronome comparison and cross-linked to the two existing accuracy posts (how-accurate-is-your-metronome-app.html, why-your-metronome-app-might-be-lying-to-you.html). A generic accuracy post would cannibalise both - do not write that. User plans to draft this one using Fable (2026-07-03) - not started by Sonnet.
- **NEW site section 2026-07-14: Tools.** `landing/tools/` added on master (commit 2c4b47f), a new top-level nav item alongside Blog. First entry: `tools/tap-tempo-bpm-finder.html`, a free interactive tap-tempo/BPM-finder built as a linkable asset (targets "tap tempo," "bpm finder," "tempo marking chart" keywords, not yet covered by any Priority keyword below). Reuses landing/blog/style.css sitewide; tempo marking table is sourced directly from `MetronomeScreen.kt`'s `tempoLabel()` so it cannot drift from the app again. Built to expand with more tools later via the same tools/index.html listing pattern as blog/index.html. Submitted for GSC indexing same day - see Open Action Items.

## App
- Name: Metro Gnome: Metronome & Tuner
- Package: com.wynandl.metrognome
- Play Store: https://play.google.com/store/apps/details?id=com.wynandl.metrognome
- Website: http://www.metrognome.co.za
- Category: Music & Audio
- Current installs: 1,000+
- Last updated (production / Play Store): **2026-07-03, v5.12 / versionCode 62**. Bundles v5.9 -> v5.12: ad-manager cleanup, premium sounds (Kalimba/Cowbell) + instrument-affinity icons, batch mic scoring (SessionAnalyzer) + spectral clap rejection, Groove Check reward + onboarding nudge, free-feature gate removal, mic self-test calibration-first gates, app-wide Seal asset. This window (2026-06-23 to 2026-07-03) was pure engine/quality work - zero outreach or marketing activity occurred; see Open Action Items for the growing overdue queue.
- Live site + how-to-improve-your-timing.html now MATCH production: Groove Check present-tense copy is accurate, and the outreach hooks (Groove Check, noisy-room tuner, Cowbell) all reference live features. Outreach follow-ups are unblocked (but not yet sent - see Open Action Items).
- seo_context last updated: 2026-07-14
- Rating: not yet established at scale
- Brand-name search collision found 2026-07-03: two unrelated older apps ("MetroGnome" com.josmith42.metrognome, "Metrognome" com.jeremiahroque.metrognome) outrank this app for its own name in general web search - see Open Action Items

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
- Musician Wave "10 Best Metronome Apps" - emailed hello@musicianwave.com - 2026-05-29, follow-up sent 2026-07-03 - awaiting response
- Musician Wave "8 Best Rhythm Training Apps" - emailed hello@musicianwave.com - 2026-07-03 - awaiting response
- Android Authority "Best Metronome Apps for Android" - emailed joseph.hindy@androidauthority.com - 2026-06-02, follow-up sent 2026-06-17 (confirmed) - awaiting response
- Melodics "Best Metronome Apps for Drummers" - DO NOT contact - they have their own built-in metronome; they are a competitor, not an independent reviewer
- Practis Blog (pract.is) - DO NOT contact - Practis is a competitor app ("Music Practice Tracker, Timer & Metronome"); their roundup articles drive traffic to their own product, not independent reviews
- BeatIt.tv "Top 5 Metronome Apps" - drummer-focused editorial site, independent reviewer. Metro Gnome absent. Outreach sent 2026-06-04, follow-up sent 2026-07-03 to info@beatit.tv - awaiting response
- Orchestra Central "5 Best Tuner Apps in 2026" (orchestracentral.com/best-tuner-apps/) - author Bobby Fisco (bobbyfisco@gmail.com). Independent site, covers iPhone and Android. Audience: strings, brass, woodwinds. Apps listed: TonalEnergy ($3.99), iStroboSoft ($9.99), Tunable ($3.99), BOSS Tuner (free), Pano Tuner (free). Metro Gnome absent. Outreach sent 2026-06-05, follow-up sent 2026-07-03 - awaiting response.
- American Songwriter "The Best Guitar Tuner Apps, Tested and Reviewed [2026]" (americansongwriter.com/best-guitar-tuner-apps/) - author Nick Stockton. Covers Android. Apps listed: GuitarTuna, Simply Tune, Fender Tune, BOSS Tuner, Positive Grid Bias FX 2, Pitched Tuner, Chordify. Metro Gnome absent. No direct author email on page - check americansongwriter.com/contact before outreach. Lower priority than Orchestra Central.
- colindorman.com "Apps for Musicians: 25+ Tuners, Metronomes, and more" - found 2026-06-12. Music educator's mega-list, 25+ entries, teacher/student audience. Metro Gnome absent. BEST new target: a 25+ list has a low bar to inclusion. Full email drafted in 2026-06-12 report; need contact (check site footer / contact page). Angle: free all-in-one (metronome + tuner + speed trainer + rhythm game), no account, offline.
- guitarmetrics.com "Best Free Guitar Tuner Apps for Beginners in 2026" - found 2026-06-12. Tuner-focused, beginner audience. Metro Gnome absent. Angle: free chromatic tuner that works in noisy rooms, adjustable reference pitch.
- androidally.com "13 Best Piano Tuner Apps for Android" - found 2026-06-12. Lower priority; Metro Gnome's chromatic tuner could fit a piano-tuner list.
- Geekflare "11 Best Metronome Apps to Improve Your Rhythm & Timing" (geekflare.com/consumer-tech/best-metronome-apps/) - found 2026-07-13. Author Dhruv Parmar (Senior Writer). 11 apps listed (Metronome Beats, Soundbrenner Pulse, Natural Metronome, Metronomerous, Metronome, Practice+ Tuner & Metronome, Pulse Metronome, Pro Metronome, Simple Metronome, Keuwlsoft Metronome, Stage Metronome), Metro Gnome absent, no usual exclusions present. No editorial email published - contact via geekflare.com/contact/ form or LinkedIn (linkedin.com/in/parmar-dhruv/). Full email drafted in seo_reports/2026-07-13.md, not yet sent.

## Competitors
- Metronome Beats (Stonekick): 26M installs, 4.8 stars - no tuner, ad-heavy, has a free speed trainer (BPM ramp, v7.1.4 June 3 2026, added MIDI control + preset backup/sync via sign-in) - NOT uncontested; Metro Gnome differentiates on structured steps + descending ramp + mic accuracy (coming) + all-in-one free app + no account/sign-in required
- Pro Metronome (EUMLab): 7.3M installs, 3.84 stars - angry users over subscription bait-and-switch, outdated Android port, last Android updates: Jan 12, 2026 and April 14, 2026 (bug fixes: Stage mode scroll + Android 15 compat - no new features); tempo trainer is paywalled and buggy
- Music Tempo Trainer (musicutils): dedicated tempo training app, no tuner, no rhythm game, updated March 2026. Modes: Constant, Increase/Decrease, Step Training (80-160 BPM), session tracking.
- Soundbrenner: 10M+ installs, latest **v1.33.0 (June 8 2026)** - routine maintenance bump, no new headline feature surfaced. Earlier June 3 build added a playback counter (auto-pause after N bars/duration). Has paid practice tracking (~$6/mo Premium). Metro Gnome's practice tools (streak, Gnotes, timer) are entirely free - direct differentiator.
- Takt (`xyz.zedler.patrick.tack`): surfaced in 2026 metronome roundups (found 2026-06-12), "recommended first" in one as "a metronome that does more than count time". Fully scanned 2026-07-13: last updated Feb 13 2026, 4.92 stars / 1.2K ratings, free, no ads, no analytics, native audio engine, tempo trainer with small-step climbs, swing feel, setlists. Privacy/quality-focused positioning distinct from the ad-heavy (Metronome Beats, Pro Metronome) and subscription (Soundbrenner) competitors.

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
