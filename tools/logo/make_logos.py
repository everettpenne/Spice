#!/usr/bin/env python3
"""Draws Spice's Dune-inspired logo concepts as SVG (108x108, the adaptive-icon canvas) and renders previews with rsvg-convert.
    python3 tools/logo/make_logos.py          # writes docs/logo/*.svg and docs/logo/preview.png
The same path data is what the Android vector drawables use."""
import math, os, subprocess

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "logo")
os.makedirs(OUT, exist_ok=True)

DEFS = '''<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="0" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#303033"/><stop offset="1" stop-color="#0F0F11"/></linearGradient>
  <linearGradient id="line" x1="22" y1="26" x2="86" y2="100" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#9BE4FF"/><stop offset=".5" stop-color="#00A4DC"/><stop offset="1" stop-color="#AA5CC3"/></linearGradient>
  <radialGradient id="iris" cx="54" cy="54" r="16" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#7FD6FF"/><stop offset=".55" stop-color="#0A8FD0"/><stop offset="1" stop-color="#06406E"/></radialGradient>
</defs>'''
S = 'stroke="url(#line)" stroke-linecap="round" stroke-linejoin="round"'
INK = "#0C0C0E"

def circle(cx, cy, r):
    return f"M{cx-r:.2f},{cy:.2f}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0Z"

def dot(x, y, w, a=1.0):
    return f'<path d="M{x:.2f},{y:.2f}h0.01" {S} stroke-width="{w}" stroke-opacity="{a}"/>'

def sparkle(cx, cy, r):
    k = r * 0.22
    return f"M{cx},{cy-r}C{cx+k},{cy-k} {cx+k},{cy-k} {cx+r},{cy}C{cx+k},{cy+k} {cx+k},{cy+k} {cx},{cy+r}C{cx-k},{cy+k} {cx-k},{cy+k} {cx-r},{cy}C{cx-k},{cy-k} {cx-k},{cy-k} {cx},{cy-r}Z"

# A: the blue-within-blue eye of the Fremen, over the dune that is its lower lid
def concept_a():
    eye = "M14,52C30,32 78,32 94,52C78,72 30,72 14,52Z"
    ridge1 = "M8,88C26,74 40,84 56,78C72,72 86,84 100,76"
    ridge2 = "M20,98C36,90 48,96 62,92C76,88 88,94 98,91"
    return (f'<path d="{eye}" fill="{INK}" {S} stroke-width="2.6"/>'
            f'<path d="{circle(54,52,15)}" fill="url(#iris)"/>'
            f'<path d="{circle(54,52,15)}" {S} stroke-width="1.4" stroke-opacity=".8"/>'
            f'<path d="{circle(54,52,6.5)}" {S} stroke-width="1" stroke-opacity=".45"/>'
            f'<path d="M47.5,45.5a9,9 0 0,1 7,-3" fill="none" stroke="#E6F7FF" stroke-linecap="round" stroke-width="1.8" stroke-opacity=".9"/>'
            f'<path d="{ridge1}" {S} stroke-width="2.4"/><path d="{ridge2}" {S} stroke-width="1.6" stroke-opacity=".6"/>')

# B: twin moons over three layered dunes (the dunes run past the edges so no stroke shows there)
def crescent(cx, cy, r, tilt=-35):
    """A crescent: the moon's disc with a smaller disc bitten out of it, as a single outline."""
    a0, a1 = math.radians(tilt + 90), math.radians(tilt - 90)
    p0 = (cx + r * math.cos(a0), cy - r * math.sin(a0)); p1 = (cx + r * math.cos(a1), cy - r * math.sin(a1))
    return f"M{p0[0]:.2f},{p0[1]:.2f}A{r},{r} 0 1,1 {p1[0]:.2f},{p1[1]:.2f}A{r*0.72:.2f},{r*0.72:.2f} 0 1,0 {p0[0]:.2f},{p0[1]:.2f}Z"

def concept_b():
    back = "M-6,68C14,56 36,64 56,56C76,48 94,56 114,50L114,114L-6,114Z"
    mid = "M-6,80C16,68 38,78 58,70C78,62 94,72 114,66L114,114L-6,114Z"
    front = "M-6,94C18,82 42,92 64,84C84,77 98,86 114,81L114,114L-6,114Z"
    s = (f'<path d="{crescent(70,30,11)}" fill="#E6F7FF" fill-opacity=".14" {S} stroke-width="2.4"/>'
         f'<path d="{crescent(90,46,6,-30)}" fill="#E6F7FF" fill-opacity=".14" {S} stroke-width="2"/>')
    for d, w, al in ((back, 1.6, .55), (mid, 2.0, .8), (front, 2.6, 1)):
        s += f'<path d="{d}" fill="{INK}" {S} stroke-width="{w}" stroke-opacity="{al}"/>'
    return s

# C: a blow of spice rising from a dune crest in a slow S of grains
def concept_c():
    crest = "M-6,84C16,70 38,80 58,72C78,64 94,76 114,68"
    below = "M14,98C30,90 46,96 62,92C76,88 90,95 98,92"
    s = f'<path d="{crest}" {S} stroke-width="2.6"/><path d="{below}" {S} stroke-width="1.6" stroke-opacity=".55"/>'
    n = 22
    for i in range(n):
        t = i / (n - 1)
        x = 54 + 15 * math.sin(t * 2.6 * math.pi) * (0.35 + 0.65 * t)
        y = 68 - t * 50
        w = 1.4 + 3.4 * (1 - t) ** 1.5
        s += dot(x, y, round(w, 2), round(.45 + .55 * (1 - t * .6), 2))
    s += f'<path d="{sparkle(80,26,8)}" fill="#E6F7FF" fill-opacity=".92" stroke="none"/>'
    return s

CONCEPTS = {"eye": concept_a, "moons": concept_b, "bloom": concept_c}

def svg(body, rounded=True):
    import re
    body = re.sub(r'<path (?![^>]*\sfill=)', '<path fill="none" ', body)      # an outline-only path says so once
    clip = f'<clipPath id="m"><rect width="108" height="108" rx="{108*.24:.1f}"/></clipPath>' if rounded else ""
    g = f'<g clip-path="url(#m)">' if rounded else "<g>"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">{DEFS}{clip}{g}'
            f'<rect width="108" height="108" fill="url(#bg)"/>{body}</g></svg>')

if __name__ == "__main__":
    pngs = []
    for name, fn in CONCEPTS.items():
        path = os.path.join(OUT, f"concept-{name}.svg")
        open(path, "w").write(svg(fn()))
        png = path.replace(".svg", ".png")
        subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", path, "-o", png], check=True)
        pngs.append(png)
    from PIL import Image
    ims = [Image.open(p).convert("RGBA") for p in pngs]
    sheet = Image.new("RGB", (512 * 3 + 80, 592), (158, 160, 246))
    for i, im in enumerate(ims): sheet.paste(im, (20 + 532 * i, 40), im)
    sheet.save(os.path.join(OUT, "preview.png"))
    print("ok")
