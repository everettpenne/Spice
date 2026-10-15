#!/usr/bin/env python3
"""Spice logos in the 'peeking character' style: a near-black rounded square with a gradient-outline character looking in from the corner and a few
white details, and a bold solid-black logo with a wordmark. Two ideas: a blue eye over a dune, and a bag of spice with cinnamon drifting out like whiskers.
    python3 tools/logo/make_peek_logos.py   # docs/logo/peek-*.svg/png and preview-peek-eye.png, preview-peek-bag.png"""
import math, os, subprocess
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "docs", "logo")
INTER = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font", "inter_variable.ttf")
INK = "#0C0C0E"
SPICE = ("#EDA469", "#A5592C")

COLORWAYS = {   # gradient stops for the outline: light, mid, deep
    "blue": ("#BFEAFF", "#1FAEEA", "#3B63DB"),
    "white": ("#FFFFFF", "#D4D4D8", "#8E8E93"),
    "violet": ("#DAD0FF", "#9B83FF", "#6A4BE8"),
}

def defs(cw):
    a, b, c = COLORWAYS[cw]
    return (f'<linearGradient id="bg" x1="0" y1="0" x2="0" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#303033"/><stop offset="1" stop-color="#0F0F11"/></linearGradient>'
            f'<linearGradient id="ln" x1="46" y1="30" x2="104" y2="104" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{a}"/><stop offset=".5" stop-color="{b}"/><stop offset="1" stop-color="{c}"/></linearGradient>'
            f'<linearGradient id="sp" x1="0" y1="40" x2="0" y2="66" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{SPICE[0]}"/><stop offset="1" stop-color="{SPICE[1]}"/></linearGradient>'
            f'<radialGradient id="iris" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#7FD3FF"/><stop offset=".6" stop-color="#1B93E0"/><stop offset="1" stop-color="#0B4FA8"/></radialGradient>')

L = 'stroke="url(#ln)" stroke-linecap="round" stroke-linejoin="round"'

def circ(cx, cy, r): return f"M{cx-r:.2f},{cy:.2f}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0Z"
def spark(cx, cy, r):
    k = r * .2
    return f"M{cx},{cy-r}C{cx+k},{cy-k} {cx+k},{cy-k} {cx+r},{cy}C{cx+k},{cy+k} {cx+k},{cy+k} {cx},{cy+r}C{cx-k},{cy+k} {cx-k},{cy+k} {cx-r},{cy}C{cx-k},{cy-k} {cx-k},{cy-k} {cx},{cy-r}Z"

# ---------------------------------------------------------------- icons: a character looking in from the bottom right
def eye_icon():
    """A blue eye lifting over a dune crest, cut off by the right edge; ripples in the sand fan out to the left like whiskers."""
    lid = "M60,66C66,46 96,40 116,58"
    crest = "M26,88C46,76 64,82 84,72C98,65 108,68 116,62"
    s = f'<path d="{lid}L116,70Z" fill="{INK}"/>'                                       # the eye's white is just the dark of the icon: only the lid is drawn
    s += f'<clipPath id="lidc"><path d="{lid}L116,80L60,80Z"/></clipPath><g clip-path="url(#lidc)"><path d="{circ(88,62,12.5)}" fill="url(#iris)"/>'
    s += f'<path d="M80.5,56.5a9,9 0 0,1 6.5,-4" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/></g>'
    s += f'<path d="{crest}L116,116L26,116Z" fill="{INK}"/>'
    s += f'<path d="{lid}" fill="none" {L} stroke-width="3"/><path d="{crest}" fill="none" {L} stroke-width="3"/>'
    for d, w in (("M34,87C26,86 18,87 10,91", 2.4), ("M38,93C30,95 22,98 16,103", 2.2), ("M46,98C40,102 34,105 28,109", 2.0)):
        s += f'<path d="{d}" fill="none" {L} stroke-width="{w}"/>'
    return s

def bag_icon():
    """A round sack looking in over the bottom-right corner: cinnamon heaped in its gathered mouth, dotted cinnamon trails off to the left like whiskers."""
    body = "M42,118C36,98 48,84 66,77C70,75 71,72 71,68L105,68C105,72 108,75 114,78L120,82L120,118Z"
    s = f'<path d="{body}" fill="{INK}"/>'
    s += f'<path d="M42,118C36,98 48,84 66,77C70,75 71,72 71,68" fill="none" {L} stroke-width="3.2"/>'
    s += f'<path d="M72,67C72,57 80,50 88,50C96,50 104,57 104,67Z" fill="url(#sp)"/>'
    s += f'<path d="M71,67C73,73 80,75 88,75C96,75 103,73 105,67" fill="none" {L} stroke-width="3.2"/>'
    s += f'<path d="M68,82C80,87 96,87 108,82" fill="none" {L} stroke-width="2.4" stroke-opacity=".8"/>'
    s += f'<path d="{spark(82,99,6.5)}" fill="#FFFFFF"/>'
    for d, w in (("M68,60C58,55 48,55 38,59", 2.6), ("M68,66C58,66 48,69 40,75", 2.4), ("M70,54C62,48 54,44 46,42", 2.2)):
        s += f'<path d="{d}" fill="none" stroke="#EDA469" stroke-width="{w}" stroke-linecap="round" stroke-dasharray="0.01 4.6"/>'
    return s

