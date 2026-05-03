#!/usr/bin/env python3
"""
Generate MetroGnome launcher icons and Play Store assets.

Renders from ic_launcher_foreground.xml + ic_launcher_background.xml using
pycairo. Every path, gradient, alpha, clip and rotation matches the XML
exactly. Text for the feature graphic uses Pillow + Segoe UI.

Outputs
-------
  app/store_icon_512.png
  app/feature_graphic_1024x500.png
  res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp
  res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp

Requirements:  pip install pycairo Pillow
"""

import cairo
import math
import os
import random
import re
from PIL import Image, ImageDraw, ImageFont

# ── Colour helpers ─────────────────────────────────────────────────────────────

def hc(h, fa=1.0):
    """
    Android colour hex (#RRGGBB or #AARRGGBB) → (r, g, b, a) in 0-1 range.
    fa (fillAlpha) is multiplied into the parsed alpha.
    """
    h = h.lstrip('#')
    if len(h) == 8:
        a = int(h[0:2], 16) / 255.0
        r = int(h[2:4], 16) / 255.0
        g = int(h[4:6], 16) / 255.0
        b = int(h[6:8], 16) / 255.0
    elif len(h) == 6:
        r = int(h[0:2], 16) / 255.0
        g = int(h[2:4], 16) / 255.0
        b = int(h[4:6], 16) / 255.0
        a = 1.0
    else:
        raise ValueError(f"Unknown colour: #{h}")
    return (r, g, b, a * fa)


# ── SVG arc → cairo arc (standard SVG spec conversion) ────────────────────────

