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

  /* ── house fallback: if Google has nothing to show, the reserved space
     holds a link to another free tool instead of an empty box ───────── */
  var HOUSE = [
    { href: '/tools/rhythm-timing-test.html', label: 'Rhythm Test', line: 'How good is your timing? Find out in 20 seconds.' },
    { href: '/tools/pitch-ear-test.html', label: 'Pitch Test', line: 'How fine is your ear? Find the smallest gap you can hear.' },
    { href: '/tools/steady-tempo-test.html', label: 'Steady Tempo Test', line: 'Can you hold a tempo when the click stops?' },
    { href: '/tools/tuning-drone.html', label: 'Tuning Drone', line: 'A steady reference note for playing in tune by ear.' },
    { href: '/tools/chord-finder.html', label: 'Chord Finder', line: 'Tap in notes on a guitar or piano and get the chord name.' }
  ];
  var here = window.location.pathname;
  var houseChoices = HOUSE.filter(function (h) { return h.href !== here; });

  slots.forEach(function (slot, i) {
    var house = houseChoices[i % houseChoices.length];
    slot.innerHTML =
      '<span class="ad-label"><span class="al-ad">Advertisement</span><span class="al-house">More free tools</span></span>' +
      '<a class="ad-house" href="' + house.href + '">' +
        '<span class="ad-house-kicker">Free tool</span>' +
        '<span class="ad-house-title">' + house.label + '</span>' +
        '<span class="ad-house-line">' + house.line + '</span>' +
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
