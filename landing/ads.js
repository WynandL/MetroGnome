/* Metro Gnome site ads: hand-placed, fixed-size, never on Android.
 *
 * The rules this file exists to enforce:
 *
 *  1. Ads appear ONLY in <div class="ad-slot" data-ad="..."> elements written
 *     into a page by hand. There is no Auto Ads, so Google never decides where
 *     an ad goes, and nothing can land inside a tool panel or an embed.
 *  2. Android visitors never see an ad. They can install the app, so that
 *     space is theirs; everyone else (iPhone, desktop) cannot, and gets one
 *     calm ad instead. The decision is made by the one-line #ads-gate script
 *     in each page's <head>, before the body paints, so the slot's reserved
 *     height is either there from the first frame or never there at all.
 *     That is what keeps the page from jumping (no layout shift).
 *  3. Nothing here touches the page until it has loaded and gone idle, and
 *     not while a tool says it is busy (MetroAds.busy(true) during a test
 *     run), so the AdSense script never competes with a click scheduler.
 *     Each slot is only filled when it scrolls near the viewport.
 *  4. Never inside a frame: the embed widgets are on other people's sites.
 *
 * Reviewing locally: on localhost every slot shows a labelled placeholder
 * of its real size and AdSense is never loaded. Add ?android=1 to see the
 * page as an Android visitor does, or ?ads=1 on an Android phone to see it
 * as everyone else does.
 */
