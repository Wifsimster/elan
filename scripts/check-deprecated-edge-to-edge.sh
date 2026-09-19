#!/usr/bin/env bash
# Vérifie qu'aucun appel aux API « bord à bord » obsolètes ne subsiste dans le
# dex final (celles que la Play Console signale pour les apps qui ciblent
# Android 15+) : Window.setStatusBarColor / setNavigationBarColor /
# setNavigationBarDividerColor et leurs accesseurs.
#
# Les écritures directes de WindowManager.LayoutParams.layoutInDisplayCutoutMode
# sont listées à titre indicatif (la constante est inlinée, impossible de
# distinguer SHORT_EDGES d'ALWAYS dans le bytecode) mais ne font pas échouer.
#
# Usage : ./scripts/check-deprecated-edge-to-edge.sh <app.apk|app.aab>
# Sortie : 0 = propre · 1 = appel obsolète trouvé · 2 = usage/environnement
set -euo pipefail

ARCHIVE="${1:?usage: check-deprecated-edge-to-edge.sh <app.apk|app.aab>}"
[ -f "$ARCHIVE" ] || { echo "✋ fichier introuvable : $ARCHIVE" >&2; exit 2; }
command -v unzip >/dev/null 2>&1 || { echo "✋ unzip introuvable" >&2; exit 2; }

# dexdump vient des build-tools du SDK ; on prend la version la plus récente.
DEXDUMP="${DEXDUMP:-}"
if [ -z "$DEXDUMP" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  DEXDUMP="$(ls -d "$sdk"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -x "${DEXDUMP:-/nonexistent}" ] || { echo "✋ dexdump introuvable (build-tools du SDK)" >&2; exit 2; }

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
# Un AAB range les dex sous base/dex/, un APK à la racine.
unzip -o -q "$ARCHIVE" 'classes*.dex' 'base/dex/classes*.dex' -d "$workdir" 2>/dev/null || true
mapfile -t dexes < <(find "$workdir" -name 'classes*.dex' | sort -V)
[ "${#dexes[@]}" -gt 0 ] || { echo "✋ aucun classes*.dex dans $(basename "$ARCHIVE")" >&2; exit 2; }

# Signatures interdites : méthodes de android.view.Window.
FORBIDDEN='Landroid/view/Window;[.](set|get)(StatusBarColor|NavigationBarColor|NavigationBarDividerColor)'
CUTOUT='Landroid/view/WindowManager[$]LayoutParams;[.]layoutInDisplayCutoutMode'

dump="$workdir/dump.txt"
for dex in "${dexes[@]}"; do "$DEXDUMP" -d "$dex"; done > "$dump" 2>/dev/null

# dexdump imprime la méthode courante dans une ligne « #N : (in Lcom/…;) »
# puis « name : '…' » ; on remonte pour attribuer chaque appel à sa classe.
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
