#!/usr/bin/env python3
"""Refines docs/logo/eye-gradient.svg (a hand-traced proposal): the same composition and colours, with smooth curves instead of a faceted polygon.
  - the eye's upper lid is a smooth curve through the proposal's outline; its lower edge IS the dune line, so nothing shows beneath the stroke
  - the pupil is a real cutout (even-odd), so the background gradient shows through exactly instead of a second, slightly different gradient
  - the hairline black outline is gone
    python3 tools/logo/refine_eye_gradient.py     # docs/logo/eye-gradient-refined*.svg/png and compare-eye-gradient.png"""
import os, subprocess
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "logo")

def bez(p, t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = p; u = 1 - t
    return (u**3*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t**3*x3, u**3*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t**3*y3)

def split(p, t):
    """De Casteljau: the first part, [0, t], of a cubic."""
    lerp = lambda a, b: (a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t)
    a, b, c = lerp(p[0], p[1]), lerp(p[1], p[2]), lerp(p[2], p[3]); d, e = lerp(a, b), lerp(b, c); f = lerp(d, e)
    return (p[0], a, d, f)

def catmull(points):
    """A smooth cubic path through the points (Catmull-Rom)."""
    pts = [points[0]] + points + [points[-1]]; d = ""
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i-1], pts[i], pts[i+1], pts[i+2]
        c1 = (p1[0] + (p2[0]-p0[0])/6, p1[1] + (p2[1]-p0[1])/6); c2 = (p2[0] - (p3[0]-p1[0])/6, p2[1] - (p3[1]-p1[1])/6)
        d += f"C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}"
    return d

# The proposal's dune line (two cubics) -------------------------------------------------------------------------------------------------
SEG1 = ((53.548, 315.236), (141.058, 278.743), (202.926, 343.345), (274.252, 332.092))
SEG2 = ((274.252, 332.092), (345.579, 320.84), (389.065, 380.779), (460.392, 369.526))
DUNE = "M53.548,315.236C141.058,278.743 202.926,343.345 274.252,332.092C345.579,320.84 389.065,380.779 460.392,369.526"

# The eye: one clean lens. Its left corner is where the dune line starts, its upper lid is a smooth arch through the proposal's high point, and
# its lower edge is the dune line itself, so nothing is left under the stroke. It meets the dune again on the right, at x = 436.
def eye_path():
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if bez(SEG2, mid)[0] < 436 else (lo, mid)
    sub = split(SEG2, lo); end = sub[3]; s1 = SEG1
    d = f"M{s1[0][0]},{s1[0][1]}"
    d += "C50,226 126,141 232,140"                                           # up the left and over the top
    d += "C340,139 446,205 461,297"                                          # across and down the right
    d += f"C463,326 454,350 {end[0]:.1f},{end[1]:.1f}"                       # round the right corner onto the dune line
    d += f"C{sub[2][0]:.1f},{sub[2][1]:.1f} {sub[1][0]:.1f},{sub[1][1]:.1f} {sub[0][0]:.1f},{sub[0][1]:.1f}"      # back along the dune: second curve, reversed
    d += f"C{s1[2][0]},{s1[2][1]} {s1[1][0]},{s1[1][1]} {s1[0][0]},{s1[0][1]}Z"                                  # first curve, reversed
    return d

# The pupil, as a hole: a tall ellipse, a little smaller than the proposal's so it sits wholly inside the lid (its foot goes under the dune line)
PUPIL = "M182,244A74,92 0 1 0 330,244A74,92 0 1 0 182,244Z"

def svg(dune_stops, rounded=True):
    st = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in dune_stops)
    clip = '<clipPath id="m"><rect width="512" height="512" rx="122.8"/></clipPath>' if rounded else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512"><defs>'
            '<linearGradient id="bg" x1="0" y1="0" x2="0" y2="512" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#303033"/><stop offset="1" stop-color="#0f0f11"/></linearGradient>'
            '<linearGradient id="eye" x1="168.9" y1="82.3" x2="343.1" y2="429.7" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#9be4ff"/><stop offset=".5" stop-color="#00a4dc"/><stop offset="1" stop-color="#aa5cc3"/></linearGradient>'
            f'<linearGradient id="dune" x1="196.4" y1="216.7" x2="317.5" y2="458.2" gradientUnits="userSpaceOnUse">{st}</linearGradient>{clip}</defs>'
            f'<g clip-path="url(#m)"><rect width="512" height="512" fill="url(#bg)"/>'
            f'<path d="{eye_path()} {PUPIL}" fill="url(#eye)" fill-rule="evenodd"/>'
            f'<path d="{DUNE}" fill="none" stroke="url(#dune)" stroke-width="20.859" stroke-linecap="round" stroke-linejoin="round"/></g></svg>')

VARIANTS = {   # (name, gradient stops along the dune line)
    "": [(.2552, "#ff6633"), (.4617, "#00a4dc"), (.7679, "#ff9125")],                                        # as proposed: orange, with the eye's blue beneath the pupil
    "-calm": [(0, "#ff6a35"), (1, "#ff9a2c")],                                                               # plain orange, nothing to muddy
}

if __name__ == "__main__":
    ims = []
    orig = os.path.join(OUT, "eye-gradient.svg")
    subprocess.run(["rsvg-convert", "-w", "512", orig, "-o", "/tmp/_orig.png"], check=True)
    o = Image.open("/tmp/_orig.png").convert("RGBA")
    mask = Image.new("L", o.size, 0)
    from PIL import ImageDraw
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 511, 511), radius=123, fill=255); o.putalpha(mask); ims.append(o)
    for suffix, stops in VARIANTS.items():
        p = os.path.join(OUT, f"eye-gradient-refined{suffix}.svg"); open(p, "w").write(svg(stops))
        subprocess.run(["rsvg-convert", "-w", "512", p, "-o", p.replace(".svg", ".png")], check=True)
        ims.append(Image.open(p.replace(".svg", ".png")).convert("RGBA"))
    sheet = Image.new("RGB", (512 * 3 + 80, 592), (158, 160, 246))
    for i, im in enumerate(ims): sheet.paste(im, (20 + 532 * i, 40), im)
    sheet.save(os.path.join(OUT, "compare-eye-gradient.png")); print("ok")