def icon_svg(fn, cw):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><defs>{defs(cw)}'
            f'<clipPath id="m"><rect width="108" height="108" rx="24"/></clipPath></defs><g clip-path="url(#m)"><rect width="108" height="108" fill="url(#bg)"/>{fn()}</g></svg>')

# ---------------------------------------------------------------- logos: a bold solid glyph and a wordmark
def eye_glyph():
    """A solid black almond eye with a blue iris, cut off by a straight edge on the right (as the cat is), ripples fanning out to the left."""
    eye = "M4,50C18,20 66,20 80,50C66,80 18,80 4,50Z"
    s = f'<clipPath id="wall"><rect x="-30" y="0" width="98" height="100"/></clipPath><g clip-path="url(#wall)">'
    s += f'<path d="{eye}" fill="#000"/>'
    s += f'<clipPath id="almond"><path d="{eye}"/></clipPath><g clip-path="url(#almond)"><path d="{circ(40,50,17)}" fill="url(#iris)"/>'
    s += f'<path d="M30,44a12,12 0 0,1 9,-6.5" fill="none" stroke="#FFF" stroke-width="2.6" stroke-linecap="round"/></g></g>'
    for d in ("M2,52C-8,52 -16,54 -24,58", "M4,58C-5,60 -12,64 -18,69", "M8,64C2,68 -3,72 -7,77"):
        s += f'<path d="{d}" fill="none" stroke="#000" stroke-width="3.2" stroke-linecap="round"/>'
    return s, (-28, 14, 96, 70)

def bag_glyph():
    """A solid black sack, cut flat at the bottom like the cat, cinnamon heaped in its mouth and trailing off in dots."""
    g = '<path d="M30,52C26,58 8,62 5,78C4,84 5,92 5,92L86,92C86,84 88,76 84,70C80,62 66,58 62,52Z" fill="#000"/>'
    g += f'<defs><linearGradient id="spg" x1="0" y1="26" x2="0" y2="52" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{SPICE[0]}"/><stop offset="1" stop-color="{SPICE[1]}"/></linearGradient></defs>'
    g += f'<path d="M29,52C29,42 37,35 46,35C55,35 63,42 63,52Z" fill="url(#spg)"/>'
    g += '<path d="M28,52C32,58 60,58 64,52" fill="none" stroke="#000" stroke-width="4.6" stroke-linecap="round"/>'
    g += '<path d="M22,66C34,72 58,72 70,66" fill="none" stroke="#FFF" stroke-width="2.6" stroke-linecap="round"/>'
    g += f'<path d="{spark(40,81,7)}" fill="#FFF"/>'
    for d, w in (("M26,45C16,40 7,40 -3,44", 3.0), ("M26,51C16,52 7,55 0,61", 2.8), ("M28,39C22,32 15,28 7,26", 2.6)):
        g += f'<path d="{d}" fill="none" stroke="#E8985C" stroke-width="{w}" stroke-linecap="round" stroke-dasharray="0.01 5.2"/>'
    return g, (-8, 18, 100, 78)

def lockup(glyph_fn, name):
    body, (x0, y0, w, h) = glyph_fn()
    gh = 300; sc = gh / h; gw = int(w * sc)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x0} {y0} {w} {h}" width="{gw}" height="{gh}"><defs>{defs("blue")}</defs>{body}</svg>')
    p = os.path.join(OUT, f"peek-{name}-glyph.svg"); open(p, "w").write(svg)
    subprocess.run(["rsvg-convert", "-h", str(gh), p, "-o", p.replace(".svg", ".png")], check=True)
    glyph = Image.open(p.replace(".svg", ".png")).convert("RGBA")
    font = ImageFont.truetype(INTER, 210); font.set_variation_by_axes([32, 800])
    W, H = 1080, 1080
    bg = Image.new("RGB", (W, H)); px = bg.load()
    for y in range(H):
        for x in range(W):
            t = (x * .35 + y * .65) / (W * .35 + H * .65)
            px[x, y] = (int(150 + 50 * t), int(150 + 55 * t), int(244 + 8 * t))
    d = ImageDraw.Draw(bg)
    tw = d.textlength("Spice", font=font)
    total = glyph.width + 40 + tw; x = (W - total) / 2
    bg.paste(glyph, (int(x), 250), glyph)
    d.text((x + glyph.width + 40, 250 + gh / 2), "Spice", font=font, fill=(0, 0, 0), anchor="lm")
    return bg

if __name__ == "__main__":
    for name, icon, glyph in (("eye", eye_icon, eye_glyph), ("bag", bag_icon, bag_glyph)):
        sheet = lockup(glyph, name); icons = []
        for cw in COLORWAYS:
            p = os.path.join(OUT, f"peek-{name}-{cw}.svg"); open(p, "w").write(icon_svg(icon, cw))
            subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", p, "-o", p.replace(".svg", ".png")], check=True)
            icons.append(Image.open(p.replace(".svg", ".png")).convert("RGBA").resize((256, 256), Image.LANCZOS))
        for i, im in enumerate(icons): sheet.paste(im, (150 + i * 290, 640), im)
        sheet.save(os.path.join(OUT, f"preview-peek-{name}.png"))
    print("ok")
