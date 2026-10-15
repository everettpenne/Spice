#!/usr/bin/env python3
"""Spice logo: a cloth bag with blue accents and cinnamon-coloured spice heaped in its mouth and spilling down its side. Apple-style (filled shapes,
soft gradients, lit rim, no outlines), built without filters so it converts to Android vector drawables.
    python3 tools/logo/make_bag_logos.py     # docs/logo/bag-*.svg/png and docs/logo/preview-bag.png"""
import math, os, random, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_apple_logos import squircle, lin, rad
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "logo")

PALETTES = {
    # navy ground, a luminous blue bag
    "midnight": dict(bg=[(0, "#0B1648"), (1, "#1B3A9C")], bag=("#5AA2FF", "#1E55C8"), inner="#0B2570", band=("#BFE0FF", "#7FB6F5"), lip="#CFE8FF",
                     patch="#FFFFFF", shadow=.34, ground="#0A1440"),
    # bright cobalt ground, a pale cloth bag (white glyph on blue, as many iOS icons are)
    "cobalt":   dict(bg=[(0, "#4D9BFF"), (1, "#1747C7")], bag=("#FFFFFF", "#CFE0FA"), inner="#7FA6E8", band=("#2F73E8", "#1A4FC4"), lip="#FFFFFF",
                     patch="#2F73E8", shadow=.30, ground="#103A9E"),
    # soft light ground, a saturated blue bag
    "light":    dict(bg=[(0, "#F7F9FF"), (1, "#D9E4FA")], bag=("#4F96FF", "#1B4FC2"), inner="#0F2F86", band=("#CFE6FF", "#8EBEF7"), lip="#D8ECFF",
                     patch="#FFFFFF", shadow=.20, ground="#9FB4DA"),
}
SPICE = ("#E3965A", "#8C4524")            # cinnamon: lit top, dark base

def grains(seed, area, n, rmin, rmax, colors, opacity=(.5, .9)):
    """Scattered spice grains inside a box (x0, y0, x1, y1): small filled dots in cinnamon shades."""
    rnd = random.Random(seed); x0, y0, x1, y1 = area; out = ""
    for _ in range(n):
        x, y, r = rnd.uniform(x0, x1), rnd.uniform(y0, y1), rnd.uniform(rmin, rmax)
        out += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.2f}" fill="{rnd.choice(colors)}" fill-opacity="{rnd.uniform(*opacity):.2f}"/>'
    return out

def sparkle(cx, cy, r):
    k = r * 0.2
    return f"M{cx},{cy-r}C{cx+k},{cy-k} {cx+k},{cy-k} {cx+r},{cy}C{cx+k},{cy+k} {cx+k},{cy+k} {cx},{cy+r}C{cx-k},{cy+k} {cx-k},{cy+k} {cx-r},{cy}C{cx-k},{cy-k} {cx-k},{cy-k} {cx},{cy-r}Z"

# The bag is drawn upright in its own 108-unit frame, then tipped to pour. These place it on the canvas.
TIP = dict(t=(47, 41), rot=116, scale=.9, pivot=(52, 62))

def place(x, y):
    """Where a point of the upright bag lands on the canvas after it is tipped."""
    a = math.radians(TIP["rot"]); c, s_ = math.cos(a), math.sin(a)
    x = (x - TIP["pivot"][0]) * TIP["scale"]; y = (y - TIP["pivot"][1]) * TIP["scale"]
    return TIP["t"][0] + x * c - y * s_, TIP["t"][1] + x * s_ + y * c

def frill(cx, cy, rx, ry, lobes=11, amp=0.0, steps=120):
    """The gathered edge of the bag's mouth: an ellipse whose edge ripples like cloth drawn in by a cord."""
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        k = 1 + (amp / rx) * math.sin(lobes * t)
        pts.append((cx + rx * k * math.cos(t), cy + ry * k * math.sin(t)))
    return "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z"

