/* Metro Gnome online metronome.
   One widget, mounted wherever a page has <div data-metronome></div>
   (the homepage and /tools/online-metronome.html). Everything it needs,
   styles included, is in this file so the two pages cannot drift.

   Behaviour follows the Android app where the app has a rule:
   - BPM 20 to 300 (MetronomeViewModel.setBpm).
   - Tap tempo: a gap over 2.5 s starts a new run, the last 6 taps are
     averaged, 2 taps are enough (MetronomeViewModel.tap).
   - Tempo names are MetronomeScreen.kt's tempoLabel() table.
   - Time signatures, grouping and default accents are theory/Meter.kt
     (classify, beatGrouping, defaultAccents, label, description).
   - Each of the top number's pulses is one click at the BPM, as in the app.

   Timing: a lookahead scheduler on the Web Audio clock (the same
   discipline as the site's other tools), so the click never drifts even
   though the polling timer is imprecise. Visuals are fired at each
   click's audio time plus the reported output latency, so the lights
   move when the click is heard, not when it is scheduled. */
(function () {
  'use strict';

  var BPM_MIN = 20, BPM_MAX = 300, TOP_MIN = 1, TOP_MAX = 16;
  var DENOMS = [2, 4, 8, 16];
  var PRESETS = [[2, 4], [3, 4], [4, 4], [3, 8], [6, 8], [9, 8], [12, 8], [5, 4], [7, 8]]; /* Meter.COMMON_METERS */
  var STORE_KEY = 'mg-metronome-v1';

  /* ── Meter.kt, ported ─────────────────────────────────────────────── */
  function classify(t) {
    if (t <= 4) return 'SIMPLE';
    if (t % 3 === 0) return 'COMPOUND';
    return 'IRREGULAR';
  }
  function irregularGroups(n) {
    var groups = [], remaining = n;
    while (remaining >= 2) { groups.push(2); remaining -= 2; }
    if (remaining === 1) { if (groups.length) groups[groups.length - 1] = 3; else groups.push(1); }
    return groups;
  }
  function beatGrouping(t) {
    var c = classify(t);
    if (c === 'SIMPLE') return [Math.max(t, 1)];
    if (c === 'COMPOUND') { var a = []; for (var i = 0; i < t / 3; i++) a.push(3); return a; }
    return irregularGroups(t);
  }
  function defaultAccents(t) {
    var s = {}, idx = 0;
    beatGrouping(t).forEach(function (g) { s[idx] = true; idx += g; });
    return s;
  }
  function countWord(n) { return { 2: 'duple', 3: 'triple', 4: 'quadruple', 5: 'quintuple', 6: 'sextuple' }[n] || null; }
  function meterLabel(t) {
    var c = classify(t), w;
    if (c === 'SIMPLE') { w = countWord(t); return 'Simple' + (w ? ' ' + w : ''); }
    if (c === 'COMPOUND') { w = countWord(t / 3); return 'Compound' + (w ? ' ' + w : ''); }
    return (t % 2 === 1) ? 'Odd' : 'Irregular';
  }
  function meterDescription(t) {
    var c = classify(t);
    if (c === 'SIMPLE') return 'felt in ' + Math.max(t, 1);
    if (c === 'COMPOUND') return 'felt in ' + (t / 3) + ', beats split in three';
    return 'felt in ' + beatGrouping(t).join('+');
  }

  /* ── MetronomeScreen.kt tempoLabel(), ported ─────────────────────── */
  function tempoLabel(b) {
    if (b < 40) return 'Grave';
    if (b < 60) return 'Largo';
    if (b < 66) return 'Larghetto';
    if (b < 76) return 'Adagio';
    if (b < 96) return 'Andante';
    if (b < 110) return 'Moderato';
    if (b < 120) return 'Allegretto';
    if (b < 156) return 'Allegro';
    if (b < 176) return 'Vivace';
    if (b < 200) return 'Presto';
    return 'Prestissimo';
  }

  /* ── styles, injected once ────────────────────────────────────────── */
  var CSS = [
    '.mm{--mm-gold:var(--gold,#FFD700);--mm-purple:var(--purple,#7B4DB0);--mm-plight:var(--purple-light,#AB7DE0);--mm-pdark:var(--purple-dark,#5B2D8A);--mm-surface:var(--surface,#1E1B3A);--mm-up:var(--surface-up,#2A2550);--mm-deep:var(--surface-deep,#13102A);--mm-text:var(--text,#EEEEFF);--mm-muted:var(--text-muted,#8080AA);',
    'background:linear-gradient(180deg,var(--mm-surface),var(--mm-deep));border:1px solid rgba(255,215,0,.18);border-radius:22px;padding:1.4rem 1.1rem 1.2rem;color:var(--mm-text);text-align:center;font-family:inherit;max-width:560px;margin:0 auto;position:relative}',
    '.mm *{box-sizing:border-box}',
    '.mm-top{display:flex;align-items:center;justify-content:center;gap:1rem}',
    '.mm-round{width:46px;height:46px;border-radius:50%;border:1px solid var(--mm-up);background:var(--mm-deep);color:var(--mm-text);font-size:1.5rem;line-height:1;cursor:pointer;flex:none;touch-action:manipulation}',
    '.mm-round:hover{border-color:var(--mm-purple);color:var(--mm-gold)}',
    '.mm-bpm{min-width:7.5rem}',
    '.mm-bpm-n{font-size:3.6rem;font-weight:800;line-height:1;letter-spacing:-.02em;font-variant-numeric:tabular-nums}',
    '.mm-bpm-u{font-size:.72rem;font-weight:700;letter-spacing:.14em;color:var(--mm-muted);text-transform:uppercase;margin-top:.2rem}',
    '.mm-mark{color:var(--mm-gold);font-weight:700;font-size:.95rem;margin-top:.15rem;min-height:1.4em}',
    '.mm-slider{width:100%;margin:1rem 0 .4rem;accent-color:var(--mm-gold);height:28px}',
    '.mm-beats{display:flex;justify-content:center;flex-wrap:wrap;gap:.45rem;margin:.9rem 0 .2rem;min-height:44px}',
    '.mm-group{display:flex;gap:.35rem}',
    '.mm-group+.mm-group{margin-left:.55rem}',
    '.mm-beat{width:34px;height:34px;border-radius:50%;border:1px solid var(--mm-up);background:var(--mm-deep);color:var(--mm-muted);font-weight:800;font-size:.8rem;cursor:pointer;transition:transform .08s,background .08s;padding:0}',
    '.mm-beat.acc{border-color:rgba(255,215,0,.55);color:var(--mm-gold)}',
    '.mm-beat.on{background:var(--mm-plight);color:#1a0a2e;transform:scale(1.15)}',
    '.mm-beat.acc.on{background:var(--mm-gold);color:#1a0a2e;transform:scale(1.22);box-shadow:0 0 18px rgba(255,215,0,.55)}',
    '.mm-hint{font-size:.78rem;color:var(--mm-muted);margin:.25rem 0 0;line-height:1.4}',
    '.mm-row{display:flex;justify-content:center;align-items:center;gap:.6rem;margin-top:1rem;flex-wrap:wrap}',
    '.mm-play{flex:1 1 auto;max-width:240px;height:56px;border-radius:16px;border:1px solid var(--mm-plight);background:var(--mm-pdark);color:#fff;font-weight:800;font-size:1.05rem;letter-spacing:.06em;cursor:pointer;touch-action:manipulation}',
    '.mm-play:hover{background:var(--mm-purple)}',
    '.mm-play.live{background:#7a2440;border-color:#e0607e}',
    '.mm-tap{width:84px;height:56px;border-radius:16px;border:1px solid var(--mm-up);background:var(--mm-deep);color:var(--mm-text);font-weight:800;letter-spacing:.08em;cursor:pointer;touch-action:manipulation}',
    '.mm-tap:active,.mm-tap.hit{border-color:var(--mm-gold);color:var(--mm-gold)}',
    '.mm-opts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem 1.2rem;margin-top:1.2rem;padding-top:1.1rem;border-top:1px solid var(--mm-up);text-align:left}',
    '@media (max-width:420px){.mm-opts{grid-template-columns:1fr}.mm-compact .mm-pre{display:none}}',
    '.mm-opt label,.mm-optl{display:block;font-size:.68rem;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--mm-muted);margin-bottom:.35rem}',
    '.mm-seg{display:flex;flex-wrap:wrap;gap:.3rem}',
    '.mm-chip{border:1px solid var(--mm-up);background:var(--mm-deep);color:var(--mm-muted);border-radius:999px;padding:.3rem .65rem;font-size:.82rem;font-weight:700;cursor:pointer;line-height:1.3}',
    '.mm-chip:hover{color:var(--mm-text);border-color:var(--mm-purple)}',
    '.mm-chip.sel{border-color:var(--mm-gold);color:var(--mm-gold);background:rgba(255,215,0,.08)}',
    '.mm-ts{display:flex;align-items:center;gap:.4rem}',
    '.mm-ts .mm-round{width:34px;height:34px;font-size:1.1rem}',
    '.mm-ts-n{font-weight:800;font-size:1.25rem;min-width:3.2rem;text-align:center;font-variant-numeric:tabular-nums}',
    '.mm-vol{width:100%;accent-color:var(--mm-gold)}',
    '.mm-meter{font-size:.8rem;color:var(--mm-muted);margin-top:.3rem}',
    '.mm button:focus-visible,.mm input:focus-visible{outline:2px solid var(--mm-gold);outline-offset:2px}',
    '.mm-compact .mm-adv{display:none}',
    '@media (max-width:420px){.mm-bpm-n{font-size:3rem}.mm-beat{width:30px;height:30px}}',
    '@media (prefers-reduced-motion:reduce){.mm-beat{transition:none}}'
  ].join('');
  function injectCss() {
    if (document.getElementById('mm-css')) return;
    var s = document.createElement('style');
    s.id = 'mm-css';
    s.textContent = CSS;
    document.head.appendChild(s);
  }

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function load() {
    try { return JSON.parse(window.localStorage.getItem(STORE_KEY) || 'null'); } catch (e) { return null; }
  }
  function save(st) {
    try { window.localStorage.setItem(STORE_KEY, JSON.stringify(st)); } catch (e) { /* private mode */ }
  }

  function mount(root) {
    if (root.__mm) return;
    root.__mm = true;
    injectCss();
    var compact = root.hasAttribute('data-compact');

    /* ── state ──────────────────────────────────────────────────────── */
    var saved = load() || {};
    var st = {
      bpm: clampBpm(saved.bpm || 100),
      top: saved.top >= TOP_MIN && saved.top <= TOP_MAX ? saved.top : 4,
      bottom: DENOMS.indexOf(saved.bottom) >= 0 ? saved.bottom : 4,
      sub: [1, 2, 3, 4].indexOf(saved.sub) >= 0 ? saved.sub : 1,
      sound: ['classic', 'wood', 'hihat'].indexOf(saved.sound) >= 0 ? saved.sound : 'classic',
      vol: typeof saved.vol === 'number' ? Math.max(0, Math.min(1, saved.vol)) : 0.8,
      accents: null
    };
    (function readParams() {
      var q; try { q = new URLSearchParams(window.location.search); } catch (e) { return; }
      var b = parseInt(q.get('bpm'), 10);
      if (b >= BPM_MIN && b <= BPM_MAX) st.bpm = b;
      var ts = (q.get('ts') || '').replace('/', '-').split('-');
      if (ts.length === 2) {
        var t = parseInt(ts[0], 10), d = parseInt(ts[1], 10);
        if (t >= TOP_MIN && t <= TOP_MAX && DENOMS.indexOf(d) >= 0) { st.top = t; st.bottom = d; }
      }
    })();
    st.accents = Array.isArray(saved.accents) && saved.top === st.top ? arrToSet(saved.accents) : defaultAccents(st.top);

    function clampBpm(b) { return Math.max(BPM_MIN, Math.min(BPM_MAX, Math.round(b))); }
    function arrToSet(a) { var s = {}; a.forEach(function (i) { if (i >= 0 && i < 16) s[i] = true; }); return s; }
    function persist() {
      save({ bpm: st.bpm, top: st.top, bottom: st.bottom, sub: st.sub, sound: st.sound, vol: st.vol,
             accents: Object.keys(st.accents).map(Number) });
    }

    /* ── DOM ─────────────────────────────────────────────────────────── */
    root.innerHTML = '';
    var box = el('div', 'mm' + (compact ? ' mm-compact' : ''));
    box.setAttribute('role', 'group');
    box.setAttribute('aria-label', 'Online metronome');

    var topRow = el('div', 'mm-top');
    var minus = el('button', 'mm-round', '−'); minus.type = 'button'; minus.setAttribute('aria-label', 'Slower');
    var plus = el('button', 'mm-round', '+'); plus.type = 'button'; plus.setAttribute('aria-label', 'Faster');
    var bpmBox = el('div', 'mm-bpm');
    var bpmN = el('div', 'mm-bpm-n'); bpmN.setAttribute('aria-live', 'polite');
    var bpmU = el('div', 'mm-bpm-u', 'BPM');
    var mark = el('div', 'mm-mark');
    bpmBox.appendChild(bpmN); bpmBox.appendChild(bpmU); bpmBox.appendChild(mark);
    topRow.appendChild(minus); topRow.appendChild(bpmBox); topRow.appendChild(plus);
    box.appendChild(topRow);

    var slider = el('input', 'mm-slider');
    slider.type = 'range'; slider.min = BPM_MIN; slider.max = BPM_MAX; slider.step = 1;
    slider.setAttribute('aria-label', 'Tempo in beats per minute');
    box.appendChild(slider);

    var beats = el('div', 'mm-beats');
    box.appendChild(beats);
    var meterLine = el('div', 'mm-meter');
    box.appendChild(meterLine);

    var row = el('div', 'mm-row');
    var play = el('button', 'mm-play', '▶ START'); play.type = 'button';
    var tap = el('button', 'mm-tap', 'TAP'); tap.type = 'button'; tap.setAttribute('aria-label', 'Tap tempo');
    row.appendChild(play); row.appendChild(tap);
    box.appendChild(row);

    var opts = el('div', 'mm-opts');

    var tsOpt = el('div', 'mm-opt');
    tsOpt.appendChild(el('span', 'mm-optl', 'Time signature'));
    var tsRow = el('div', 'mm-ts');
    var tsMinus = el('button', 'mm-round', '−'); tsMinus.type = 'button'; tsMinus.setAttribute('aria-label', 'Fewer beats per bar');
    var tsN = el('span', 'mm-ts-n');
    var tsPlus = el('button', 'mm-round', '+'); tsPlus.type = 'button'; tsPlus.setAttribute('aria-label', 'More beats per bar');
    tsRow.appendChild(tsMinus); tsRow.appendChild(tsN); tsRow.appendChild(tsPlus);
    tsOpt.appendChild(tsRow);
    var denomSeg = el('div', 'mm-seg'); denomSeg.style.marginTop = '.4rem';
    var denomBtns = DENOMS.map(function (d) {
      var b = el('button', 'mm-chip', '/' + d); b.type = 'button';
      b.setAttribute('aria-label', 'Note value ' + d);
      b.addEventListener('click', function () { st.bottom = d; refresh(); });
      denomSeg.appendChild(b); return b;
    });
    tsOpt.appendChild(denomSeg);
    opts.appendChild(tsOpt);

    var preOpt = el('div', 'mm-opt mm-pre');
    preOpt.appendChild(el('span', 'mm-optl', 'Common meters'));
    var preSeg = el('div', 'mm-seg');
    var preBtns = PRESETS.map(function (p) {
      var b = el('button', 'mm-chip', p[0] + '/' + p[1]); b.type = 'button';
      b.addEventListener('click', function () { setMeter(p[0], p[1]); });
      preSeg.appendChild(b); return b;
    });
    preOpt.appendChild(preSeg);
    opts.appendChild(preOpt);

    var subOpt = el('div', 'mm-opt mm-adv');
    subOpt.appendChild(el('span', 'mm-optl', 'Subdivision'));
    var subSeg = el('div', 'mm-seg');
    var SUBS = [[1, 'Off'], [2, '2'], [3, '3'], [4, '4']];
    var subBtns = SUBS.map(function (s) {
      var b = el('button', 'mm-chip', s[1]); b.type = 'button';
      b.setAttribute('aria-label', s[0] === 1 ? 'No subdivision' : s[0] + ' clicks per beat');
      b.addEventListener('click', function () { st.sub = s[0]; refresh(); });
      subSeg.appendChild(b); return b;
    });
    subOpt.appendChild(subSeg);
    opts.appendChild(subOpt);

    var sndOpt = el('div', 'mm-opt mm-adv');
    sndOpt.appendChild(el('span', 'mm-optl', 'Sound'));
    var sndSeg = el('div', 'mm-seg');
    var SOUNDS = [['classic', 'Classic'], ['wood', 'Wood'], ['hihat', 'Hi-Hat']];
    var sndBtns = SOUNDS.map(function (s) {
      var b = el('button', 'mm-chip', s[1]); b.type = 'button';
      b.addEventListener('click', function () { st.sound = s[0]; refresh(); if (!playing) preview(); });
      sndSeg.appendChild(b); return b;
    });
    sndOpt.appendChild(sndSeg);
    var volLabel = el('label', '', 'Volume');
    volLabel.style.marginTop = '.6rem';
    var vol = el('input', 'mm-vol'); vol.type = 'range'; vol.min = 0; vol.max = 100; vol.step = 1;
    vol.setAttribute('aria-label', 'Volume');
    sndOpt.appendChild(volLabel); sndOpt.appendChild(vol);
    opts.appendChild(sndOpt);

    box.appendChild(opts);
    var hint = el('p', 'mm-hint mm-adv', 'Tap a beat to accent it or take the accent off. Keys: Space starts and stops, arrows change the tempo, T taps.');
    box.appendChild(hint);
    root.appendChild(box);

    /* ── render ──────────────────────────────────────────────────────── */
    var beatEls = [];
    function renderBeats() {
      beats.innerHTML = '';
      beatEls = [];
      var idx = 0;
      beatGrouping(st.top).forEach(function (g) {
        var grp = el('div', 'mm-group');
        for (var i = 0; i < g; i++) {
          (function (n) {
            var b = el('button', 'mm-beat' + (st.accents[n] ? ' acc' : ''), String(n + 1));
            b.type = 'button';
            b.setAttribute('aria-pressed', st.accents[n] ? 'true' : 'false');
            b.setAttribute('aria-label', 'Beat ' + (n + 1) + (st.accents[n] ? ', accented' : ''));
            b.addEventListener('click', function () {
              if (st.accents[n]) delete st.accents[n]; else st.accents[n] = true;
              refresh();
            });
            grp.appendChild(b);
            beatEls.push(b);
          })(idx);
          idx++;
        }
        beats.appendChild(grp);
      });
    }

    function refresh() {
      bpmN.textContent = String(st.bpm);
      mark.textContent = tempoLabel(st.bpm);
      slider.value = String(st.bpm);
      tsN.textContent = st.top + '/' + st.bottom;
      meterLine.textContent = st.top + '/' + st.bottom + ': ' + meterLabel(st.top) + ', ' + meterDescription(st.top);
      denomBtns.forEach(function (b, i) { b.classList.toggle('sel', DENOMS[i] === st.bottom); });
      preBtns.forEach(function (b, i) { b.classList.toggle('sel', PRESETS[i][0] === st.top && PRESETS[i][1] === st.bottom); });
      subBtns.forEach(function (b, i) { b.classList.toggle('sel', SUBS[i][0] === st.sub); });
      sndBtns.forEach(function (b, i) { b.classList.toggle('sel', SOUNDS[i][0] === st.sound); });
      vol.value = String(Math.round(st.vol * 100));
      renderBeats();
      persist();
    }

    function setBpm(b) { st.bpm = clampBpm(b); refresh(); }
    function setMeter(t, d) {
      t = Math.max(TOP_MIN, Math.min(TOP_MAX, t));
      if (t !== st.top) st.accents = defaultAccents(t);
      st.top = t; st.bottom = d;
      if (pulse >= st.top) pulse = 0;
      refresh();
    }

    /* ── audio ───────────────────────────────────────────────────────── */
    var ctx = null, master = null, noiseBuf = null;
    var timer = null, nextTime = 0, pulse = 0, subIdx = 0, playing = false;
    var LOOKAHEAD_MS = 25, AHEAD_S = 0.12;

    function ensureAudio() {
      if (!ctx) {
        var AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return false;
        ctx = new AC();
        master = ctx.createGain();
        master.connect(ctx.destination);
        noiseBuf = ctx.createBuffer(1, Math.floor(ctx.sampleRate * 0.08), ctx.sampleRate);
        var d = noiseBuf.getChannelData(0), seed = 12345;
        for (var i = 0; i < d.length; i++) { seed = (seed * 1103515245 + 12345) & 0x7fffffff; d[i] = seed / 0x3fffffff - 1; }
      }
      if (ctx.state === 'suspended') ctx.resume();
      master.gain.value = st.vol;
      return true;
    }

    /* kind: 2 accent, 1 beat, 0 subdivision */
    function voice(time, kind) {
      var level = kind === 2 ? 0.9 : kind === 1 ? 0.62 : 0.32;
      var g = ctx.createGain();
      g.connect(master);
      if (st.sound === 'hihat') {
        var src = ctx.createBufferSource(); src.buffer = noiseBuf;
        var f = ctx.createBiquadFilter(); f.type = 'bandpass';
        f.frequency.value = kind === 2 ? 7000 : 9000; f.Q.value = 1.2;
        var hp = ctx.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 5000;
        src.connect(hp).connect(f).connect(g);
        var len = kind === 2 ? 0.07 : 0.035;
        g.gain.setValueAtTime(0.0001, time);
        g.gain.exponentialRampToValueAtTime(level * 1.6, time + 0.001);
        g.gain.exponentialRampToValueAtTime(0.0001, time + len);
        src.start(time); src.stop(time + len + 0.01);
        return;
      }
      var osc = ctx.createOscillator();
      var len2;
      if (st.sound === 'wood') {
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(kind === 2 ? 1250 : kind === 1 ? 880 : 700, time);
        osc.frequency.exponentialRampToValueAtTime(kind === 2 ? 1000 : 700, time + 0.03);
        len2 = 0.06;
      } else {
        osc.type = 'square';
        osc.frequency.value = kind === 2 ? 1800 : kind === 1 ? 1100 : 900;
        level *= 0.5;
        len2 = 0.045;
      }
      osc.connect(g);
      g.gain.setValueAtTime(0.0001, time);
      g.gain.exponentialRampToValueAtTime(level, time + 0.002);
      g.gain.exponentialRampToValueAtTime(0.0001, time + len2);
      osc.start(time); osc.stop(time + len2 + 0.01);
    }

    function lightAt(time, idx) {
      var lat = (ctx.outputLatency || ctx.baseLatency || 0);
      var ms = Math.max(0, (time - ctx.currentTime + lat) * 1000);
      setTimeout(function () {
        if (!playing) return;
        beatEls.forEach(function (b) { b.classList.remove('on'); });
        if (beatEls[idx]) beatEls[idx].classList.add('on');
        root.dispatchEvent(new CustomEvent('metronome:beat', { detail: { index: idx, accent: !!st.accents[idx] } }));
      }, ms);
    }

    function schedule() {
      var beatLen = 60 / st.bpm;
      while (nextTime < ctx.currentTime + AHEAD_S) {
        if (subIdx === 0) {
          if (pulse >= st.top) pulse = 0;
          voice(nextTime, st.accents[pulse] ? 2 : 1);
          lightAt(nextTime, pulse);
        } else {
          voice(nextTime, 0);
        }
        nextTime += beatLen / st.sub;
        subIdx++;
        if (subIdx >= st.sub) { subIdx = 0; pulse = (pulse + 1) % st.top; }
      }
    }

    function start() {
      if (!ensureAudio()) return;
      pulse = 0; subIdx = 0;
      nextTime = ctx.currentTime + 0.08;
      playing = true;
      schedule();
      timer = setInterval(schedule, LOOKAHEAD_MS);
      play.textContent = '■ STOP';
      play.classList.add('live');
    }
    function stop() {
      if (timer) clearInterval(timer);
      timer = null; playing = false;
      play.textContent = '▶ START';
      play.classList.remove('live');
      beatEls.forEach(function (b) { b.classList.remove('on'); });
    }
    function preview() {
      if (!ensureAudio()) return;
      voice(ctx.currentTime + 0.03, 2);
    }

    /* ── input ───────────────────────────────────────────────────────── */
    play.addEventListener('click', function () { playing ? stop() : start(); });
    minus.addEventListener('click', function () { setBpm(st.bpm - 1); });
    plus.addEventListener('click', function () { setBpm(st.bpm + 1); });
    slider.addEventListener('input', function () { setBpm(Number(slider.value)); });
    tsMinus.addEventListener('click', function () { setMeter(st.top - 1, st.bottom); });
    tsPlus.addEventListener('click', function () { setMeter(st.top + 1, st.bottom); });
    vol.addEventListener('input', function () {
      st.vol = Number(vol.value) / 100;
      if (master) master.gain.value = st.vol;
      persist();
    });

    var taps = [];
    function doTap(stamp) {
      var now = stamp || performance.now();
      if (taps.length && now - taps[taps.length - 1] > 2500) taps = [];
      taps.push(now);
      if (taps.length > 6) taps.shift();
      if (taps.length >= 2) {
        var sum = 0;
        for (var i = 1; i < taps.length; i++) sum += taps[i] - taps[i - 1];
        setBpm(60000 / (sum / (taps.length - 1)));
      }
      tap.classList.add('hit');
      setTimeout(function () { tap.classList.remove('hit'); }, 90);
    }
    /* score the tap when the finger lands, not when it lifts */
    tap.addEventListener('pointerdown', function (e) { e.preventDefault(); doTap(e.timeStamp); });
    tap.addEventListener('click', function (e) { if (e.detail === 0) doTap(); });

    box.addEventListener('keydown', function (e) { handleKey(e); });
    if (!compact) {
      document.addEventListener('keydown', function (e) {
        var t = e.target;
        if (box.contains(t)) return;
        if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT|BUTTON|A)$/.test(t.tagName))) return;
        handleKey(e);
      });
    }
    function handleKey(e) {
      if (e.altKey || e.ctrlKey || e.metaKey) return;
      var t = e.target;
      if (t && t.tagName === 'INPUT' && t.type === 'range' && /Arrow/.test(e.key)) return;
      if (e.key === ' ' || e.key === 'Spacebar') {
        if (t && t.tagName === 'BUTTON' && box.contains(t)) return; /* the button's own click handles it */
        e.preventDefault(); playing ? stop() : start();
      } else if (e.key === 'ArrowUp' || e.key === 'ArrowRight') { e.preventDefault(); setBpm(st.bpm + (e.shiftKey ? 10 : 1)); }
      else if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') { e.preventDefault(); setBpm(st.bpm - (e.shiftKey ? 10 : 1)); }
      else if (e.key === 't' || e.key === 'T') { doTap(e.timeStamp); }
    }

    refresh();
    root.metronome = { start: start, stop: stop, setBpm: setBpm, setMeter: setMeter, isPlaying: function () { return playing; } };
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-metronome]'), mount);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
  window.MetroMetronome = { mount: mount, tempoLabel: tempoLabel, beatGrouping: beatGrouping };
})();