(function () {
  'use strict';

  /* ── configuration: the only lines that change when going live ────── */
  var CLIENT = 'ca-pub-8485854692249613';   /* AdSense publisher id (confirm) */
  var SLOTS = {                             /* data-ad name -> AdSense ad unit id */
    'tool-below': '8884138789',
    'tool-mid':   '2631564933',
    'blog-mid':   '2474643100',
    'blog-end':   '5066156588'
  };
  var IDLE_DELAY_MS = 1500;                 /* after load, before anything is fetched */
  var NEAR_PX = 600;                        /* fill a slot this far before it is visible */

  var root = document.documentElement;
  var params = window.location.search;
  var local = /^(localhost|127\.0\.0\.1|\[::1\]|)$/.test(window.location.hostname);
  var framed = false;
  try { framed = window.self !== window.top; } catch (e) { framed = true; }

  /* review overrides (only ever change what THIS viewer sees) */
  if (/[?&]android=1/.test(params)) { root.classList.remove('ads-on'); root.classList.add('is-android'); }
  if (/[?&]ads=1/.test(params))     { root.classList.remove('is-android'); root.classList.add('ads-on'); }

  var busyCount = 0;
  var queue = [];                           /* slots waiting for idle / not-busy */
  var libRequested = false;

  window.MetroAds = {
    busy: function (on) {
      busyCount = Math.max(0, busyCount + (on ? 1 : -1));
      if (!busyCount) flush();
    }
  };

  if (framed || !root.classList.contains('ads-on')) return;

  var slots = Array.prototype.slice.call(document.querySelectorAll('.ad-slot[data-ad]'));
  if (!slots.length) return;

  /* ── house card: until (or unless) Google fills the slot, the reserved
     space shows another free tool, with a real screenshot of it in use.
     It fills the whole slot and is designed as a feature in its own
     right, not as an empty box waiting for an ad. `pos` is the vertical
     crop of the 640px square capture (tools/img), chosen per tool so the
     band shows the interesting part: the chart, the fretboard, the keys. */
  var HOUSE = [
    { href: '/tools/rhythm-timing-test.html', img: 'rhythm-test', pos: '94%', tag: 'Timing', label: 'Rhythm Test', line: 'How good is your timing? Find out in 20 seconds.', cta: 'Take the test' },
    { href: '/tools/pitch-ear-test.html', img: 'pitch-test', pos: '88%', tag: 'Ear', label: 'Pitch Test', line: 'How fine is your ear? Find the smallest gap you can hear.', cta: 'Take the test' },
    { href: '/tools/steady-tempo-test.html', img: 'steady-tempo', pos: '94%', tag: 'Timing', label: 'Steady Tempo Test', line: 'Can you hold the tempo when the click stops?', cta: 'Take the test' },
    { href: '/tools/chord-finder.html', img: 'chord-finder', pos: '100%', tag: 'Chords', label: 'Chord Finder', line: 'Tap notes on a guitar or piano and get the chord name.', cta: 'Find a chord' },
    { href: '/tools/tuning-drone.html', img: 'tuning-drone', pos: '42%', tag: 'Intonation', label: 'Tuning Drone', line: 'A steady reference note for playing in tune by ear.', cta: 'Start a drone' },
    { href: '/tools/tap-tempo-bpm-finder.html', img: 'tap-tempo', pos: '26%', tag: 'Tempo', label: 'Tap Tempo', line: 'Tap along to any song and see its BPM.', cta: 'Find the tempo' },
    { href: '/tools/odd-meter-practice-tool.html', img: 'odd-meter', pos: '4%', tag: 'Time signatures', label: 'Odd Meter Metronome', line: 'Hear 7/8, 5/4 and friends accented the way they are felt.', cta: 'Play a meter' }
  ];
  var here = window.location.pathname.replace(/\/$/, '');
  var houseChoices = HOUSE.filter(function (h) { return h.href !== here && h.href.replace(/\.html$/, '') !== here; });
  /* a different pair on every visit, never the same tool twice on a page */
  for (var k = houseChoices.length - 1; k > 0; k--) {
    var j = Math.floor(Math.random() * (k + 1));
    var t = houseChoices[k]; houseChoices[k] = houseChoices[j]; houseChoices[j] = t;
  }

  var ARROW = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="13 5 20 12 13 19"/></svg>';

  slots.forEach(function (slot, i) {
    var house = houseChoices[i % houseChoices.length];
    slot.innerHTML =
      '<span class="ad-label">Advertisement</span>' +
      '<a class="ad-house" href="' + house.href + '">' +
        '<span class="ad-house-shot" style="background-image:url(/tools/img/' + house.img + '.webp);background-position:50% ' + house.pos + '"></span>' +
        '<span class="ad-house-badge">Free tool</span>' +
        '<span class="ad-house-body">' +
          '<span class="ad-house-kicker">' + house.tag + '</span>' +
          '<span class="ad-house-title">' + house.label + '</span>' +
          '<span class="ad-house-line">' + house.line + '</span>' +
          '<span class="ad-house-cta">' + house.cta + ARROW + '</span>' +
        '</span>' +
      '</a>';
  });

  if (local) {
    slots.forEach(function (slot) {
      slot.classList.add('ad-preview');
      var tag = document.createElement('span');
      tag.className = 'ad-preview-tag';
      tag.textContent = 'Ad slot: ' + slot.getAttribute('data-ad');
      slot.appendChild(tag);
    });
    return;
  }

  /* ── live: fill each slot as it nears the viewport, once idle ─────── */
  function fill(slot) {
    var name = slot.getAttribute('data-ad');
    var unit = SLOTS[name];
    if (!unit || /^0+$/.test(unit)) return;   /* not configured yet: house card stays */
    var ins = document.createElement('ins');
    ins.className = 'adsbygoogle metro-ad';
    ins.setAttribute('data-ad-client', CLIENT);
    ins.setAttribute('data-ad-slot', unit);
    slot.appendChild(ins);
    requestLib();
    try { (window.adsbygoogle = window.adsbygoogle || []).push({}); } catch (e) {}
  }

  function requestLib() {
    if (libRequested) return;
    libRequested = true;
    var s = document.createElement('script');
    s.async = true;
    s.crossOrigin = 'anonymous';
    s.src = 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=' + CLIENT;
    document.head.appendChild(s);
  }

  var ready = false;
  function flush() {
    if (!ready || busyCount) return;
    var pending = queue;
    queue = [];
    pending.forEach(fill);
  }

  function whenIdle(fn) {
    if ('requestIdleCallback' in window) window.requestIdleCallback(fn, { timeout: 4000 });
    else setTimeout(fn, 200);
  }

  function arm() {
    setTimeout(function () {
      whenIdle(function () {
        ready = true;
        if ('IntersectionObserver' in window) {
          var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (en) {
              if (!en.isIntersecting) return;
              io.unobserve(en.target);
              queue.push(en.target);
            });
            flush();
          }, { rootMargin: NEAR_PX + 'px 0px' });
          slots.forEach(function (s) { io.observe(s); });
        } else {
          queue = slots.slice();
          flush();
        }
      });
    }, IDLE_DELAY_MS);
  }

  if (document.readyState === 'complete') arm();
  else window.addEventListener('load', arm);
})();
