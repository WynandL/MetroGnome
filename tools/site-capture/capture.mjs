// Captures every tool panel in a representative, finished state, whole and uncropped.
// Usage: node capture.mjs <outDir> [name ...]
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.resolve(process.argv[2] || 'out');
const ONLY = process.argv.slice(3);
const BASE = 'http://localhost:8765';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 9333;
const sleep = ms => new Promise(r => setTimeout(r, ms));

// Injected before any page script: watches what the page schedules and plays like a decent player.
const SIM = `
(() => {
  const S = window.__sim = { sources: [], freqs: [], mode: null, taps: 0 };
  const P = AudioParam.prototype;
  const d = Object.getOwnPropertyDescriptor(P, 'value');
  Object.defineProperty(P, 'value', { get: d.get, set(v) { if (this.__isFreq) S.freqs.push({ v, at: performance.now() }); return d.set.call(this, v); }, configurable: true });
  const sv = P.setValueAtTime;
  P.setValueAtTime = function (v, t) { if (this.__isFreq) S.freqs.push({ v, at: performance.now() }); return sv.call(this, v, t); };
  const co = BaseAudioContext.prototype.createOscillator;
  BaseAudioContext.prototype.createOscillator = function () { const o = co.call(this); o.frequency.__isFreq = true; o.__ctx = this; return o; };
  const cb = BaseAudioContext.prototype.createBufferSource;
  BaseAudioContext.prototype.createBufferSource = function () { const o = cb.call(this); o.__ctx = this; return o; };
  const st = AudioScheduledSourceNode.prototype.start;
  AudioScheduledSourceNode.prototype.start = function (when) {
    const ctx = this.__ctx || this.context;
    const w = when || ctx.currentTime;
    const f = this.frequency ? this.frequency.value : 0;
    const perf = performance.now() + (w - ctx.currentTime) * 1000;
    if (!S.inPress) { S.sources.push({ perf, f }); if (S.onSource) S.onSource(perf, f); }
    return st.apply(this, arguments);
  };
  const gauss = () => { let u = 0, v = 0; while (!u) u = Math.random(); while (!v) v = Math.random(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); };
  S.press = (el) => { S.inPress = true; try { el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerType: 'touch', isPrimary: true })); } finally { S.inPress = false; } S.taps++; };
  S.tapAt = (el, perf, sd) => { const t = perf + gauss() * sd; setTimeout(() => S.press(el), Math.max(0, t - performance.now())); };
  // follow every distinct scheduled sound, and keep the beat going after the click stops
  S.follow = (el, sd, keepGoingFor) => {
    let last = -1e9, periods = [], timer = null;
    S.onSource = (perf) => {
      if (perf - last < 40) return;
      if (last > 0) periods.push(perf - last);
      last = perf;
      S.tapAt(el, perf, sd);
      clearTimeout(timer);
      if (keepGoingFor && periods.length >= 3) {
        const per = periods.slice(-4).reduce((a, b) => a + b, 0) / Math.min(4, periods.length);
        const from = last;
        timer = setTimeout(() => {           /* the click has stopped: keep the same beat */
          for (let k = 1; k <= keepGoingFor; k++) S.tapAt(el, from + k * per * (1 + 0.002 * k / keepGoingFor), sd);
        }, Math.max(0, from + per * 0.6 - performance.now()));
      }
    };
  };
})();
`;