def icon(p):
    defs = lin("bg", 0, 0, 0, 108, [(o, c, None) for o, c in p["bg"]])
    defs += lin("bag", 22, 40, 86, 94, [(0, p["bag"][0], None), (1, p["bag"][1], None)])
    defs += lin("band", 0, 48, 0, 60, [(0, p["band"][0], None), (1, p["band"][1], None)])
    defs += lin("top", 0, 30, 0, 48, [(0, SPICE[0], None), (1, "#B2602F", None)])
    defs += lin("fall", 0, 52, 0, 92, [(0, SPICE[0], None), (1, SPICE[1], None)])
    defs += lin("sheen", 22, 44, 50, 80, [(0, "#FFFFFF", .42), (1, "#FFFFFF", 0)])
    body = '<rect width="108" height="108" fill="url(#bg)"/>'
    mx, my = place(54, 41)
    # ground shadow under the pile
    for rx, a_ in ((40, .10), (33, .12), (26, .16)):
        body += f'<ellipse cx="{mx+9:.1f}" cy="95" rx="{rx}" ry="{rx*0.13:.1f}" fill="{p["ground"]}" fill-opacity="{a_}"/>'
    # --- the falling spice, behind the bag's mouth: a core stream, two thinner side streams, and loose grains
    ex = mx + 11
    stream = (f"M{mx-2:.1f},{my+4:.1f}C{mx+8:.1f},{my+8:.1f} {ex-8:.1f},72 {ex-7:.1f},93L{ex+7:.1f},93C{ex+8:.1f},72 {mx+10:.1f},{my-2:.1f} {mx+2:.1f},{my-4:.1f}Z")
    body += f'<path d="{stream}" fill="url(#fall)"/>'
    body += f'<path d="M{mx+1:.1f},{my:.1f}C{mx+12:.1f},{my+4:.1f} {ex+1:.1f},74 {ex+1:.1f},91" fill="none" stroke="#FFD9AE" stroke-opacity=".45" stroke-width=".9" stroke-linecap="round"/>'
    body += grains(5, (mx + 4, my + 6, ex + 9, 88), 11, .4, .9, ["#F7C08A", "#B5602F", "#6E3418"], (.4, .85))
    # --- the pile, with a lit crest
    pile = f"M{ex-21:.1f},96C{ex-13:.1f},86 {ex+11:.1f},86 {ex+21:.1f},96Z"
    body += f'<path d="{pile}" fill="url(#fall)"/>'
    body += f'<path d="M{ex-14:.1f},92C{ex-7:.1f},88 {ex+4:.1f},88 {ex+10:.1f},91" fill="none" stroke="#FFE3C0" stroke-opacity=".5" stroke-width=".9" stroke-linecap="round"/>'
    body += grains(9, (ex - 19, 90, ex + 19, 95), 12, .4, .95, ["#F2B27A", "#B5602F", "#6E3418"], (.45, .9))
    body += grains(14, (ex - 27, 92, ex - 20, 96), 3, .5, .9, ["#E3965A", "#8C4524"], (.7, 1))
    # --- the bag, tipped
    g = f'translate({TIP["t"][0]},{TIP["t"][1]}) rotate({TIP["rot"]}) scale({TIP["scale"]}) translate({-TIP["pivot"][0]},{-TIP["pivot"][1]})'
    bag = "M38,43C27,51 21,65 24,78C26,88 38,95 54,95C70,95 82,88 84,78C87,65 81,51 70,43Z"
    mouth = "M36,43C36,37 44,34 54,34C64,34 72,37 72,43C72,47 64,49 54,49C44,49 36,47 36,43Z"
    inner = f'<path d="{bag}" fill="url(#bag)"/><path d="{bag}" fill="url(#sheen)"/>'
    inner += f'<path d="{mouth}" fill="url(#top)"/>' + grains(3, (40, 36, 68, 46), 9, .5, 1.0, ["#F7C08A", "#8C4524", "#6E3418"])
    inner += f'<path d="{frill(54, 41, 18.5, 7.6)}" fill="none" stroke="{p["lip"]}" stroke-width="2.6" stroke-linejoin="round"/>'
    inner += f'<path d="M34,52C42,56 66,56 74,52L75,56.5C66,61 42,61 33,56.5Z" fill="url(#band)"/>'
    inner += f'<path d="M33,52C42,57 66,57 75,52" fill="none" stroke="#FFFFFF" stroke-opacity=".55" stroke-width=".9"/>'
    body += f'<g transform="{g}">{inner}</g>'
    # the sparkle patch stays upright: it is the mark, not the cloth
    px, py = place(52, 77)
    body += f'<circle cx="{px:.1f}" cy="{py:.1f}" r="7.4" fill="{p["patch"]}" fill-opacity=".16"/><path d="{sparkle(round(px,1),round(py,1),4.6)}" fill="{p["patch"]}" fill-opacity=".92"/>'
    return defs, body

def svg(p):
    defs, body = icon(p)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><defs>{defs}'
            f'<clipPath id="m"><path d="{squircle()}"/></clipPath></defs><g clip-path="url(#m)">{body}</g></svg>')

if __name__ == "__main__":
    pngs = []
    for name, p in PALETTES.items():
        path = os.path.join(OUT, f"bag-{name}.svg"); open(path, "w").write(svg(p))
        png = path.replace(".svg", ".png"); subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", path, "-o", png], check=True); pngs.append(png)
    ims = [Image.open(x).convert("RGBA") for x in pngs]
    sheet = Image.new("RGB", (512 * 3 + 80, 592), (236, 236, 242))
    for i, im in enumerate(ims): sheet.paste(im, (20 + 532 * i, 40), im)
    sheet.save(os.path.join(OUT, "preview-bag.png")); print("ok")
