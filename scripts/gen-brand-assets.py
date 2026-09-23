#!/usr/bin/env python3
"""Génère les vecteurs de marque Sillage à partir de la police embarquée.

Le logotype « élan » et l'icône « é » sont les contours exacts d'Archivo
Condensed ExtraBold Italic (app/src/main/res/font/), l'accent — le « trait
d'élan » — étant isolé dans son propre calque pour recevoir le Volt.

    pip install fonttools
    python3 scripts/gen-brand-assets.py

Écrit dans app/src/main/res/drawable/ :
  ic_launcher_foreground.xml, ic_launcher_monochrome.xml,
  ic_launcher_background.xml, brand_wordmark.xml, brand_wordmark_accent.xml
et docs/brand/elan-mark.svg, docs/brand/elan-wordmark.svg.
"""
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / "app/src/main/res/font/archivo_condensed_extrabold_italic.ttf"
DRAWABLE = ROOT / "app/src/main/res/drawable"
BRAND = ROOT / "docs/brand"

INK = "#0D0E0B"
PAPER = "#F3F4EE"
VOLT = "#D4F545"
# Un contour dont le bas dépasse cette hauteur (unités de fonte) est l'accent.
ACCENT_MIN_Y = 480

font = TTFont(FONT)
glyphs = font.getGlyphSet()


def contours(name):
    pen = RecordingPen()
    glyphs[name].draw(pen)
    out, cur = [], []
    for op, args in pen.value:
        cur.append((op, args))
        if op in ("closePath", "endPath"):
            out.append(cur)
            cur = []
    return out


def bounds(cs):
    pen = BoundsPen(None)
    for c in cs:
        for op, args in c:
            getattr(pen, op)(*args)
    return pen.bounds


def path(cs, tf):
    pen = SVGPathPen(None)
    for c in cs:
        for op, args in c:
            getattr(pen, op)(*[tf(x, y) for x, y in args])
    return pen.getCommands()


def r2(v):
    return float("%.2f" % v)


def split_accent(cs):
    accent = [c for c in cs if bounds([c])[1] > ACCENT_MIN_Y]
    return [c for c in cs if c not in accent], accent


# --- Icône « é » : 58 unités de haut, centrée dans le carré de 108 dp.
e_body, e_acc = split_accent(contours("eacute"))
x0, y0, x1, y1 = bounds(e_body + e_acc)
scale = 58 / (y1 - y0)
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
mark_tf = lambda x, y: (r2(54 + (x - cx) * scale), r2(54 - (y - cy) * scale))
mark = {"body": path(e_body, mark_tf), "acc": path(e_acc, mark_tf)}

# --- Logotype « élan ».
xoff, w_body, w_acc = 0, [], []
for g in ["eacute", "l", "a", "n"]:
    for c in contours(g):
        shifted = [(op, [(x + xoff, y) for x, y in args]) for op, args in c]
        (w_acc if g == "eacute" and bounds([c])[1] > ACCENT_MIN_Y else w_body).append(shifted)
    xoff += glyphs[g].width
X0, Y0, X1, Y1 = bounds(w_body + w_acc)
word_tf = lambda x, y: (r2(x - X0), r2(Y1 - y))
word = {"body": path(w_body, word_tf), "acc": path(w_acc, word_tf), "w": r2(X1 - X0), "h": r2(Y1 - Y0)}


def vector(comment, w, h, vw, vh, paths):
    body = "".join(
        f'    <path\n        android:fillColor="{color}"\n        android:pathData="{d}" />\n' for color, d in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment} -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{w}dp"\n    android:height="{h}dp"\n'
        f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n' + body + "</vector>\n"
    )


def argb(hex6):
    return "#FF" + hex6.lstrip("#").upper()


GEN = "Généré par scripts/gen-brand-assets.py — ne pas éditer à la main."
(DRAWABLE / "ic_launcher_foreground.xml").write_text(vector(
    "Avant-plan de l'icône adaptative Sillage : le « é » du logotype, corps en\n"
    f"     papier {PAPER} et accent — le trait d'élan — en Volt {VOLT}.\n     {GEN}",
    108, 108, 108, 108, [(argb(PAPER), mark["body"]), (argb(VOLT), mark["acc"])]))
(DRAWABLE / "ic_launcher_monochrome.xml").write_text(vector(
    f"Icône monochrome (thèmes d'icônes Android 13+), teintée par le système.\n     {GEN}",
    108, 108, 108, 108, [("#FFFFFFFF", mark["body"]), ("#FFFFFFFF", mark["acc"])]))
(DRAWABLE / "ic_launcher_background.xml").write_text(vector(
    f"Fond de l'icône adaptative Sillage : aplat Encre {INK}.\n     {GEN}",
    108, 108, 108, 108, [(argb(INK), "M0,0 H108 V108 H0 Z")]))
ww = r2(word["w"] / word["h"] * 32)
(DRAWABLE / "brand_wordmark.xml").write_text(vector(
    f"Logotype « élan » (lettres, sans l'accent), teinté à l'usage (ElanWordmark).\n     {GEN}",
    ww, 32, word["w"], word["h"], [("#FF000000", word["body"])]))
(DRAWABLE / "brand_wordmark_accent.xml").write_text(vector(
    f"Accent du logotype — le trait d'élan —, même repère que brand_wordmark.\n     {GEN}",
    ww, 32, word["w"], word["h"], [("#FF000000", word["acc"])]))

BRAND.mkdir(parents=True, exist_ok=True)
(BRAND / "elan-mark.svg").write_text(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
    f'<rect width="108" height="108" rx="24" fill="{INK}"/>'
    f'<path d="{mark["body"]}" fill="{PAPER}"/><path d="{mark["acc"]}" fill="{VOLT}"/></svg>\n')
(BRAND / "elan-wordmark.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {word["w"]} {word["h"]}">'
    f'<path d="{word["body"]}" fill="{INK}"/><path d="{word["acc"]}" fill="#5A7F00"/></svg>\n')
print("Vecteurs de marque régénérés.")