const TOOLS = {
  'metronome': { url: '/tools/online-metronome.html?bpm=96&ts=4-4', panel: '.mm', prep: `document.querySelectorAll('.mm-beat')[0].classList.add('on'); true` },
  'rhythm-test': { url: '/tools/rhythm-timing-test.html', act: `__sim.follow(document.getElementById('tapPad'), 36, 0); document.getElementById('tapPad').click(); true`, wait: 16000 },
  'steady-tempo': { url: '/tools/steady-tempo-test.html', act: `__sim.follow(document.getElementById('tapPad'), 16, 24); document.getElementById('tapPad').click(); true`, wait: 26000 },
  'polyrhythm-test': { url: '/tools/polyrhythm-test.html', act: `
      (() => {
        const slow = document.querySelector('[data-hand="slow"]') || document.getElementById('padSlow');
        const fast = document.querySelector('[data-hand="fast"]') || document.getElementById('padFast');
        let cycleStart = null, offs = { 520: [], 1560: [] }, learned = false, P = 0, ones = [];
        __sim.onSource = (perf, f) => {
                    f = Math.round(f);
          if (f === 2400 || (f === 520 && cycleStart === null)) {}
          if (f === 520 || f === 1560) {
            if (cycleStart === null) cycleStart = perf;
            if (!learned) offs[f].push(perf - cycleStart);
            if (__sim.sources.filter(s => Math.round(s.f) === 520).length > 2) __sim.tapAt(f === 520 ? slow : fast, perf, 9);
          }
          if (f === 2400) {
            learned = true;
            const P0 = Math.max(...offs[520]) + (offs[520][1] - offs[520][0]);
            const cyc = { 520: offs[520].filter(o => o < P0 - 5), 1560: offs[1560].filter(o => o < P0 - 5) };
            if (ones.length === 0) { /* first lone tick: play the cycle from here */ }
            ones.push(perf);
            cyc[520].forEach(o => __sim.tapAt(slow, perf + o, 10));
            cyc[1560].forEach(o => __sim.tapAt(fast, perf + o, 12));
          }
        };
        document.getElementById('startBtn').click();
        return !!slow + ':' + !!fast;
      })()`, wait: 30000 },
  'rushing-dragging': { url: '/tools/rushing-or-dragging-test.html', act: `
      (() => {
        const iv = setInterval(() => {
          const r = document.getElementById('rushBtn'), d = document.getElementById('dragBtn');
          if (!r || r.disabled || r.offsetParent === null || !window.__rdTrial) return;
          if (window.__lastTrial === window.__rdTrial) return;
          window.__lastTrial = window.__rdTrial;
          const right = Math.random() < 0.82;
          const sayRush = right ? window.__rdTrial.rushing : !window.__rdTrial.rushing;
          setTimeout(() => (sayRush ? r : d).click(), 300);
        }, 150);
        document.getElementById('startPad').click();
        return true;
      })()`, wait: 150000, done: `!!document.querySelector('#againBtn') && !document.getElementById('againBtn').hidden` },
  'pitch-test': { url: '/tools/pitch-ear-test.html', act: `
      (() => {
        let lastLen = 0;
        const iv = setInterval(() => {
          const h = document.getElementById('higherBtn'), l = document.getElementById('lowerBtn');
          if (!h || h.disabled || h.offsetParent === null) return;
          const src = __sim.sources.filter(x => x.f > 40);
          if (src.length === lastLen) return;
          const groups = [];
          src.forEach(x => { const g = groups.find(g => Math.abs(g.t - x.perf) < 5); if (g) g.f = Math.min(g.f, x.f); else groups.push({ t: x.perf, f: x.f }); });
          if (groups.length < 2) return;
          lastLen = src.length;
          const a = groups[groups.length - 2].f, b = groups[groups.length - 1].f;
          const cents = Math.abs(1200 * Math.log2(b / a));
          const pRight = cents > 25 ? 0.97 : cents > 8 ? 0.9 : cents > 4 ? 0.75 : 0.55;
          const up = b > a, right = Math.random() < pRight;
          setTimeout(() => ((up === right) ? h : l).click(), 250);
        }, 120);
        document.getElementById('startPad').click();
        return true;
      })()`, wait: 150000, done: `!document.getElementById('retryBtn').hidden` },
  'speed-trainer': { url: '/tools/speed-trainer.html', act: `(() => { const b = [...document.querySelectorAll('button')].find(x => /start/i.test(x.textContent)); if (b) b.click(); return !!b; })()`, wait: 9000 },
  'chord-finder': { url: '/tools/chord-finder.html?notes=C3,E3,G3,B3', act: `true`, wait: 500 },
  'tuning-drone': { url: '/tools/tuning-drone.html?note=A&octave=3&timbre=warm&blend=root&ref=440', act: `true`, wait: 500 },
  'shruti-box': { vw: 720, url: '/embed/tuning-drone.html?note=Cs&octave=3&timbre=warm&blend=octave', act: `true`, wait: 800 },
  'tap-tempo': { keepHint: true, url: '/tools/tap-tempo-bpm-finder.html', act: `(() => { const p = document.getElementById('tapPad') || document.querySelector('.tap-pad'); let i = 0; const go = () => { p.dispatchEvent(new PointerEvent('pointerdown', {bubbles:true})); p.click(); if (++i < 8) setTimeout(go, 500); }; go(); return true; })()`, wait: 5000 },
  'odd-meter': { url: '/tools/odd-meter-practice-tool.html?ts=7-8&bpm=96', act: `true`, wait: 500 },
};

