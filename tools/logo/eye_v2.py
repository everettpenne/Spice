#!/usr/bin/env python3
"""A second pass on the eye-gradient proposal, applying a design critique:
  1. it read as a dome/cap and, with the dune tail, as the letter Q  -> a real almond (pointed corners), an iris and a slit pupil with a highlight
  2. the wavy line read as water, not a dune                        -> an asymmetric crest: long gentle windward slope, one sharp crest, steep slip face; a faint second ridge
  3. three loud gradients competed                                  -> the eye is blue only (blue-within-blue); orange is spent on the dune alone
  4. the mark sat high and wide (80% of the width)                  -> centred, ~66% wide, inside the adaptive-icon safe zone
  5. the dune stroke ended in fat round blobs                       -> the crest tapers away at both ends
    python3 tools/logo/eye_v2.py      # docs/logo/eye-v2.svg/png and compare-eye-v2.png"""
import os, subprocess, math
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "logo")

def tapered(crest, w0=2.0, w1=17.0, steps=60):
    """A stroke that swells from nothing, peaks about 60% along and tapers away: a filled outline built from the centre line's cubic segments."""
    pts = []
    for seg in crest:
        for i in range(steps // len(crest)):
            t = i / (steps // len(crest)); u = 1 - t
            x = u**3*seg[0][0] + 3*u*u*t*seg[1][0] + 3*u*t*t*seg[2][0] + t**3*seg[3][0]
            y = u**3*seg[0][1] + 3*u*u*t*seg[1][1] + 3*u*t*t*seg[2][1] + t**3*seg[3][1]
            pts.append((x, y))
    pts.append(crest[-1][3])
    left, right = [], []
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        a = pts[max(i-1, 0)]; b = pts[min(i+1, n-1)]
        dx, dy = b[0]-a[0], b[1]-a[1]; L = math.hypot(dx, dy) or 1
        nx, ny = -dy/L, dx/L
        k = i / (n - 1); w = w0 + (w1 - w0) * math.sin(math.pi * min(1, k * 1.0)) ** 0.8
        left.append((x + nx*w/2, y + ny*w/2)); right.append((x - nx*w/2, y - ny*w/2))
    return "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in left + right[::-1]) + "Z"

# the dune crest: long gentle slope up from the left to a sharp crest, then the steep slip face down to the right
CREST = [((70, 338), (150, 322), (240, 294), (322, 262)), ((322, 262), (338, 256), (350, 262), (360, 276)), ((360, 276), (384, 308), (420, 326), (452, 330))]
CREST_D = "M70,338C150,322 240,294 322,262C338,256 350,262 360,276C384,308 420,326 452,330"
RIDGE2 = "M120,372C190,358 262,346 330,324C350,318 364,326 376,338C396,358 424,368 452,368"

EYE = "M92,264C150,150 336,138 424,242C352,332 168,344 92,264Z"           # an almond: pointed at both corners; its lower half is behind the dune
IRIS = "M190,230a66,66 0 1,0 132,0a66,66 0 1,0 -132,0Z"
PUPIL = "M256,160C276,176 282,204 282,228C282,258 270,284 256,298C242,284 230,258 230,228C230,204 236,176 256,160Z"   # a slit, as a cat's

def svg(rounded=True):
    clip = '<clipPath id="m"><rect width="512" height="512" rx="122.8"/></clipPath>' if rounded else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512"><defs>'
            '<linearGradient id="bg" x1="0" y1="0" x2="0" y2="512" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#303033"/><stop offset="1" stop-color="#0f0f11"/></linearGradient>'
            '<linearGradient id="eye" x1="150" y1="140" x2="380" y2="340" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#c3ecff"/><stop offset=".55" stop-color="#2fb0ee"/><stop offset="1" stop-color="#2f6fe0"/></linearGradient>'
            '<radialGradient id="iris" cx="256" cy="226" r="70" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#1b86e0"/><stop offset=".7" stop-color="#0b4fae"/><stop offset="1" stop-color="#083a86"/></radialGradient>'
            '<linearGradient id="dune" x1="70" y1="338" x2="452" y2="330" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#ff6a35"/><stop offset="1" stop-color="#ffa030"/></linearGradient>'
            f'<clipPath id="e"><path d="{EYE}"/></clipPath>{clip}</defs>'
            f'<g clip-path="url(#m)"><rect width="512" height="512" fill="url(#bg)"/>'
            f'<path d="{EYE}" fill="url(#eye)"/>'
            f'<g clip-path="url(#e)"><path d="{IRIS}" fill="url(#iris)"/><path d="{PUPIL}" fill="#0c0c0e"/>'
            f'<path d="M226,196a40,40 0 0,1 22,-22" fill="none" stroke="#FFF" stroke-width="9" stroke-linecap="round" stroke-opacity=".9"/></g>'
            f'<path d="{CREST_D}L452,540L70,540Z" fill="url(#bg)"/>'                                  # the dune, in the background's own colour: it hides the eye's lower half
            f'<path d="{RIDGE2}" fill="none" stroke="url(#dune)" stroke-width="7" stroke-linecap="round" stroke-opacity=".45"/>'
            f'<path d="{tapered(CREST)}" fill="url(#dune)"/></g></svg>')

if __name__ == "__main__":
    p = os.path.join(OUT, "eye-v2.svg"); open(p, "w").write(svg())
    subprocess.run(["rsvg-convert", "-w", "512", p, "-o", p.replace(".svg", ".png")], check=True)
    a = Image.open(os.path.join(OUT, "eye-gradient-refined-calm.png")).convert("RGBA"); b = Image.open(p.replace(".svg", ".png")).convert("RGBA")
    sheet = Image.new("RGB", (512 * 2 + 60, 592), (158, 160, 246))
    for i, im in enumerate((a, b)): sheet.paste(im, (20 + 532 * i, 40), im)
    sheet.save(os.path.join(OUT, "compare-eye-v2.png")); print("ok")
