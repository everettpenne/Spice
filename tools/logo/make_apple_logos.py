#!/usr/bin/env python3
"""Spice logo in Apple's icon language: filled shapes with soft gradients and a lit rim (no outlines), dunes layered behind the eye.
Written without filters so it converts directly to Android vector drawables (shadows are stacked translucent shapes).
    python3 tools/logo/make_apple_logos.py     # docs/logo/apple-*.svg/png and docs/logo/preview-apple.png"""
import math, os, subprocess
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "logo")

def squircle(n=5.0, size=108, steps=240):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        c, s = math.cos(t), math.sin(t)
        x = math.copysign(abs(c) ** (2 / n), c); y = math.copysign(abs(s) ** (2 / n), s)
        pts.append((size / 2 + x * size / 2, size / 2 + y * size / 2))
    return "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z"

def lin(id_, x1, y1, x2, y2, stops):
    st = "".join(f'<stop offset="{o}" stop-color="{c}"' + (f' stop-opacity="{a}"' if a is not None else "") + "/>" for o, c, a in stops)
    return f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" gradientUnits="userSpaceOnUse">{st}</linearGradient>'

def rad(id_, cx, cy, r, stops):
    st = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{r}" gradientUnits="userSpaceOnUse">{st}</radialGradient>'

def circle(cx, cy, r):
    return f"M{cx-r:.2f},{cy:.2f}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0Z"

# Three palettes: dusk (warm), midnight (the brand blue), sand (light)
PALETTES = {
    "dusk": dict(sky=[(0, "#0E1A45"), (.42, "#34439C"), (.74, "#E0895A"), (1, "#F7C27E")],
                 dunes=[("#A85A45", "#7C3F3B"), ("#D2814F", "#A9593F"), ("#F0AE68", "#D2824A")],
                 rim="#FFE2B0", eye_out="#0A3A86", eye_in="#1C74D6", iris=[(0, "#B4ECFF"), (.55, "#43B6F5"), (1, "#1E78D8")], glass="#FFFFFF"),
    "midnight": dict(sky=[(0, "#070B20"), (.5, "#14246B"), (1, "#2A56B8")],
                     dunes=[("#1A3C86", "#12296B"), ("#2B5CB8", "#1B3F8C"), ("#4D8BE0", "#2E62BD")],
                     rim="#BFE6FF", eye_out="#0A2C6B", eye_in="#1A6FD0", iris=[(0, "#C4F1FF"), (.55, "#4CC0FF"), (1, "#1A78D6")], glass="#FFFFFF"),
    "sand": dict(sky=[(0, "#FFF4DC"), (.55, "#F8DDAE"), (1, "#EDBE82")],
                 dunes=[("#E2AE72", "#CC9055"), ("#D79A5E", "#BC7B45"), ("#C98547", "#A9632F")],
                 rim="#FFF6E2", eye_out="#0B3F95", eye_in="#1F78DA", iris=[(0, "#BDEFFF"), (.55, "#45B8F6"), (1, "#1F7BDC")], glass="#FFFFFF"),
}

def icon(p):
    defs = lin("sky", 0, 0, 0, 108, [(o, c, None) for o, c in p["sky"]])
    for i, (a, b) in enumerate(p["dunes"]):
        defs += lin(f"d{i}", 0, 50 + i * 12, 0, 108, [(0, a, None), (1, b, None)])
    defs += lin("eye", 0, 34, 0, 76, [(0, p["eye_in"], None), (1, p["eye_out"], None)])
    defs += rad("iris", 54, 52, 17, p["iris"])
    defs += lin("sheen", 0, 30, 0, 60, [(0, p["glass"], .55), (1, p["glass"], 0)])
    defs += lin("rimlight", 20, 30, 88, 76, [(0, p["glass"], .95), (.5, p["glass"], .35), (1, p["glass"], .75)])
    # Dunes, far to near, each running past the edges; a thin light rim along each crest is the only "line".
    crests = ["M-6,70C14,60 34,68 54,60C74,52 92,62 114,56", "M-6,80C16,70 38,80 60,72C80,65 96,74 114,68", "M-6,92C20,82 44,92 66,84C86,77 100,86 114,80"]
    body = f'<rect width="108" height="108" fill="url(#sky)"/>'
    for i, c in enumerate(crests):
        body += f'<path d="{c}L114,114L-6,114Z" fill="url(#d{i})"/>'
        body += f'<path d="{c}" fill="none" stroke="{p["rim"]}" stroke-opacity="{.45 + .15 * i}" stroke-width="0.9" stroke-linecap="round"/>'
    eye = "M20,52C34,23 74,23 88,52C74,81 34,81 20,52Z"
    # Soft shadow: a few translucent copies of the eye, lower and wider.
    for dy, grow, a in ((5.5, 3.0, .10), (4.0, 1.8, .12), (2.5, .8, .16)):
        body += f'<path d="M{20-grow},52C{34-grow},{23-grow} {74+grow},{23-grow} {88+grow},52C{74+grow},{81+grow} {34-grow},{81+grow} {20-grow},52Z" transform="translate(0,{dy})" fill="#000" fill-opacity="{a}"/>'
    body += f'<path d="{eye}" fill="url(#eye)"/>'
    body += f'<clipPath id="e"><path d="{eye}"/></clipPath><g clip-path="url(#e)"><path d="{circle(54,52,19)}" fill="url(#iris)"/>'
    body += f'<path d="{eye}" fill="url(#sheen)"/></g>'                                   # glass sheen over the upper half
    body += f'<path d="{eye}" fill="none" stroke="url(#rimlight)" stroke-width="1.8" stroke-linejoin="round"/>'   # lit rim
    return defs, body

def svg(p, mask=True):
    defs, body = icon(p)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><defs>{defs}'
            f'<clipPath id="m"><path d="{squircle()}"/></clipPath></defs><g clip-path="url(#m)">{body}</g></svg>')

if __name__ == "__main__":
    pngs = []
    for name, p in PALETTES.items():
        path = os.path.join(OUT, f"apple-{name}.svg"); open(path, "w").write(svg(p))
        png = path.replace(".svg", ".png"); subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", path, "-o", png], check=True); pngs.append(png)
    ims = [Image.open(x).convert("RGBA") for x in pngs]
    sheet = Image.new("RGB", (512 * 3 + 80, 592), (236, 236, 242))
    for i, im in enumerate(ims): sheet.paste(im, (20 + 532 * i, 40), im)
    sheet.save(os.path.join(OUT, "preview-apple.png")); print("ok")
