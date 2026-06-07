#!/usr/bin/env bash
# Génère le feature graphic 1024×500 du Play Store (langue par défaut fr-FR).
# Sortie sans canal alpha (exigence Google). Nécessite ImageMagick (convert)
# avec les délégués pangocairo + freetype.
#
#   bash scripts/feature-graphic.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."

W=1024; H=500
OUT="fastlane/metadata/android/fr-FR/images/featureGraphic.png"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$(dirname "$OUT")"

# Logo Élan → forme (alpha) remplie du dégradé accent #6478FF → #7A3BFF.
# Le renderer SVG interne d'ImageMagick n'applique pas le dégradé du SVG, on
# réutilise donc seulement la forme comme masque.
convert -background none assets/images/elan-logo.svg -resize 300x300 PNG32:"$TMP/logo.png"
convert "$TMP/logo.png" -alpha extract "$TMP/mask.png"
SZ="$(identify -format "%wx%h" "$TMP/logo.png")"
convert -size "$SZ" gradient:'#6478FF'-'#7A3BFF' "$TMP/grad.png"
convert "$TMP/grad.png" "$TMP/mask.png" -alpha off -compose CopyOpacity -composite PNG32:"$TMP/logo_col.png"

# Fond sombre PULSE + halo accent doux (extérieur noir pur → pas de raccord visible).
convert -size "${W}x${H}" gradient:'#161A24'-'#0A0C10' "$TMP/bg.png"
convert -size 900x900 radial-gradient:'#33268A'-'#000000' -blur 0x40 "$TMP/glow.png"
convert "$TMP/bg.png" \( "$TMP/glow.png" \) -gravity West -geometry -160+0 -compose Screen -composite "$TMP/bg2.png"

# Wordmark + tagline + trait d'accent.
convert -background none -define pango:align=left \
  pango:'<span font="DejaVu Sans Bold 116" foreground="#F4F7FB" letter_spacing="-2000">Élan</span>' "$TMP/title.png"
convert -background none -define pango:align=left \
  pango:'<span font="DejaVu Sans 28" foreground="#9AA3B0">Vélo · Musculation · 100% local</span>' "$TMP/tag.png"
TW="$(identify -format "%w" "$TMP/title.png")"
convert -size "${TW}x6" gradient:'#6478FF'-'#7A3BFF' "$TMP/rule.png"

convert "$TMP/bg2.png" \
  \( "$TMP/logo_col.png" \) -gravity West     -geometry +110+0   -composite \
  \( "$TMP/title.png"    \) -gravity NorthWest -geometry +426+170 -composite \
  \( "$TMP/rule.png"     \) -gravity NorthWest -geometry +432+312 -composite \
  \( "$TMP/tag.png"      \) -gravity NorthWest -geometry +432+338 -composite \
  -alpha remove -alpha off -background '#0A0C10' -flatten \
  PNG24:"$OUT"

identify -format "OK → %f : %wx%h %[colorspace] alpha:%A\n" "$OUT"