def _arc(ctx, x1, y1, rx, ry, x_rot, large_arc, sweep, x2, y2):
    """Append a single SVG elliptic-arc segment to the current cairo path."""
    if x1 == x2 and y1 == y2:
        return
    if rx == 0 or ry == 0:
        ctx.line_to(x2, y2)
        return
    phi = math.radians(x_rot)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p =  cp * dx + sp * dy
    y1p = -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        s = math.sqrt(lam); rx *= s; ry *= s
    num = max(0.0, (rx * ry) ** 2 - (rx * y1p) ** 2 - (ry * x1p) ** 2)
    den = (rx * y1p) ** 2 + (ry * x1p) ** 2
    sq  = (math.sqrt(num / den) if den else 0.0)
    if large_arc == sweep:
        sq = -sq
    cxp =  sq * rx * y1p / ry
    cyp = -sq * ry * x1p / rx
    ccx = cp * cxp - sp * cyp + (x1 + x2) / 2.0
    ccy = sp * cxp + cp * cyp + (y1 + y2) / 2.0

    def ang(ux, uy, vx, vy):
        n = math.sqrt(ux * ux + uy * uy) * math.sqrt(vx * vx + vy * vy)
        if n == 0:
            return 0.0
        c = max(-1.0, min(1.0, (ux * vx + uy * vy) / n))
        a = math.acos(c)
        return -a if (ux * vy - uy * vx) < 0 else a

    th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry,
              (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if sweep == 0 and dth > 0:
        dth -= 2 * math.pi
    if sweep == 1 and dth < 0:
        dth += 2 * math.pi

    ctx.save()
    ctx.translate(ccx, ccy)
    ctx.rotate(phi)
    ctx.scale(rx, ry)
    if dth >= 0:
        ctx.arc(0, 0, 1, th1, th1 + dth)
    else:
        ctx.arc_negative(0, 0, 1, th1, th1 + dth)
    ctx.restore()


# ── SVG path parser ────────────────────────────────────────────────────────────

_TOK = re.compile(
    r'[MmLlHhVvCcQqAaZz]'
    r'|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?'
)

def P(ctx, d):
    """Parse SVG path data string and append it to the cairo context."""
    tokens = _TOK.findall(d)
    i = 0
    cx = cy = sx = sy = 0.0

    def n(j): return float(tokens[j])

    while i < len(tokens):
        cmd = tokens[i]; i += 1

        if cmd in ('M', 'm'):
            x, y = n(i), n(i + 1); i += 2
            if cmd == 'm': x += cx; y += cy
            ctx.move_to(x, y)
            cx, cy = x, y; sx, sy = x, y
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                x, y = n(i), n(i + 1); i += 2
                if cmd == 'm': x += cx; y += cy
                ctx.line_to(x, y)
                cx, cy = x, y

        elif cmd in ('L', 'l'):
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                x, y = n(i), n(i + 1); i += 2
                if cmd == 'l': x += cx; y += cy
                ctx.line_to(x, y); cx, cy = x, y

        elif cmd in ('H', 'h'):
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                x = n(i); i += 1
                if cmd == 'h': x += cx
                ctx.line_to(x, cy); cx = x

        elif cmd in ('V', 'v'):
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                y = n(i); i += 1
                if cmd == 'v': y += cy
                ctx.line_to(cx, y); cy = y

        elif cmd in ('C', 'c'):
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                x1, y1 = n(i), n(i+1)
                x2, y2 = n(i+2), n(i+3)
                x,  y  = n(i+4), n(i+5); i += 6
                if cmd == 'c':
                    x1+=cx; y1+=cy; x2+=cx; y2+=cy; x+=cx; y+=cy
                ctx.curve_to(x1, y1, x2, y2, x, y); cx, cy = x, y

        elif cmd in ('Q', 'q'):
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                qx, qy = n(i), n(i+1); x, y = n(i+2), n(i+3); i += 4
                if cmd == 'q': qx+=cx; qy+=cy; x+=cx; y+=cy
                ctx.curve_to(
                    cx + 2/3*(qx-cx), cy + 2/3*(qy-cy),
                    x  + 2/3*(qx-x),  y  + 2/3*(qy-y), x, y)
                cx, cy = x, y

        elif cmd in ('A', 'a'):
            while i < len(tokens) and tokens[i] not in 'MmLlHhVvCcQqAaZz':
                rx, ry, xr = n(i), n(i+1), n(i+2)
                la, sw      = int(n(i+3)), int(n(i+4))
                x,  y       = n(i+5), n(i+6); i += 7
                if cmd == 'a': x += cx; y += cy
                _arc(ctx, cx, cy, rx, ry, xr, la, sw, x, y)
                cx, cy = x, y

        elif cmd in ('Z', 'z'):
            ctx.close_path(); cx, cy = sx, sy


# ── Fill / stroke shortcuts ───────────────────────────────────────────────────

def F(ctx, colour, fa=1.0):
    ctx.set_source_rgba(*hc(colour, fa)); ctx.fill()

def FR(ctx, cx, cy, r, stops):
    pat = cairo.RadialGradient(cx, cy, 0, cx, cy, r)
    for off, col in stops:
        pat.add_color_stop_rgba(off, *hc(col))
    ctx.set_source(pat); ctx.fill()

def FL(ctx, x1, y1, x2, y2, stops):
    pat = cairo.LinearGradient(x1, y1, x2, y2)
    for off, col in stops:
        pat.add_color_stop_rgba(off, *hc(col))
    ctx.set_source(pat); ctx.fill()

def S(ctx, pd, colour, w, cap=cairo.LINE_CAP_BUTT):
    P(ctx, pd)
    ctx.set_source_rgba(*hc(colour))
    ctx.set_line_width(w); ctx.set_line_cap(cap); ctx.stroke()


# ── Shared path constants ─────────────────────────────────────────────────────

_BRIM = ("M 72.9,62.66 A 18.9,2.61,0,1,1,35.1,62.66"
         " A 18.9,2.61,0,1,1,72.9,62.66 Z")

_LENSES = (
    "M 44.55,68.51 H 50.85 Q 52.65,68.51 52.65,70.31"
    " V 72.29 Q 52.65,74.09 50.85,74.09"
    " H 44.55 Q 42.75,74.09 42.75,72.29"
    " V 70.31 Q 42.75,68.51 44.55,68.51 Z"
    " M 57.15,68.51 H 63.45 Q 65.25,68.51 65.25,70.31"
    " V 72.29 Q 65.25,74.09 63.45,74.09"
    " H 57.15 Q 55.35,74.09 55.35,72.29"
    " V 70.31 Q 55.35,68.51 57.15,68.51 Z"
)


# ── Foreground renderer (shared by icon and feature graphic) ──────────────────

def _draw_foreground(ctx):
    """
    Draw Metro's face into ctx.
    ctx must already be scaled so 108 units = the desired output size.
    Mirrors ic_launcher_foreground.xml exactly.
    """
    ctx.save()
    ctx.translate(0, -7)   # <group android:translateY="-7">

    # NECK
    P(ctx, "M 51.93,87.5 H 56.07"
           " Q 57.42,87.5 57.42,88.85 V 93.17"
           " Q 57.42,94.52 56.07,94.52 H 51.93"
           " Q 50.58,94.52 50.58,93.17 V 88.85"
           " Q 50.58,87.5 51.93,87.5 Z")
    F(ctx, "#F0BC80")

    # EARS
    P(ctx, "M 39.96,70.58 C 37.35,69.95 32.4,68.6 31.14,69.86"
           " C 32.4,71.48 36.45,78.32 39.96,79.22 Z")
    F(ctx, "#F0BC80")
    P(ctx, "M 38.7,73.28 C 36.9,73.1 33.48,70.94 32.58,71.48"
           " C 33.66,72.92 36.72,76.7 38.7,76.52 Z")
    F(ctx, "#D8A060", 0.9)

    P(ctx, "M 68.04,70.58 C 70.65,69.95 75.6,68.6 76.86,69.86"
           " C 75.6,71.48 71.55,78.32 68.04,79.22 Z")
    F(ctx, "#F0BC80")
    P(ctx, "M 69.3,73.28 C 71.1,73.1 74.52,70.94 75.42,71.48"
           " C 74.34,72.92 71.28,76.7 69.3,76.52 Z")
    F(ctx, "#D8A060", 0.9)

    # HEAD
    P(ctx, "M 70.65,74 A 16.65,16.65,0,1,1,37.35,74"
           " A 16.65,16.65,0,1,1,70.65,74 Z")
    FR(ctx, 49.84, 69.84, 21.65,
        [(0.0, "#FAD09A"), (0.5, "#F0BC80"), (1.0, "#D8A060")])

    # CHEEK BLUSH
    P(ctx, "M 48.87,78.05 A 4.32,4.32,0,1,1,40.23,78.05"
           " A 4.32,4.32,0,1,1,48.87,78.05 Z")
    F(ctx, "#EBA080", 0.16)
    P(ctx, "M 67.77,78.05 A 4.32,4.32,0,1,1,59.13,78.05"
           " A 4.32,4.32,0,1,1,67.77,78.05 Z")
    F(ctx, "#EBA080", 0.16)

    # HAIR
    P(ctx, "M 41.22,60.32 C 38.88,63.65 35.28,67.52 34.92,73.1"
           " C 36.36,74 39.15,73.28 40.32,70.58"
           " C 40.86,66.08 42.48,62.48 43.38,60.68 Z")
    F(ctx, "#B5B0AB")
    P(ctx, "M 66.78,60.32 C 69.12,63.65 72.72,67.52 73.08,73.1"
           " C 71.64,74 68.85,73.28 67.68,70.58"
           " C 67.14,66.08 65.52,62.48 64.62,60.68 Z")
    F(ctx, "#B5B0AB")
    P(ctx, "M 48.6,58.88 C 54,57.62 60.48,59.15 63.45,60.95"
           " C 61.38,61.4 55.08,59.96 49.95,59.78 Z")
    F(ctx, "#888280")

    # NOSE
    P(ctx, "M 57.96,79.76 A 3.96,3.24,0,1,1,50.04,79.76"
           " A 3.96,3.24,0,1,1,57.96,79.76 Z")
    FR(ctx, 53.28, 79.22, 5.0, [(0.0, "#FAD09A"), (1.0, "#CC8868")])

    # NOSTRILS
    P(ctx, "M 53.1,80.39 A 0.9,0.81,0,1,1,51.3,80.39"
           " A 0.9,0.81,0,1,1,53.1,80.39 Z"
           " M 56.7,80.39 A 0.9,0.81,0,1,1,54.9,80.39"
           " A 0.9,0.81,0,1,1,56.7,80.39 Z")
    F(ctx, "#D8A060", 0.35)

    # MUSTACHE
    P(ctx, "M 53.28,81.92 C 49.95,80.84 41.85,81.2 39.78,85.7"
           " C 40.95,87.5 46.98,86.87 50.85,85.88"
           " C 52.92,85.16 54,84.17 53.28,81.92 Z")
    F(ctx, "#F2EEEA")
    P(ctx, "M 54.72,81.92 C 58.05,80.84 66.15,81.2 68.22,85.7"
           " C 67.05,87.5 61.02,86.87 57.15,85.88"
           " C 55.08,85.16 54,84.17 54.72,81.92 Z")
    F(ctx, "#F2EEEA")

    # SUNGLASSES – dark lenses
    P(ctx, _LENSES); F(ctx, "#080818")
    # SUNGLASSES – gold frame
    P(ctx, _LENSES)
    ctx.set_source_rgba(*hc("#FFD700"))
    ctx.set_line_width(0.9); ctx.set_line_cap(cairo.LINE_CAP_BUTT); ctx.stroke()
    # Bridge and arms
    S(ctx,
      "M 52.65,71.3 L 55.35,71.3"
      " M 42.75,71.3 L 37.62,72.2"
      " M 65.25,71.3 L 70.38,72.2",
      "#FFD700", 0.72, cairo.LINE_CAP_ROUND)
    # Lens reflections (#664466AA = AARRGGBB, alpha≈0.4)
    S(ctx,
      "M 44.73,69.91 L 47.21,72.14"
      " M 57.33,69.91 L 59.81,72.14",
      "#664466AA", 0.99, cairo.LINE_CAP_ROUND)

    # EYEBROWS
    S(ctx, "M 39.78,66.8 C 44.28,64.55 48.78,65 52.2,67.07",
      "#B8B0A8", 1.89, cairo.LINE_CAP_ROUND)
    S(ctx, "M 68.22,66.8 C 63.72,64.55 59.22,65 55.8,67.07",
      "#B8B0A8", 1.89, cairo.LINE_CAP_ROUND)

    # HAT GROUP (rotation=11° around pivot 54, 64.1)
    ctx.save()
    ctx.translate(54, 64.1)
    ctx.rotate(math.radians(11))
    ctx.translate(-54, -64.1)

    # Brim upper half — behind hat body
    ctx.save()
    ctx.rectangle(0, 0, 108, 64.1); ctx.clip()
    P(ctx, _BRIM); F(ctx, "#881010")
    ctx.restore()

    # Hat body
    P(ctx, "M 38.25,64.1 C 40.95,46.1 52.02,20 54,18.2"
           " C 55.98,20 67.05,46.1 69.75,64.1 Z")
    FL(ctx, 54, 18.2, 54, 64.1, [
        (0.0, "#DD3535"), (0.5, "#CC1818"), (1.0, "#881010")])

    # Brim lower half — in front of hat body
    ctx.save()
    ctx.rectangle(0, 64.1, 108, 43.9); ctx.clip()
    P(ctx, _BRIM); F(ctx, "#881010")
    ctx.restore()

    ctx.restore()  # end hat group

    # SHIRT COLLAR
    P(ctx, "M 49.32,96.32 L 54,101.45 L 58.68,96.32"
           " L 56.88,91.28 L 54,93.08 L 51.12,91.28 Z")
    F(ctx, "#F8F4EE")
    # Tie triangles
    P(ctx, "M 53.55,93.62 L 49.5,91.82 L 49.5,95.42 Z"
           " M 54.45,93.62 L 58.5,91.82 L 58.5,95.42 Z")
    F(ctx, "#AA1E2E")
    # Tie knot
    P(ctx, "M 55.08,93.62 A 1.08,1.08,0,1,1,52.92,93.62"
           " A 1.08,1.08,0,1,1,55.08,93.62 Z")
    F(ctx, "#AA1E2E")

    ctx.restore()  # end outer group translateY=-7


# ── Icon renderer ─────────────────────────────────────────────────────────────

def draw_icon(size: int) -> cairo.ImageSurface:
    """Render the full launcher icon (background + foreground) at size×size px."""
    scale = size / 108.0
    surf  = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx   = cairo.Context(surf)
    ctx.scale(scale, scale)
    ctx.set_antialias(cairo.ANTIALIAS_BEST)
    ctx.rectangle(0, 0, 108, 108)
    FL(ctx, 54, 0, 54, 108, [(0, "#0A0818"), (1, "#16133A")])
    _draw_foreground(ctx)
    return surf


# ── Feature graphic renderer ──────────────────────────────────────────────────

def _load_font(filename, size):
    for path in [f"C:/Windows/Fonts/{filename}", f"C:/Windows/Fonts/arialbd.ttf"]:
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            pass
    return ImageFont.load_default()


def draw_feature_graphic() -> Image.Image:
    """Render the 1024×500 Play Store feature graphic."""
    W, H = 1024, 500

    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, W, H)
    ctx  = cairo.Context(surf)
    ctx.set_antialias(cairo.ANTIALIAS_BEST)

    # Background — same gradient direction as the app
    ctx.rectangle(0, 0, W, H)
    pat = cairo.LinearGradient(0, 0, W, H)
    pat.add_color_stop_rgba(0.0, *hc("#0A0818"))
    pat.add_color_stop_rgba(1.0, *hc("#16133A"))
    ctx.set_source(pat); ctx.fill()

    # Stars — same seed as the launcher icon background for consistency
    rng = random.Random(1337)
    for _ in range(80):
        fx, fy = rng.random(), rng.random()
        sx = fx * W
        sy = fy * H * 0.78          # keep stars in upper 78% of height
        sr = max(0.8, (1.0 + fx * 1.5) * (W / 512.0))
        b  = 0.2 + fy * 0.45
        ctx.arc(sx, sy, sr, 0, 2 * math.pi)
        ctx.set_source_rgba(b, b, b + 0.05, 1.0)
        ctx.fill()

    # Gnome face — right side, viewport 390×390 px starting at (574, 48)
    # Face centre lands at x≈774, y≈295; hat tip at y≈89; collar at y≈398
    GNOME_PX = 390
    GX, GY   = 574, 48
    ctx.save()
    ctx.translate(GX, GY)
    ctx.scale(GNOME_PX / 108.0, GNOME_PX / 108.0)
    _draw_foreground(ctx)
    ctx.restore()

    # Hand off to Pillow for text (better font rendering on Windows)
    img  = Image.frombuffer(
        'RGBA', (W, H), bytes(surf.get_data()), 'raw', 'BGRA', 0, 1).copy()
    draw = ImageDraw.Draw(img)

    font_title = _load_font("segoeuib.ttf", 88)
    font_tag   = _load_font("segoeui.ttf",  26)

    TX = 68   # left text margin

    def shadowed(x, y, text, fill):
        draw.text((x + 2, y + 2), text, fill=(0, 0, 0, 130), font=font_title)
        draw.text((x,     y    ), text, fill=fill,             font=font_title)

    # "Metro" white, "Gnome" gold — stacked, vertically centred on canvas
    shadowed(TX, 148, "Metro", (255, 255, 255, 255))
    shadowed(TX, 255, "Gnome", (255, 215,   0, 255))

    # Gold accent line
    draw.line([(TX, 368), (TX + 330, 368)], fill=(255, 215, 0, 180), width=2)

    # Tagline
    draw.text((TX + 2, 381), "Beat Detection · Rhythm Game · Metronome",
              fill=(0,   0,   0,   110), font=font_tag)
    draw.text((TX,     379), "Beat Detection · Rhythm Game · Metronome",
              fill=(165, 160, 200, 255), font=font_tag)

    # Four beat dots — a nod to the metronome's 4/4 pulse
    DOT_Y, DOT_R = 455, 7
    for i in range(4):
        bx = TX + i * 30
        draw.ellipse([bx-DOT_R, DOT_Y-DOT_R, bx+DOT_R, DOT_Y+DOT_R],
                     fill=(255, 215, 0, 200))

    return img.convert('RGB')


