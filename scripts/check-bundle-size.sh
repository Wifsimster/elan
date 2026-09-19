#!/usr/bin/env bash
# Mesure la taille de téléchargement de l'AAB par ABI via bundletool et échoue
# si l'arm64 dépasse le seuil (objectif du portage natif : < 15 Mo).
#
# Usage : ./scripts/check-bundle-size.sh <app.aab> [seuil_Mo=15]
# Sortie : 0 = sous le seuil · 1 = dépassement · 2 = usage/environnement
set -euo pipefail

AAB="${1:?usage: check-bundle-size.sh <app.aab> [seuil_Mo]}"
LIMIT_MB="${2:-15}"
[ -f "$AAB" ] || { echo "✋ fichier introuvable : $AAB" >&2; exit 2; }
command -v java >/dev/null 2>&1 || { echo "✋ java introuvable" >&2; exit 2; }

BUNDLETOOL_VERSION="${BUNDLETOOL_VERSION:-1.18.1}"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/bundletool"
jar="$cache/bundletool-all-$BUNDLETOOL_VERSION.jar"
if [ ! -f "$jar" ]; then
  mkdir -p "$cache"
  curl -fsSL -o "$jar" \
    "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar"
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
# Signature debug automatique : seule la taille nous intéresse.
java -jar "$jar" build-apks --bundle="$AAB" --output="$workdir/app.apks" --overwrite >/dev/null
sizes="$(java -jar "$jar" get-size total --apks="$workdir/app.apks" --dimensions=ABI | tr -d '\r')"

echo "Taille de téléchargement (octets, min–max) par ABI :"
echo "$sizes" | sed 's/^/   /'

# Ligne CSV : ABI,MIN,MAX — on prend le max de l'arm64.
arm64="$(echo "$sizes" | awk -F, '$1=="arm64-v8a" {print $3}')"
[ -n "$arm64" ] || { echo "✋ pas de ligne arm64-v8a dans la sortie de bundletool" >&2; exit 2; }
arm64_mb=$(( arm64 / 1024 / 1024 ))
if [ "$arm64" -gt $(( LIMIT_MB * 1024 * 1024 )) ]; then
  echo "❌ arm64-v8a : ${arm64_mb} Mo > seuil ${LIMIT_MB} Mo"
  exit 1
fi
echo "✅ arm64-v8a : ${arm64_mb} Mo ≤ seuil ${LIMIT_MB} Mo"
