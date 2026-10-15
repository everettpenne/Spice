#!/usr/bin/env python3
"""Builds Spice's launcher icon from the refined eye-gradient design (tools/logo/refine_eye_gradient.py): Android vector drawables for the adaptive
icon's foreground and its one-colour (themed icon) layer, and docs/icon.png for the README. The 512-unit artwork is moved and scaled into the
108dp adaptive canvas, centred, and sized so the whole mark (the dune line's ends included) sits inside the 66dp safe zone.
    python3 tools/logo/build_icon.py"""
import importlib.util, os, re, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")
spec = importlib.util.spec_from_file_location("refine", os.path.join(HERE, "refine_eye_gradient.py")); refine = importlib.util.module_from_spec(spec); spec.loader.exec_module(refine)

S = 0.135                 # dp per unit of the 512-unit artwork (the full canvas would be 0.211); the mark is ~58dp wide, inside the 66dp safe circle
CX, CY = 256, 260         # the artwork's visual centre, mapped to the middle of the 108dp canvas

def tx(x): return (x - CX) * S + 54
def ty(y): return (y - CY) * S + 54

def transform_path(d):
    """Moves and scales a path made of M, C and Z commands."""
    out = []
    for cmd, args in re.findall(r"([MCZ])([^MCZ]*)", d):
        nums = [float(v) for v in re.findall(r"-?\d+\.?\d*", args)]
        pts = [f"{tx(nums[i]):.2f},{ty(nums[i+1]):.2f}" for i in range(0, len(nums), 2)]
        out.append(cmd + " ".join(pts))
    return "".join(out)

def ellipse(cx, cy, rx, ry):
    k = 0.5522847498
    return (f"M{cx-rx},{cy}C{cx-rx},{cy-ry*k} {cx-rx*k},{cy-ry} {cx},{cy-ry}C{cx+rx*k},{cy-ry} {cx+rx},{cy-ry*k} {cx+rx},{cy}"
            f"C{cx+rx},{cy+ry*k} {cx+rx*k},{cy+ry} {cx},{cy+ry}C{cx-rx*k},{cy+ry} {cx-rx},{cy+ry*k} {cx-rx},{cy}Z")

PUPIL = ellipse(256, 244, 74, 92)
EYE = transform_path(refine.eye_path()) + transform_path(PUPIL)                         # the pupil is a second sub-path: a hole (even-odd)

def eye_raised(dy):
    """The eye with its lower edge lifted off the dune line by dy units, for the one-colour version: in one colour the line would otherwise fuse with the eye."""
    seg1, seg2 = refine.SEG1, refine.SEG2
    lift = lambda seg: tuple((x, y - dy) for x, y in seg)
    refine.SEG1, refine.SEG2 = lift(seg1), lift(seg2)
    try: return refine.eye_path()
    finally: refine.SEG1, refine.SEG2 = seg1, seg2
EYE_MONO = transform_path(eye_raised(19)) + transform_path(PUPIL)
DUNE = transform_path(refine.DUNE)
WIDTH = 20.859 * S

def grad(x1, y1, x2, y2, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return f'<gradient android:startX="{tx(x1):.2f}" android:startY="{ty(y1):.2f}" android:endX="{tx(x2):.2f}" android:endY="{ty(y2):.2f}" android:type="linear">{items}</gradient>'

HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- {note} -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')

foreground = HEAD.format(note="The eye, its pupil cut out (a hole, so the background shows through), over the orange dune line.") + f'''    <path android:pathData="{EYE}" android:fillType="evenOdd">
        <aapt:attr name="android:fillColor">{grad(168.9, 82.3, 343.1, 429.7, [(0, "#FF9BE4FF"), (0.5, "#FF00A4DC"), (1, "#FFAA5CC3")])}</aapt:attr>
    </path>
    <path android:pathData="{DUNE}" android:strokeWidth="{WIDTH:.2f}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000">
        <aapt:attr name="android:strokeColor">{grad(196.4, 216.7, 317.5, 458.2, [(0.2552, "#FFFF6633"), (0.4617, "#FF00A4DC"), (0.7679, "#FFFF9125")])}</aapt:attr>
    </path>
</vector>
'''
monochrome = HEAD.format(note="The same mark in one colour, for Android's themed icons: the eye with its pupil cut out, and the dune line.") + f'''    <path android:pathData="{EYE_MONO}" android:fillType="evenOdd" android:fillColor="#FF000000"/>
    <path android:pathData="{DUNE}" android:strokeWidth="{WIDTH:.2f}" android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeColor="#FF000000" android:fillColor="#00000000"/>
</vector>
'''
open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w").write(foreground)
open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w").write(monochrome)

# docs/icon.png: the same design as an iOS-style rounded square, for the README
docs = os.path.join(HERE, "..", "..", "docs")
subprocess.run(["rsvg-convert", "-w", "512", os.path.join(docs, "logo", "eye-gradient-refined.svg"), "-o", os.path.join(docs, "icon.png")], check=True)
print("ok")
