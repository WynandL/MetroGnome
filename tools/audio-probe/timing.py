"""Timing origin (review A05): is an on-time clap scored as on time?

    python timing.py <work dir> <tag> [groove_check_latency_ms]

Reads work/res/mic_<tag>.csv (AudioProbeReceiver mic_dump after a Practice or Speed Trainer
session with no claps). Each "beat" row is stamped (t_ms) when the engine callback ran and
carries in column b the time scoring expects the click to be heard: the beat reference plus
the Groove Check latency. Each "click" row is the metronome's own click as the mic heard it.

  click - expected  is what an on-time clap scores; it should be about 0.
  click - callback  is the raw output-pipeline delay (250-275 ms on an S926B), for context.

Before the A05 fix the beat reference was the callback, so click - expected read ~+190 ms.
"""
import csv, statistics as st, sys

S, tag = sys.argv[1], sys.argv[2]
lat = float(sys.argv[3]) if len(sys.argv) > 3 else None
rows = list(csv.DictReader(open(f"{S}/res/mic_{tag}.csv")))
beats, expected = [], []
for r in rows:
    if r['event'] != 'beat':
        continue
    t = int(r['t_ms'])
    if beats and t - beats[-1] < 30:   # pre-fix builds logged each beat twice
        continue
    beats.append(t)
    expected.append(int(r['b']))
clicks = sorted(int(r['t_ms']) for r in rows if r['event'] == 'click')
if len(beats) < 3:
    sys.exit(f"{tag}: only {len(beats)} beats logged; was dev mode on and the mic active?")
if not clicks:
    sys.exit(f"{tag}: no clicks heard; is the metronome muted?")
interval = st.median(b - a for a, b in zip(beats, beats[1:]))
dupes = sum(1 for r in rows if r['event'] == 'beat') - len(beats)

def spread(xs):
    q = st.quantiles(xs, n=4) if len(xs) >= 4 else [min(xs), max(xs)]
    return f"median {st.median(xs):+.1f} ms (IQR {q[0]:+.0f} to {q[-1]:+.0f}, n={len(xs)})"

pipe = [c - max(b for b in beats if b <= c) for c in clicks
        if any(b <= c for b in beats) and c - max(b for b in beats if b <= c) < interval]
score = [c - min(expected, key=lambda e: abs(c - e)) for c in clicks]
score = [d for d in score if abs(d) < interval / 2]

print(f"{tag}: beat interval {interval:.0f} ms, {len(beats)} beats ({dupes} duplicate rows), {len(clicks)} clicks")
print(f"   callback -> heard click:          {spread(pipe)}")
print(f"   an on-time clap scores about:     {spread(score)}   (pass: within +-15 of 0)")
if lat is not None:
    print(f"   (Groove Check latency in use: {lat:.1f} ms)")
