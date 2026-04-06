#!/usr/bin/env python3
"""
Generate MetroGnome launcher icons.
Face-focused headshot: hat + clean solid face + just a collar hint.
No semi-transparent overlays — every fill is fully opaque.
"""

from PIL import Image, ImageDraw
import math, random, os

# ── Helpers ───────────────────────────────────────────────────────────────────

def cubic_bezier(p0, p1, p2, p3, n=60):
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

# ── Master draw ───────────────────────────────────────────────────────────────

def draw_gnome(ds=2048):
    """
    Face-focused headshot. Coordinate system matches GnomeCanvas.kt:
    origin = feet, negative Y = up, 1 unit (u) = ds / 11.
    Head is centred at ~65% from the top; hat tip sits near the top edge.
    """
    u     = ds / 11.0
    cx    = ds / 2.0
    # Head centre (gy(-10)) at 65% from top
    baseY = ds * 0.65 + 10.0 * u

    def gx(xu): return cx + xu * u
    def gy(yu): return baseY + yu * u

    # ── Exact GnomeColors from Color.kt (all fully opaque) ──────────────────
    SKIN     = (0xF0, 0xBC, 0x80, 255)
    SKIN_DK  = (0xD8, 0xA0, 0x60, 255)
    BEARD    = (0xF2, 0xEE, 0xEA, 255)
    BSHADE   = (0xB8, 0xB0, 0xA8, 255)
    HAT_R    = (0xCC, 0x18, 0x18, 255)
    HAT_LT   = (0xDD, 0x35, 0x35, 255)
    HAT_DK   = (0x88, 0x10, 0x10, 255)
    HAT_BRIM = (0x6A, 0x0A, 0x0A, 255)
    HAT_BAND = (0x44, 0x06, 0x06, 255)
    HAT_GOLD = (0xFF, 0xD7, 0x00, 255)
    JACKET   = (0x11, 0x11, 0x15, 255)
    JACKET_L = (0x1C, 0x1C, 0x22, 255)
    SHIRT    = (0xF8, 0xF4, 0xEE, 255)
    TIE      = (0xAA, 0x1E, 0x2E, 255)
    HAIR     = (0xB5, 0xB0, 0xAB, 255)
    HAIR_DK  = (0x88, 0x82, 0x80, 255)
    NOSE_C   = (0xCC, 0x88, 0x68, 255)
    G_FRAME  = (0xFF, 0xD7, 0x00, 255)
    G_LENS   = (0x08, 0x08, 0x18, 255)
    G_REFL   = (0x22, 0x44, 0x88, 255)   # opaque — no alpha tricks

    img  = Image.new('RGB', (ds, ds), (0x0A, 0x08, 0x18))
    draw = ImageDraw.Draw(img)

    # ── Background gradient ──────────────────────────────────────────────────
    for y in range(ds):
        t = y / ds
        draw.line([(0, y), (ds, y)], fill=(
            int(0x0A + (0x16 - 0x0A) * t),
            int(0x08 + (0x13 - 0x08) * t),
            int(0x18 + (0x3A - 0x18) * t),
        ))

    # Stars
    rng = random.Random(1337)
    for _ in range(70):
        fx, fy = rng.random(), rng.random()
        sx, sy = fx * ds, fy * ds * 0.65
        sr = max(2, int((1.5 + fx * 2) * (ds / 512)))
        sa = int(220 * (0.25 + fy * 0.45))
        draw.ellipse([sx-sr, sy-sr, sx+sr, sy+sr], fill=(sa, sa, sa))

    # ── Collar hint — just the very top of the suit ──────────────────────────
    # Suit body top
    draw.ellipse([gx(-1.8), gy(-7.6), gx(1.8), gy(-3.6)], fill=JACKET)

    # Lapels
    draw.polygon([(gx(-0.15), gy(-7.55)), (gx(-0.65), gy(-6.85)),
                  (gx(-1.35), gy(-7.05)), (gx(-1.45), gy(-7.60))], fill=JACKET_L)
    draw.polygon([(gx(0.15), gy(-7.55)), (gx(0.65), gy(-6.85)),
                  (gx(1.35), gy(-7.05)), (gx(1.45), gy(-7.60))], fill=JACKET_L)

    # White shirt collar
    draw.polygon([
        (gx(-0.52), gy(-7.52)), (gx(0),     gy(-6.95)),
        (gx(0.52),  gy(-7.52)), (gx(0.32),  gy(-8.08)),
        (gx(0),     gy(-7.88)), (gx(-0.32), gy(-8.08)),
    ], fill=SHIRT)

    # Bow tie
    draw.polygon([(gx(-0.05), gy(-7.82)), (gx(-0.5), gy(-7.62)), (gx(-0.5), gy(-8.02))], fill=TIE)
    draw.polygon([(gx(0.05),  gy(-7.82)), (gx(0.5),  gy(-7.62)), (gx(0.5),  gy(-8.02))], fill=TIE)
    kr = int(0.12 * u)
    draw.ellipse([gx(0)-kr, gy(-7.82)-kr, gx(0)+kr, gy(-7.82)+kr], fill=TIE)

    # ── Neck ────────────────────────────────────────────────────────────────
    nw, nh = int(0.76 * u), int(0.55 * u)
    nx, ny = int(gx(-0.38)), int(gy(-8.5))
    draw.rounded_rectangle([nx, ny, nx+nw, ny+nh], radius=int(0.15*u), fill=SKIN)

    # ── Head — clean solid sphere, NO transparency ───────────────────────────
    hcx, hcy = gx(0), gy(-10.0)
    r_head   = int(1.85 * u)

    # Ears (behind head so draw first)
    for side in [-1, 1]:
        ex = int(hcx + side * r_head * 0.97)
        ey = int(hcy + 0.15 * u)
        er = int(0.36 * u)
        draw.ellipse([ex-er, ey-er, ex+er, ey+er], fill=SKIN)
        er2 = int(0.20 * u)
        draw.ellipse([ex-er2, ey-er2, ex+er2, ey+er2], fill=SKIN_DK)

    # Head — single solid fill, no overlays
    draw.ellipse([hcx-r_head, hcy-r_head, hcx+r_head, hcy+r_head], fill=SKIN)

    # ── Grey side-parted hair ─────────────────────────────────────────────────
    for sign in [-1, 1]:
        hair_pts = (
            cubic_bezier(
                (gx(sign*1.42), gy(-11.52)),
                (gx(sign*1.68), gy(-11.15)),
                (gx(sign*2.08), gy(-10.72)),
                (gx(sign*2.12), gy(-10.1)),
            ) +
            cubic_bezier(
                (gx(sign*2.12), gy(-10.1)),
                (gx(sign*1.96), gy(-10.0)),
                (gx(sign*1.65), gy(-10.08)),
                (gx(sign*1.52), gy(-10.38)),
            ) +
            [(gx(sign*1.42), gy(-11.52))]
        )
        draw.polygon(hair_pts, fill=HAIR)

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
            draw.line([brow_pts[i], brow_pts[i+1]], fill=BSHADE, width=brow_w)

    # ── Gold-frame sunglasses ─────────────────────────────────────────────────
    lens_y = gy(-10.3)
    lw_gl  = 1.1 * u
    lh_gl  = 0.62 * u
    ftk    = max(3, int(0.1 * u))
    crn    = int(0.2 * u)

    for lx in [gx(-0.7), gx(0.7)]:
        x1, y1 = lx - lw_gl/2, lens_y - lh_gl/2
        x2, y2 = lx + lw_gl/2, lens_y + lh_gl/2
        draw.rounded_rectangle([x1, y1, x2, y2], radius=crn, fill=G_LENS)
        draw.rounded_rectangle([x1, y1, x2, y2], radius=crn, outline=G_FRAME, width=ftk)
        # Opaque reflection stripe (no alpha)
        rw = max(2, int(0.08 * u))
        draw.line([(lx - lw_gl*0.30, lens_y - lh_gl*0.25),
                   (lx - lw_gl*0.05, lens_y + lh_gl*0.15)],
                  fill=G_REFL, width=rw)

    bw_gl = max(2, int(0.08 * u))
    draw.line([(gx(-0.15), lens_y), (gx(0.15), lens_y)], fill=G_FRAME, width=bw_gl)
    draw.line([(gx(-0.7) - lw_gl/2, lens_y), (gx(-1.82), lens_y + 0.1*u)], fill=G_FRAME, width=bw_gl)
    draw.line([(gx(0.7)  + lw_gl/2, lens_y), (gx(1.82),  lens_y + 0.1*u)], fill=G_FRAME, width=bw_gl)

    # ── Nose ─────────────────────────────────────────────────────────────────
    draw.ellipse([gx(0) - 0.44*u, gy(-9.72),
                  gx(0) + 0.44*u, gy(-9.72) + 0.72*u], fill=NOSE_C)

    # ── Mustache — wide drooping Santa wings ─────────────────────────────────
    mb_y = gy(-9.12)

    def wing(sign):
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

    draw.polygon(wing(-1), fill=BEARD)
    draw.polygon(wing(1),  fill=BEARD)

    # Subtle shadow groove at centre
    draw.line([(gx(-1.2), mb_y + 0.52*u), (gx(1.2), mb_y + 0.52*u)],
              fill=BSHADE, width=max(2, int(0.06*u)))

    # ── Hat — separate RGBA layer, rotated 11° CW around hat base ────────────
    hat_img  = Image.new('RGBA', (ds, ds), (0, 0, 0, 0))
    h_draw   = ImageDraw.Draw(hat_img)
    hby      = gy(-11.85)
    pivot    = (cx, hby)

    # Brim shadow oval
    h_draw.ellipse([gx(-2.55), hby - 0.28*u,
                    gx(2.55),  hby - 0.28*u + 0.65*u],
                   fill=HAT_BRIM)

    # Brim top
    h_draw.ellipse([gx(-2.38), hby - 0.50*u,
                    gx(2.38),  hby - 0.50*u + 0.65*u],
                   fill=HAT_DK)

    # Cone — bezier sides
    cone_pts = (
        cubic_bezier(
            (gx(-1.75), hby), (gx(-1.45), hby - 2.0*u),
            (gx(-0.22), hby - 4.9*u), (gx(0), hby - 5.1*u),
        ) +
        cubic_bezier(
            (gx(0), hby - 5.1*u), (gx(0.22), hby - 4.9*u),
            (gx(1.45), hby - 2.0*u), (gx(1.75), hby),
        )
    )
    h_draw.polygon(cone_pts, fill=HAT_R)

    # Left-side shading for depth
    shade_pts = (
        cubic_bezier(
            (gx(-1.75), hby), (gx(-1.45), hby - 2.0*u),
            (gx(-0.22), hby - 4.9*u), (gx(0), hby - 5.1*u),
        ) +
        cubic_bezier(
            (gx(0), hby - 5.1*u), (gx(-0.18), hby - 4.6*u),
            (gx(-0.95), hby - 2.3*u), (gx(-1.25), hby),
        )
    )
    h_draw.polygon(shade_pts, fill=HAT_DK)

    # Right-side highlight
    hi_pts = (
        cubic_bezier(
            (gx(1.75), hby), (gx(1.45), hby - 2.0*u),
            (gx(0.22), hby - 4.9*u), (gx(0), hby - 5.1*u),
        ) +
        cubic_bezier(
            (gx(0), hby - 5.1*u), (gx(0.1), hby - 4.8*u),
            (gx(0.8), hby - 2.5*u), (gx(1.1), hby),
        )
    )
    h_draw.polygon(hi_pts, fill=HAT_LT)

    # Dark band near brim
    h_draw.polygon([
        (gx(-1.7), hby - 0.05*u), (gx(-1.3), hby - 0.68*u),
        (gx(1.3),  hby - 0.68*u), (gx(1.7),  hby - 0.05*u),
    ], fill=HAT_BAND)

    # Gold star on cone
    star_cx = gx(-0.18)
    star_cy = hby - 3.7 * u
    h_draw.polygon(star_polygon(star_cx, star_cy, 0.36*u, 0.36*0.42*u), fill=HAT_GOLD)

    # Rotate 11° CW (negative in PIL = clockwise)
    hat_rot = hat_img.rotate(-11, resample=Image.BICUBIC,
                              center=(int(pivot[0]), int(pivot[1])), expand=False)
    # Composite hat onto RGB image
    img_rgba = img.convert('RGBA')
    img_rgba = Image.alpha_composite(img_rgba, hat_rot)
    img = img_rgba.convert('RGB')

    return img

# ── Output ────────────────────────────────────────────────────────────────────

BASE  = r"C:\Users\wlambrechts\AndroidStudioProjects\MetroGnome\app\src\main\res"
STORE = r"C:\Users\wlambrechts\AndroidStudioProjects\MetroGnome\app"

SIZES = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi':  144,
    'mipmap-xxxhdpi': 192,
}

if __name__ == '__main__':
    print("Drawing Metro at 2048×2048…")
    master = draw_gnome(2048)

    store_path = os.path.join(STORE, 'store_icon_512.png')
    master.resize((512, 512), Image.LANCZOS).save(store_path, 'PNG')
    print("  Saved store_icon_512.png")

    for folder, px in SIZES.items():
        resized = master.resize((px, px), Image.LANCZOS)
        for name in ['ic_launcher.webp', 'ic_launcher_round.webp']:
            resized.save(os.path.join(BASE, folder, name), 'WEBP', quality=95)
        print(f"  Saved {folder} ({px}×{px})")

    print("Done!")