async function cdp(wsUrl) {
  const ws = new WebSocket(wsUrl);
  await new Promise(r => ws.addEventListener('open', r, { once: true }));
  let id = 0; const pend = new Map(); const listeners = [];
  ws.addEventListener('message', ev => { const m = JSON.parse(ev.data); if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id); } else listeners.forEach(l => l(m)); });
  const send = (method, params = {}) => new Promise(r => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
  return { send, on: f => listeners.push(f), close: () => ws.close() };
}

const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, '--user-data-dir=' + path.resolve(OUT, '..', 'prof'),
  '--autoplay-policy=no-user-gesture-required', '--hide-scrollbars', '--window-size=1100,1400', '--force-device-scale-factor=2', '--mute-audio'], { stdio: ['ignore','ignore','pipe'] }
);
chrome.stderr.on('data', d => { if (/DevTools listening|error/i.test(String(d))) console.error(String(d).trim().slice(0,200)); });
for (let i = 0; i < 40; i++) { try { await fetch(`http://127.0.0.1:${PORT}/json/version`); break; } catch (e) { await sleep(500); } }

try {
  for (const [name, t] of Object.entries(TOOLS)) {
    if (ONLY.length && !ONLY.includes(name)) continue;
    const tgt = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })).json();
    const c = await cdp(tgt.webSocketDebuggerUrl);
    await c.send('Page.enable'); await c.send('Runtime.enable');
    await c.send('Page.addScriptToEvaluateOnNewDocument', { source: SIM + `;try{localStorage.clear()}catch(e){};document.documentElement.classList.add('capture')` });
    await c.send('Emulation.setDeviceMetricsOverride', { width: t.vw || 1100, height: 1400, deviceScaleFactor: 2, mobile: false });
    await c.send('Page.navigate', { url: BASE + t.url });
    await sleep(1800);
    const ev = async (expr) => (await c.send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })).result?.result?.value;
    await ev(`document.documentElement.style.scrollBehavior='auto'; document.querySelectorAll('.ad-slot').forEach(e=>e.remove()); true`);
    if (t.prep) await ev(t.prep);
    if (t.act) console.log(name, 'act ->', await ev(t.act));
    const t0 = Date.now();
    while (Date.now() - t0 < (t.wait || 500)) {
      await sleep(500);
      if (t.done && await ev(t.done)) { await sleep(2500); break; }
    }
    if (!t.done) await sleep(1500);
    if (t.prep) await ev(t.prep);
    // the whole panel, minus Metro's portrait at the top, which every tool shares
    const r = await ev(`(() => {
      const p = document.querySelector('${t.panel || '.tool-panel'}');
      const b = p.getBoundingClientRect();
      const m = p.querySelector('.metro-wrap');
      let top = b.top;
      if (m) { const mb = m.getBoundingClientRect(); top = mb.bottom + 4; }
      let bottom = b.bottom;
      const hints = ${t.keepHint ? '[]' : `[...p.querySelectorAll('.tool-hint')]`}.map(h => h.getBoundingClientRect()).filter(h => h.height > 0 && h.top > b.top + b.height * 0.5);
      if (hints.length) bottom = Math.min(bottom, Math.min(...hints.map(h => h.top)) - 6);
      const kids = [...p.children].filter(k => k.offsetParent !== null && getComputedStyle(k).display !== 'none');
      while (kids.length && kids[kids.length - 1].classList.contains('tool-hint')) kids.pop();
      if (kids.length && !hints.length && !${!!t.keepHint}) bottom = Math.min(b.bottom, kids[kids.length - 1].getBoundingClientRect().bottom + 28);
      return { x: b.left + scrollX, y: top + scrollY, w: b.width, h: bottom - top };
    })()`);
    const shot = await c.send('Page.captureScreenshot', { format: 'png', clip: { x: r.x, y: r.y, width: r.w, height: r.h, scale: 1 }, captureBeyondViewport: true });
    fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(shot.result.data, 'base64'));
    console.log(name, Math.round(r.w) + 'x' + Math.round(r.h), 'taps', await ev('__sim.taps'));
    c.close();
    await fetch(`http://127.0.0.1:${PORT}/json/close/${tgt.id}`);
  }
} finally {
  chrome.kill();
}
