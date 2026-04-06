#!/usr/bin/env python3
"""
Generate MetroGnome launcher icons from scratch using Pillow.
Faithfully reproduces the GnomeCanvas.kt character:
  - Dark navy star background
  - Red garden gnome hat (11-degree rakish tilt, gold star, dark band)
  - Grey side-parted hair peeking under brim
  - Warm skin face with radial gradient, ears, blush cheeks
  - Gold-frame sunglasses (brand signature)
  - Wide drooping off-white mustache
  - Near-black pinstripe suit, white collar, red bow tie, gold buttons
  - Gold baton held upper-right
"""

from PIL import Image, ImageDraw
import math, random, os

# ── Helpers ───────────────────────────────────────────────────────────────────

def cubic_bezier(p0, p1, p2, p3, n=50):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        pts.append((
            mt**3*p0[0] + 3*mt**2*t*p1[0] + 3*mt*t**2*p2[0] + t**3*p3[0],
            mt**3*p0[1] + 3*mt**2*t*p1[1] + 3*mt*t**2*p2[1] + t**3*p3[1],
        ))
    return pts

def rot(px, py, ox, oy, deg):
    r = math.radians(deg)
    dx, dy = px - ox, py - oy
    return (ox + dx*math.cos(r) - dy*math.sin(r),
            oy + dx*math.sin(r) + dy*math.cos(r))

def rot_list(pts, ox, oy, deg):
    return [rot(px, py, ox, oy, deg) for px, py in pts]

def star_polygon(cx, cy, outer_r, inner_r):
    pts = []
    for i in range(10):
        angle = math.pi/5 * i - math.pi/2
        r = outer_r if i % 2 == 0 else inner_r
        pts.append((cx + r*math.cos(angle), cy + r*math.sin(angle)))
    return pts

# ── Master draw (internal high-res) ──────────────────────────────────────────

