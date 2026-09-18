#!/usr/bin/env node
// Génère les vector drawables Android des icônes Material Design Icons (MDI)
// utilisées par Élan, plus l'objet Kotlin `MdiIcons` qui les expose.
//
// Pourquoi des drawables plutôt que la police MDI : ~150 Ko pour ~120 glyphes
// au lieu de 1,2 Mo, et une icône devient une valeur typée (R.drawable.mdi_x)
// vérifiée à la compilation.
//
// Usage : node scripts/gen-mdi-icons.mjs
//   - installe @mdi/svg dans un dossier temporaire (npm, réseau requis),
//   - lit la liste ICONS ci-dessous (noms MDI, séparés par des tirets),
//   - écrit app/src/main/res/drawable/mdi_<nom>.xml (24×24, fillColor noir,
//     teinté à l'usage via `tint`),
//   - écrit app/src/main/java/ovh/battistella/elan/ui/icons/MdiIcons.kt.
// Licence des glyphes : Pictogrammers Free License (Apache 2.0) — voir
// docs/LICENSES-ASSETS.md.

import { execSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const DRAWABLE_DIR = join(ROOT, "app/src/main/res/drawable");
const KOTLIN_FILE = join(ROOT, "app/src/main/java/ovh/battistella/elan/ui/icons/MdiIcons.kt");
const MDI_VERSION = "7";

// Inventaire relevé dans l'app Expo (docs/port-spec/02-interface.md §5) +
// quelques icônes de navigation/état utiles au natif.
const ICONS = [
  "account-outline", "alert-circle-outline", "arm-flex", "arm-flex-outline",
  "arrow-collapse-horizontal", "arrow-collapse-vertical", "arrow-down", "arrow-down-bold",
  "arrow-expand", "arrow-expand-horizontal", "arrow-left", "arrow-right", "arrow-up",
  "arrow-up-bold", "autorenew", "bell-outline", "bike", "bike-fast", "bluetooth",
  "bluetooth-off", "bridge", "bug", "calendar-check", "calendar-week", "camera",
  "camera-off-outline", "chart-line", "check", "check-circle", "chevron-double-down",
  "chevron-down", "chevron-right", "clipboard-text-clock-outline", "clock-outline",
  "close", "close-circle", "close-circle-outline", "cloud-download",
  "cloud-download-outline", "cloud-upload", "cloud-upload-outline", "code-json",
  "cog-outline", "database-outline", "delete-forever-outline", "dog", "dumbbell",
  "elevation-rise", "equal", "export-variant", "file-document-outline",
  "file-import-outline", "filter-remove", "fire", "flag-checkered", "flag-outline",
  "foot-print", "format-list-numbered", "gesture-tap", "gymnastics", "hand-wave",
  "head-outline", "heart", "heart-flash", "heart-off-outline", "heart-plus-outline",
  "heart-pulse", "history", "human", "human-handsdown", "information-outline",
  "kettlebell", "lock-outline", "magnify", "magnify-close", "map-marker-distance",
  "map-marker-path", "map-outline", "meditation", "minus", "minus-circle-outline",
  "pause", "play", "plus", "plus-circle-outline", "qrcode-scan", "refresh", "restore",
  "rotate-right", "run", "run-fast", "scale-bathroom", "seat", "share-variant", "sleep",
  "speedometer", "speedometer-medium", "stairs-up", "target", "timer-outline",
  "timer-sand", "trash-can-outline", "trending-up", "trophy", "view-dashboard-outline",
  "view-grid-outline", "walk", "weight", "weight-kilogram", "weight-lifter", "yoga",
  // natif
  "compass-outline", "crosshairs-gps", "eye-outline", "eye-off-outline",
];

function ensureMdi() {
  const dir = join(tmpdir(), "elan-mdi-svg");
  const pkg = join(dir, "node_modules/@mdi/svg/package.json");
  if (!existsSync(pkg)) {
    mkdirSync(dir, { recursive: true });
    execSync(`npm install --prefix "${dir}" --no-audit --no-fund --silent @mdi/svg@${MDI_VERSION}`, {
      stdio: "inherit",
    });
  }
  return join(dir, "node_modules/@mdi/svg");
}

function svgPath(svg) {
  const m = svg.match(/ d="([^"]+)"/);
  if (!m) throw new Error("path introuvable");
  return m[1];
}

function toResName(name) {
  return "mdi_" + name.replace(/-/g, "_");
}

function toKotlinName(name) {
  // arm-flex-outline → ArmFlexOutline
  return name.split("-").map((p) => p[0].toUpperCase() + p.slice(1)).join("");
}

const mdi = ensureMdi();
const meta = JSON.parse(readFileSync(join(mdi, "meta.json"), "utf8"));
const known = new Set(meta.map((m) => m.name));
const unknown = ICONS.filter((n) => !known.has(n));
if (unknown.length) {
  console.error("Icônes MDI inconnues : " + unknown.join(", "));
  process.exit(1);
}

mkdirSync(DRAWABLE_DIR, { recursive: true });
mkdirSync(dirname(KOTLIN_FILE), { recursive: true });

const sorted = [...new Set(ICONS)].sort();
for (const name of sorted) {
  const svg = readFileSync(join(mdi, "svg", `${name}.svg`), "utf8");
  const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- MDI « ${name} » — généré par scripts/gen-mdi-icons.mjs, ne pas éditer. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?android:attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="${svgPath(svg)}" />
</vector>
`;
  writeFileSync(join(DRAWABLE_DIR, `${toResName(name)}.xml`), xml);
}

const kotlin = `package ovh.battistella.elan.ui.icons

import androidx.annotation.DrawableRes
import ovh.battistella.elan.R

/**
 * Icônes Material Design Icons (MDI) disponibles dans l'app, sous forme de
 * vector drawables générés par scripts/gen-mdi-icons.mjs. Ne pas éditer à la
 * main : ajouter le nom MDI dans le script et le relancer.
 */
object MdiIcons {
${sorted.map((n) => `    @DrawableRes val ${toKotlinName(n)}: Int = R.drawable.${toResName(n)}`).join("\n")}

    /** Résolution par nom MDI (ex. « bike-fast »), pour les catalogues sérialisés. */
    fun byName(name: String): Int? = BY_NAME[name]

    private val BY_NAME: Map<String, Int> = mapOf(
${sorted.map((n) => `        "${n}" to ${toKotlinName(n)},`).join("\n")}
    )
}
`;
writeFileSync(KOTLIN_FILE, kotlin);
console.log(`${sorted.length} icônes écrites dans ${DRAWABLE_DIR} et ${KOTLIN_FILE}`);
