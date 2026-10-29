#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="${1:?usage: check-deprecated-edge-to-edge.sh <app.apk|app.aab>}"
[ -f "$ARCHIVE" ] || { echo "✋ fichier introuvable : $ARCHIVE" >&2; exit 2; }
command -v unzip >/dev/null 2>&1 || { echo "✋ unzip introuvable" >&2; exit 2; }

DEXDUMP="${DEXDUMP:-}"
if [ -z "$DEXDUMP" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  DEXDUMP="$(ls -d "$sdk"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -x "${DEXDUMP:-/nonexistent}" ] || { echo "✋ dexdump introuvable (build-tools du SDK)" >&2; exit 2; }

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
unzip -o -q "$ARCHIVE" 'classes*.dex' 'base/dex/classes*.dex' -d "$workdir" 2>/dev/null || true
mapfile -t dexes < <(find "$workdir" -name 'classes*.dex' | sort -V)
[ "${#dexes[@]}" -gt 0 ] || { echo "✋ aucun classes*.dex dans $(basename "$ARCHIVE")" >&2; exit 2; }

FORBIDDEN='Landroid/view/Window;[.](set|get)(StatusBarColor|NavigationBarColor|NavigationBarDividerColor)'
CUTOUT='Landroid/view/WindowManager[$]LayoutParams;[.]layoutInDisplayCutoutMode'

dump="$workdir/dump.txt"
for dex in "${dexes[@]}"; do "$DEXDUMP" -d "$dex"; done > "$dump" 2>/dev/null

report() {
  awk -v pat="$1" '
    /^ *#[0-9]+ +: \(in L/ { cls = $0; sub(/^.*\(in /, "", cls); sub(/\).*$/, "", cls) }
    /^ *name +: / { m = $0; sub(/^ *name +: /, "", m); gsub(/\x27/, "", m) }
    $0 ~ pat { api = $0; sub(/^.*Landroid\/view\//, "android.view.", api); sub(/ .*$/, "", api); print cls "#" m "  →  " api }
  ' "$dump" | sort -u
}

bad="$(report "$FORBIDDEN" || true)"
cut="$(report "$CUTOUT" || true)"

if [ -n "$cut" ]; then
  echo "ℹ️  Écritures de layoutInDisplayCutoutMode (à vérifier manuellement : ALWAYS est acceptable) :"
  echo "$cut" | sed 's/^/   /'
  echo
fi

if [ -n "$bad" ]; then
  echo "❌ API bord à bord obsolètes trouvées dans $(basename "$ARCHIVE") :"
  echo "$bad" | sed 's/^/   /'
  echo
  echo "Utiliser enableEdgeToEdge() + WindowInsetsController (ui/EdgeToEdge.kt)."
  exit 1
fi
echo "✅ Aucune API bord à bord obsolète dans $(basename "$ARCHIVE") (${#dexes[@]} dex analysés)."