def draw_gnome(ds=2048):
    """
    Draw Metro at ds x ds pixels.
    Coordinate system matches GnomeCanvas.kt:
      origin = feet, negative Y = up, 1 unit (u) = ds/18
    We position feet BELOW the canvas so we see from belt-level to hat-tip.
    """
    u   = ds / 15.5          # tuned so body fills the frame nicely
    cx  = ds / 2.0
    baseY = ds * 1.30        # feet Y — below the canvas

    def gx(xu): return cx + xu * u
    def gy(yu): return baseY + yu * u   # yu negative = upward

    # GnomeColors exact hex values from Color.kt
    C = {
        'bgTop':       (0x0A, 0x08, 0x18, 255),
        'bgBot':       (0x16, 0x13, 0x3A, 255),
        'skin':        (0xF0, 0xBC, 0x80, 255),
        'skinHi':      (0xFA, 0xD0, 0x9A, 255),
        'skinDk':      (0xD8, 0xA0, 0x60, 255),
        'beard':       (0xF2, 0xEE, 0xEA, 255),
        'beardShade':  (0xB8, 0xB0, 0xA8, 255),
        'hatRed':      (0xCC, 0x18, 0x18, 255),
        'hatRedLt':    (0xDD, 0x35, 0x35, 255),
        'hatRedDk':    (0x88, 0x10, 0x10, 255),
        'hatBrim':     (0x6A, 0x0A, 0x0A, 255),
        'hatBand':     (0x44, 0x06, 0x06, 255),
        'hatGold':     (0xFF, 0xD7, 0x00, 255),
        'jacket':      (0x11, 0x11, 0x15, 255),
        'jacketLt':    (0x1C, 0x1C, 0x22, 255),
        'jacketDk':    (0x08, 0x08, 0x0C, 255),
        'shirt':       (0xF8, 0xF4, 0xEE, 255),
        'tie':         (0xAA, 0x1E, 0x2E, 255),
        'belt':        (0x0A, 0x0A, 0x0E, 255),
        'buckle':      (0xFF, 0xD7, 0x00, 255),
        'gold':        (0xFF, 0xD7, 0x00, 255),
        'hair':        (0xB5, 0xB0, 0xAB, 255),
        'hairDk':      (0x88, 0x82, 0x80, 255),
        'cheek':       (0xEB, 0xA0, 0x80, 50),
        'nose':        (0xCC, 0x88, 0x68, 255),
        'glassFrame':  (0xFF, 0xD7, 0x00, 255),
        'glassLens':   (0x08, 0x08, 0x18, 255),
        'glassRefl':   (0x44, 0x66, 0xAA, 100),
        'batonGold':   (0xFF, 0xD7, 0x00, 255),
    }

    img  = Image.new('RGBA', (ds, ds), C['bgTop'])
    draw = ImageDraw.Draw(img)

    # ── Background gradient ─────────────────────────────────────────────────
    for y in range(ds):
        t = y / ds
        r = int(0x0A + (0x16 - 0x0A) * t)
        g = int(0x08 + (0x13 - 0x08) * t)
        b = int(0x18 + (0x3A - 0x18) * t)
        draw.line([(0, y), (ds, y)], fill=(r, g, b, 255))

    # Stars
    rng = random.Random(1337)
    for _ in range(70):
        fx, fy = rng.random(), rng.random()
        sx, sy = fx * ds, fy * ds * 0.65
        sr = max(2, int((1.5 + fx * 2) * (ds / 512)))
        sa = int(255 * (0.25 + fy * 0.45))
        draw.ellipse([sx-sr, sy-sr, sx+sr, sy+sr], fill=(255, 255, 255, sa))

    # ── Body — near-black pinstripe suit oval ───────────────────────────────
    draw.ellipse([gx(-1.8), gy(-7.6), gx(1.8), gy(-3.6)], fill=C['jacket'])

    # Pinstripes
    sw = max(1, int(0.03 * u))
    for i in range(-6, 7):
        x = gx(i * 0.28)
        draw.line([(x, gy(-7.5)), (x, gy(-3.7))],
                  fill=(0xCC, 0xCC, 0xCC, 30), width=sw)

    # Lapels
    draw.polygon([(gx(-0.15), gy(-7.55)), (gx(-0.65), gy(-6.85)),
                  (gx(-1.35), gy(-7.05)), (gx(-1.45), gy(-7.60))], fill=C['jacketLt'])
    draw.polygon([(gx(0.15), gy(-7.55)), (gx(0.65), gy(-6.85)),
                  (gx(1.35), gy(-7.05)), (gx(1.45), gy(-7.60))], fill=C['jacketLt'])

    # Pocket square
    draw.polygon([(gx(-1.42), gy(-6.82)), (gx(-1.12), gy(-6.92)),
                  (gx(-1.02), gy(-6.52)), (gx(-1.32), gy(-6.42))], fill=C['shirt'])

    # Belt
    draw.rectangle([gx(-1.75), gy(-4.05), gx(1.75), gy(-4.05) + 0.55*u],
                   fill=C['belt'])
    draw.rectangle([gx(-0.35), gy(-4.05), gx(0.35), gy(-4.05) + 0.55*u],
                   fill=C['buckle'])

    # Gold buttons
    for yf in [-5.05, -5.75, -6.45]:
        br = int(0.13 * u)
        bx, by = gx(0), gy(yf)
        draw.ellipse([bx-br, by-br, bx+br, by+br], fill=C['gold'])

    # ── Shirt collar ────────────────────────────────────────────────────────
    draw.polygon([
        (gx(-0.52), gy(-7.52)), (gx(0),     gy(-6.95)),
        (gx(0.52),  gy(-7.52)), (gx(0.32),  gy(-8.08)),
        (gx(0),     gy(-7.88)), (gx(-0.32), gy(-8.08)),
    ], fill=C['shirt'])

    # Bow tie
    draw.polygon([(gx(-0.05), gy(-7.82)), (gx(-0.5), gy(-7.62)), (gx(-0.5), gy(-8.02))],
                 fill=C['tie'])
    draw.polygon([(gx(0.05), gy(-7.82)), (gx(0.5), gy(-7.62)), (gx(0.5), gy(-8.02))],
                 fill=C['tie'])
    kr = int(0.12 * u)
    kx, ky = int(gx(0)), int(gy(-7.82))
    draw.ellipse([kx-kr, ky-kr, kx+kr, ky+kr], fill=C['tie'])

    # ── Arms ────────────────────────────────────────────────────────────────
    arm_w = max(2, int(0.82 * u))
    hr    = int(0.34 * u)

    # Left arm
    l_arm = cubic_bezier((gx(-1.75), gy(-6.4)), (gx(-2.05), gy(-5.9)),
                          (gx(-2.5),  gy(-5.1)), (gx(-2.7),  gy(-3.8)))
    for i in range(len(l_arm)-1):
        draw.line([l_arm[i], l_arm[i+1]], fill=C['jacketDk'], width=arm_w)
    hx, hy = int(gx(-2.7)), int(gy(-3.8))
    draw.ellipse([hx-hr, hy-hr, hx+hr, hy+hr], fill=C['skin'])

    # Right arm
    r_arm = cubic_bezier((gx(1.75), gy(-6.4)), (gx(2.3), gy(-6.0)),
                          (gx(2.2),  gy(-5.1)), (gx(2.0), gy(-4.5)))
    for i in range(len(r_arm)-1):
        draw.line([r_arm[i], r_arm[i+1]], fill=C['jacketDk'], width=arm_w)
    hx, hy = int(gx(2.0)), int(gy(-4.5))
    draw.ellipse([hx-hr, hy-hr, hx+hr, hy+hr], fill=C['skin'])

    # ── Baton — pointing upper-right (icon pose, mid-swing) ─────────────────
    baton_base = (gx(2.0), gy(-4.5))
    baton_len  = 4.2 * u
    swing_deg  = 35   # degrees to the right of straight-up
    baton_tip  = (
        baton_base[0] + baton_len * math.sin(math.radians(swing_deg)),
        baton_base[1] - baton_len * math.cos(math.radians(swing_deg)),
    )
    bw = max(2, int(0.18 * u))
    draw.line([baton_base, baton_tip], fill=C['batonGold'], width=bw)
    ball_r = int(0.38 * u)
    bx, by = int(baton_tip[0]), int(baton_tip[1])
    draw.ellipse([bx-ball_r, by-ball_r, bx+ball_r, by+ball_r], fill=C['batonGold'])
    # Gold ball highlight
    hl = int(ball_r * 0.45)
    draw.ellipse([bx-hl, by-ball_r+int(ball_r*0.1),
                  bx+int(hl*0.3), by-int(ball_r*0.35)],
                 fill=(0xFF, 0xE5, 0x66, 180))

    # ── Neck ────────────────────────────────────────────────────────────────
    nw = int(0.76 * u)
    nh = int(0.55 * u)
    nx, ny = int(gx(-0.38)), int(gy(-8.5))
    draw.rounded_rectangle([nx, ny, nx+nw, ny+nh],
                            radius=int(0.15*u), fill=C['skin'])

    # ── Head sphere ──────────────────────────────────────────────────────────
    hcx, hcy = gx(0), gy(-10.0)
    r_head = int(1.85 * u)

    # Faint red aura
    ar = int(r_head * 1.3)
    draw.ellipse([hcx-ar, hcy-ar, hcx+ar, hcy+ar], fill=(0xCC, 0x18, 0x18, 20))

    # Head base colour
    draw.ellipse([hcx-r_head, hcy-r_head, hcx+r_head, hcy+r_head], fill=C['skin'])

    # Skin highlight (lighter oval upper-left)
    hl_r = int(r_head * 0.72)
    hl_cx = int(hcx - r_head * 0.22)
    hl_cy = int(hcy - r_head * 0.22)
    draw.ellipse([hl_cx-hl_r, hl_cy-hl_r, hl_cx+hl_r, hl_cy+hl_r],
                 fill=(0xFA, 0xD0, 0x9A, 55))

    # Ears
    for side in [-1, 1]:
        ex = int(hcx + side * r_head * 0.97)
        ey = int(hcy + 0.15 * u)
        er = int(0.36 * u)
        draw.ellipse([ex-er, ey-er, ex+er, ey+er], fill=C['skin'])
        er2 = int(0.20 * u)
        draw.ellipse([ex-er2, ey-er2, ex+er2, ey+er2], fill=C['skinDk'])

    # Cheek blush
    ck_r = int(0.48 * u)
    for side in [-1, 1]:
        ckx = int(hcx + side * 1.05 * u)
        cky = int(hcy + 0.45 * u)
        draw.ellipse([ckx-ck_r, cky-ck_r, ckx+ck_r, cky+ck_r], fill=C['cheek'])

    # ── Grey side-parted hair ─────────────────────────────────────────────────
    for side, sign in [(-1, -1), (1, 1)]:
        hair_pts = cubic_bezier(
            (gx(sign*1.42), gy(-11.52)),
            (gx(sign*1.68), gy(-11.15)),
            (gx(sign*2.08), gy(-10.72)),
            (gx(sign*2.12), gy(-10.1)),
        ) + cubic_bezier(
            (gx(sign*2.12), gy(-10.1)),
            (gx(sign*1.96), gy(-10.0)),
            (gx(sign*1.65), gy(-10.08)),
            (gx(sign*1.52), gy(-10.38)),
        ) + [(gx(sign*1.42), gy(-11.52))]
        draw.polygon(hair_pts, fill=C['hair'])

    # ── Eyebrows ─────────────────────────────────────────────────────────────
    brow_y = gy(-10.85)
    brow_w = max(2, int(0.21 * u))
    for sign in [-1, 1]:
        brow_pts = cubic_bezier(
            (gx(sign*1.58), brow_y + 0.05*u),
            (gx(sign*1.08), brow_y - 0.20*u),
            (gx(sign*0.58), brow_y - 0.15*u),
            (gx(sign*0.20), brow_y + 0.08*u),
        )
        for i in range(len(brow_pts)-1):
            draw.line([brow_pts[i], brow_pts[i+1]], fill=C['beardShade'], width=brow_w)

    # ── Gold-frame sunglasses ─────────────────────────────────────────────────
    lens_y  = gy(-10.3)
    lw_gl   = 1.1 * u
    lh_gl   = 0.62 * u
    ftk     = max(3, int(0.1 * u))
    crn     = int(0.2 * u)

    for lx in [gx(-0.7), gx(0.7)]:
        x1, y1 = lx - lw_gl/2, lens_y - lh_gl/2
        x2, y2 = lx + lw_gl/2, lens_y + lh_gl/2
        draw.rounded_rectangle([x1, y1, x2, y2], radius=crn, fill=C['glassLens'])
        draw.rounded_rectangle([x1, y1, x2, y2], radius=crn,
                                outline=C['glassFrame'], width=ftk)
        # Reflection
        rw = max(2, int(0.11 * u))
        draw.line([(lx - lw_gl*0.3, lens_y - lh_gl*0.25),
                   (lx - lw_gl*0.05, lens_y + lh_gl*0.15)],
                  fill=C['glassRefl'], width=rw)

    bw_gl = max(2, int(0.08 * u))
    draw.line([(gx(-0.15), lens_y), (gx(0.15), lens_y)],
              fill=C['glassFrame'], width=bw_gl)
    draw.line([(gx(-0.7) - lw_gl/2, lens_y), (gx(-1.82), lens_y + 0.1*u)],
              fill=C['glassFrame'], width=bw_gl)
    draw.line([(gx(0.7) + lw_gl/2, lens_y), (gx(1.82), lens_y + 0.1*u)],
              fill=C['glassFrame'], width=bw_gl)

    # ── Nose ─────────────────────────────────────────────────────────────────
    nw_px = 0.88 * u
    nh_px = 0.72 * u
    draw.ellipse([gx(0) - nw_px/2, gy(-9.72),
                  gx(0) + nw_px/2, gy(-9.72) + nh_px], fill=C['nose'])

    # ── Mustache — wide drooping Santa wings ──────────────────────────────────
    mb_y = gy(-9.12)

    def mustache_wing(sign):
        return (
            cubic_bezier(
                (gx(sign*0.08), mb_y),
                (gx(sign*0.45), mb_y - 0.12*u),
                (gx(sign*1.35), mb_y - 0.08*u),
                (gx(sign*1.58), mb_y + 0.42*u),
            ) +
            cubic_bezier(
                (gx(sign*1.58), mb_y + 0.42*u),
                (gx(sign*1.45), mb_y + 0.62*u),
                (gx(sign*0.78), mb_y + 0.55*u),
                (gx(sign*0.35), mb_y + 0.44*u),
            ) +
            cubic_bezier(
                (gx(sign*0.35), mb_y + 0.44*u),
                (gx(sign*0.12), mb_y + 0.36*u),
                (gx(0),         mb_y + 0.25*u),
                (gx(sign*0.08), mb_y),
            )
        )

    draw.polygon(mustache_wing(-1), fill=C['beard'])
    draw.polygon(mustache_wing(1),  fill=C['beard'])

    # Shadow under mustache
    draw.line([(gx(-1.2), mb_y + 0.52*u), (gx(1.2), mb_y + 0.52*u)],
              fill=(0xB8, 0xB0, 0xA8, 100), width=max(2, int(0.08*u)))

    # ── Hat — drawn on a separate RGBA layer then rotated 11° CW ─────────────
    hat_img  = Image.new('RGBA', (ds, ds), (0, 0, 0, 0))
    h_draw   = ImageDraw.Draw(hat_img)
    hby      = gy(-11.85)   # hat base Y
    pivot    = (cx, hby)

    # Brim shadow (dark oval beneath brim)
    h_draw.ellipse([gx(-2.55), hby - 0.28*u,
                    gx(2.55),  hby - 0.28*u + 0.65*u],
                   fill=(0x6A, 0x0A, 0x0A, 190))

    # Brim top (hatRedDark)
    h_draw.ellipse([gx(-2.38), hby - 0.50*u,
                    gx(2.38),  hby - 0.50*u + 0.65*u],
                   fill=C['hatRedDk'])

    # Cone — curved sides via bezier
    cone_pts = (
        cubic_bezier(
            (gx(-1.75), hby),
            (gx(-1.45), hby - 2.0*u),
            (gx(-0.22), hby - 4.9*u),
            (gx(0),     hby - 5.1*u),
        ) +
        cubic_bezier(
            (gx(0),     hby - 5.1*u),
            (gx(0.22),  hby - 4.9*u),
            (gx(1.45),  hby - 2.0*u),
            (gx(1.75),  hby),
        )
    )
    h_draw.polygon(cone_pts, fill=C['hatRed'])

    # Left-side shading (darker red) for depth
    shade_pts = (
        cubic_bezier(
            (gx(-1.75), hby),
            (gx(-1.45), hby - 2.0*u),
            (gx(-0.22), hby - 4.9*u),
            (gx(0),     hby - 5.1*u),
        ) +
        cubic_bezier(
            (gx(0),     hby - 5.1*u),
            (gx(-0.18), hby - 4.6*u),
            (gx(-0.95), hby - 2.3*u),
            (gx(-1.25), hby),
        )
    )
    h_draw.polygon(shade_pts, fill=C['hatRedDk'])

    # Seam highlight (faint light line on left edge)
    for xf in [-0.45, 0.45]:
        h_draw.line(
            [(gx(xf), hby - 0.62*u), (gx(xf*0.18), hby - 4.7*u)],
            fill=(0xDD, 0x35, 0x35, 77),
            width=max(2, int(0.06*u)),
        )

    # Dark band at brim
    h_draw.polygon([
        (gx(-1.7), hby - 0.05*u), (gx(-1.3), hby - 0.68*u),
        (gx(1.3),  hby - 0.68*u), (gx(1.7),  hby - 0.05*u),
    ], fill=C['hatBand'])

    # Gold star on cone (at -0.18u from center, 3.7u up from brim)
    star_cx = gx(-0.18)
    star_cy = hby - 3.7 * u
    outer_r = 0.36 * u
    inner_r = outer_r * 0.42
    h_draw.polygon(star_polygon(star_cx, star_cy, outer_r, inner_r),
                   fill=C['hatGold'])

    # Rotate hat layer 11° clockwise around hat base pivot
    hat_rotated = hat_img.rotate(-11, resample=Image.BICUBIC,
                                  center=(int(pivot[0]), int(pivot[1])),
                                  expand=False)
    img = Image.alpha_composite(img, hat_rotated)
    return img

# ── Output paths ──────────────────────────────────────────────────────────────

BASE = r"C:\Users\wlambrechts\AndroidStudioProjects\MetroGnome\app\src\main\res"
STORE = r"C:\Users\wlambrechts\AndroidStudioProjects\MetroGnome\app"

MIPMAP_SIZES = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi':  144,
    'mipmap-xxxhdpi': 192,
}

if __name__ == '__main__':
    print("Drawing Metro at 2048×2048…")
    master = draw_gnome(2048)

    # 512×512 Play Store icon (PNG)
    store_path = os.path.join(STORE, 'store_icon_512.png')
    master.resize((512, 512), Image.LANCZOS).save(store_path, 'PNG')
    print(f"  Saved store_icon_512.png")

    # All mipmap sizes
    for folder, px in MIPMAP_SIZES.items():
        resized = master.resize((px, px), Image.LANCZOS).convert('RGBA')
        for name in ['ic_launcher.webp', 'ic_launcher_round.webp']:
            out = os.path.join(BASE, folder, name)
            resized.save(out, 'WEBP', quality=95)
        print(f"  Saved {folder} ({px}×{px})")

    print("Done!")
