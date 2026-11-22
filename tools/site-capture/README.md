# Tool captures for landing/tools/img

`capture.mjs` drives headless Chrome over the DevTools protocol, plays each tool
like a decent player (it watches what the page schedules on its AudioContext and
taps or answers accordingly), and screenshots the whole tool panel in a finished
state at 2x, minus Metro's portrait and the hint line. Nothing is cropped
mid-element.

1. Serve the site: `python -m http.server 8765` from `landing/`.
2. `node tools/site-capture/capture.mjs <outDir> [tool ...]` (Node 22+, Chrome installed).
3. `python tools/site-capture/sheet.py` makes a contact sheet to eyeball them.
4. Convert to 720 px wide WebP into `landing/tools/img/` and update the
   width/height attributes in `landing/tools/index.html`.

Re-capture a tool whenever its UI changes visibly.
