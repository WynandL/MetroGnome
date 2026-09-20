#!/usr/bin/env python3
"""
Generate MetroGnome launcher icons and Play Store assets.

Renders from ic_launcher_foreground.xml + ic_launcher_background.xml using
pycairo. Every path, gradient, alpha, clip and rotation matches the XML
exactly. The feature graphic is exported from its approved campaign artwork master.

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
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

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


# Read the actual vector resources so adaptive and raster icons share one drawing.
ANDROID = '{http://schemas.android.com/apk/res/android}'
AAPT = '{http://schemas.android.com/aapt}'


def _draw_vector(ctx, filename):
    root = ET.parse(Path(BASE) / 'drawable' / filename).getroot()

    def attr(node, key, default=None):
        return node.get(ANDROID + key, default)

    def number(node, key, default=0):
        return float(attr(node, key, default))

    def visit(node):
        if node.tag in ('vector', 'group'):
            ctx.save()
            px, py = number(node, 'pivotX'), number(node, 'pivotY')
            ctx.translate(number(node, 'translateX') + px, number(node, 'translateY') + py)
            ctx.rotate(math.radians(number(node, 'rotation')))
            ctx.scale(number(node, 'scaleX', 1), number(node, 'scaleY', 1))
            ctx.translate(-px, -py)
            for child in node:
                visit(child)
            ctx.restore()
        elif node.tag == 'clip-path':
            P(ctx, attr(node, 'pathData'))
            ctx.clip()
        elif node.tag == 'path':
            data = attr(node, 'pathData')
            P(ctx, data)
            gradient = node.find(AAPT + 'attr/gradient')
            if gradient is not None:
                if attr(gradient, 'type', 'linear') == 'radial':
                    x, y = number(gradient, 'centerX'), number(gradient, 'centerY')
                    paint = cairo.RadialGradient(x, y, 0, x, y, number(gradient, 'gradientRadius'))
                else:
                    paint = cairo.LinearGradient(number(gradient, 'startX'), number(gradient, 'startY'),
                                                 number(gradient, 'endX'), number(gradient, 'endY'))
                for stop in gradient:
                    paint.add_color_stop_rgba(number(stop, 'offset'),
                                              *hc(attr(stop, 'color'), number(node, 'fillAlpha', 1)))
                ctx.set_source(paint)
                ctx.fill()
            else:
                F(ctx, attr(node, 'fillColor', '#00000000'), number(node, 'fillAlpha', 1))
            if attr(node, 'strokeColor'):
                P(ctx, data)
                ctx.set_source_rgba(*hc(attr(node, 'strokeColor'), number(node, 'strokeAlpha', 1)))
                ctx.set_line_width(number(node, 'strokeWidth'))
                ctx.set_line_cap({'round': cairo.LINE_CAP_ROUND, 'square': cairo.LINE_CAP_SQUARE}.get(
                    attr(node, 'strokeLineCap'), cairo.LINE_CAP_BUTT))
                ctx.stroke()

    visit(root)


def _draw_foreground(ctx):
    _draw_vector(ctx, 'ic_launcher_foreground.xml')


# ── Icon renderer ─────────────────────────────────────────────────────────────

def draw_icon(size: int) -> cairo.ImageSurface:
    """Render the full launcher icon (background + foreground) at size×size px."""
    scale = size / 108.0
    surf  = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx   = cairo.Context(surf)
    ctx.scale(scale, scale)
    ctx.set_antialias(cairo.ANTIALIAS_BEST)
    _draw_vector(ctx, 'ic_launcher_background.xml')
    _draw_foreground(ctx)
    return surf


# ── Feature graphic renderer ──────────────────────────────────────────────────

def draw_feature_graphic() -> Image.Image:
    """Export the campaign master at Google Play's required 1024×500 size."""
    source = STORE / 'artwork' / 'feature_graphic_master.png'
    with Image.open(source) as master:
        return master.convert('RGB').resize((1024, 500), Image.Resampling.LANCZOS)


# ── cairo → PIL helper ────────────────────────────────────────────────────────

def to_pil_rgb(surf: cairo.ImageSurface) -> Image.Image:
    """Convert a cairo ARGB32 surface to a PIL RGB image."""
    w, h = surf.get_width(), surf.get_height()
    img  = Image.frombuffer('RGBA', (w, h), bytes(surf.get_data()), 'raw', 'BGRA', 0, 1)
    return img.convert('RGB')


# ── Output paths & sizes ──────────────────────────────────────────────────────

ROOT = Path(__file__).resolve().parent
BASE = ROOT / 'app' / 'src' / 'main' / 'res'
STORE = ROOT / 'app'

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
        surf = draw_icon(px * 4)
        img  = to_pil_rgb(surf).resize((px, px), Image.Resampling.LANCZOS)
        for name in ('ic_launcher.webp', 'ic_launcher_round.webp'):
            img.save(os.path.join(BASE, folder, name), 'WEBP', lossless=True)
        print(f"  {folder}  {px}×{px}px")

    print("\nDone.")