# ── cairo → PIL helper ────────────────────────────────────────────────────────

def to_pil_rgb(surf: cairo.ImageSurface) -> Image.Image:
    """Convert a cairo ARGB32 surface to a PIL RGB image."""
    w, h = surf.get_width(), surf.get_height()
    img  = Image.frombuffer('RGBA', (w, h), bytes(surf.get_data()), 'raw', 'BGRA', 0, 1)
    return img.convert('RGB')


# ── Output paths & sizes ──────────────────────────────────────────────────────

BASE  = r"C:\Users\wlambrechts\AndroidStudioProjects\MetroGnome\app\src\main\res"
STORE = r"C:\Users\wlambrechts\AndroidStudioProjects\MetroGnome\app"

DENSITIES = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi':  144,
    'mipmap-xxxhdpi': 192,
}

if __name__ == '__main__':
    print("Rendering assets from XML using pycairo…\n")

    # Play Store icon (512×512)
    surf = draw_icon(512)
    out  = os.path.join(STORE, 'store_icon_512.png')
    to_pil_rgb(surf).save(out, 'PNG')
    print(f"  {out}  (512×512)")

    # Play Store feature graphic (1024×500)
    fg  = draw_feature_graphic()
    out = os.path.join(STORE, 'feature_graphic_1024x500.png')
    fg.save(out, 'PNG')
    print(f"  {out}  (1024×500)")

    # Launcher icons
    print()
    for folder, px in DENSITIES.items():
        surf = draw_icon(px)
        img  = to_pil_rgb(surf)
        for name in ('ic_launcher.webp', 'ic_launcher_round.webp'):
            img.save(os.path.join(BASE, folder, name), 'WEBP', quality=95)
        print(f"  {folder}  {px}×{px}px")

    print("\nDone.")
