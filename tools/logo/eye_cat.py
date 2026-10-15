#!/usr/bin/env python3
"""The eye-gradient proposal with the cat's eye: the two half-lenses of the Lurkur cat's eyes (straight inner edge, bulging outer edge, slanted top), scaled up
and set either side of a vertical pupil gap, filled with the proposal's gradient. The dune line is the proposal's, unchanged (path, width and gradient).
The eyes are clipped to the area above the dune line, so nothing can show beneath it.
    python3 tools/logo/eye_cat.py       # docs/logo/eye-cat-{a,b,c}.svg/png and compare-eye-cat.png"""
import os, subprocess
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "logo")
CAT_EYE = "M0,0C-26,2 -54,30 -48,58C-44,76 -26,86 -2,85Q3,42 0,0Z"       # inner edge at x=0, bulging left; mirrored for the right eye
DUNE = "M53.548,315.236C141.058,278.743 202.926,343.345 274.252,332.092C345.579,320.84 389.065,380.779 460.392,369.526"
ABOVE_DUNE = "M-40,315.236L53.548,315.236" + DUNE[DUNE.index("C"):] + "L560,369.526L560,-10L-40,-10Z"   # the dune line, run level off both ends

def place(path, x, y, kx, ky):
    """The cat-eye path moved and scaled in the numbers themselves (not with a transform), so the gradient in canvas coordinates is not scaled with it."""
    import re
    nums = [float(v) for v in re.findall(r"-?\d+\.?\d*", path)]; cmds = re.findall(r"[A-Za-z]", path); out = []; i = 0
    for c in cmds:
        n = {"M": 2, "C": 6, "Q": 4, "Z": 0}[c]
        pts = [(x + nums[i + j] * kx, y + nums[i + j + 1] * ky) for j in range(0, n, 2)]; i += n
        out.append(c + " ".join(f"{px:.2f},{py:.2f}" for px, py in pts))
    return "".join(out)

def svg(gap, kx=2.5, ky=2.5, top=128):
    left, right = 256 - gap / 2, 256 + gap / 2
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512"><defs>'
            '<linearGradient id="bg" x1="0" y1="0" x2="0" y2="512" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#303033"/><stop offset="1" stop-color="#0f0f11"/></linearGradient>'
            '<linearGradient id="eye" x1="168.9" y1="82.3" x2="343.1" y2="429.7" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#9be4ff"/><stop offset=".5" stop-color="#00a4dc"/><stop offset="1" stop-color="#aa5cc3"/></linearGradient>'
            '<linearGradient id="dune" x1="196.4" y1="216.7" x2="317.5" y2="458.2" gradientUnits="userSpaceOnUse"><stop offset=".2552" stop-color="#ff6633"/><stop offset=".4617" stop-color="#00a4dc"/><stop offset=".7679" stop-color="#ff9125"/></linearGradient>'
            f'<clipPath id="above"><path d="{ABOVE_DUNE}"/></clipPath><clipPath id="m"><rect width="512" height="512" rx="122.8"/></clipPath></defs>'
            '<g clip-path="url(#m)"><rect width="512" height="512" fill="url(#bg)"/>'
            f'<g clip-path="url(#above)"><path d="{place(CAT_EYE, left, top, kx, ky)}" fill="url(#eye)"/>'
            f'<path d="{place(CAT_EYE, right, top, -kx, ky)}" fill="url(#eye)"/></g>'
            f'<path d="{DUNE}" fill="none" stroke="url(#dune)" stroke-width="20.859" stroke-linecap="round" stroke-linejoin="round"/></g></svg>')

if __name__ == "__main__":
    ims = []
    # (name, gap between the inner edges, horizontal and vertical scale of the cat's eye); the top is set so the eyes' feet go under the dune line
    for name, gap, kx, ky in (("a", 56, 3.0, 2.1), ("b", 76, 3.2, 2.4), ("c", 50, 2.7, 2.5)):
        p = os.path.join(OUT, f"eye-cat-{name}.svg"); open(p, "w").write(svg(gap, kx, ky, top=345 - 85 * ky))
        subprocess.run(["rsvg-convert", "-w", "512", p, "-o", p.replace(".svg", ".png")], check=True)
        ims.append(Image.open(p.replace(".svg", ".png")).convert("RGBA"))
    sheet = Image.new("RGB", (512 * 3 + 80, 592), (158, 160, 246))
    for i, im in enumerate(ims): sheet.paste(im, (20 + 532 * i, 40), im)
    sheet.save(os.path.join(OUT, "compare-eye-cat.png")); print("ok")
