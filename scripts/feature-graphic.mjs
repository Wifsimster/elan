#!/usr/bin/env node
// Génère le feature graphic 1024×500 du Play Store (langue par défaut fr-FR)
// dans l'identité Sillage : fond Encre, logotype « élan » (docs/brand/),
// accroche en Archivo embarquée (app/src/main/res/font/) et trace Volt.
// Rendu par Chromium via Playwright, image opaque (exigence Google).
//
//   npx -y playwright@1 --version   # une fois, si Playwright manque
//   node scripts/feature-graphic.mjs
//
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const font = (f) => readFileSync(resolve(root, 'app/src/main/res/font', f)).toString('base64');
const wordmark = readFileSync(resolve(root, 'docs/brand/elan-wordmark.svg'), 'utf8')
  .replace('fill="#0D0E0B"', 'fill="#F3F4EE"')
  .replace('fill="#5A7F00"', 'fill="#D4F545"');
const OUT = resolve(root, 'fastlane/metadata/android/fr-FR/images/featureGraphic.png');

const html = `<!doctype html><html><head><style>
@font-face { font-family: Archivo; font-weight: 500; src: url(data:font/ttf;base64,${font('archivo_medium.ttf')}); }
@font-face { font-family: ArchivoC; font-weight: 800; font-style: italic; src: url(data:font/ttf;base64,${font('archivo_condensed_extrabold_italic.ttf')}); }
html, body { margin: 0; width: 1024px; height: 500px; background: #0D0E0B; overflow: hidden; }
.wm { position: absolute; left: 88px; top: 118px; width: 420px; }
.wm svg { width: 100%; height: auto; display: block; }
.tag { position: absolute; left: 92px; top: 330px; font: 800 italic 44px/1 ArchivoC; color: #F3F4EE; letter-spacing: -0.5px; }
.sub { position: absolute; left: 94px; top: 392px; font: 500 22px/1 Archivo; color: #A7AB9E; }
.trace { position: absolute; right: 24px; top: 0; }
</style></head><body>
<svg class="trace" width="560" height="500" viewBox="0 0 560 500">
  <defs><linearGradient id="t" x1="0" y1="1" x2="1" y2="0">
    <stop offset="0" stop-color="#D4F545" stop-opacity="0"/><stop offset=".55" stop-color="#D4F545" stop-opacity=".55"/><stop offset="1" stop-color="#D4F545"/></linearGradient></defs>
  <path d="M40 470 C 160 420, 150 330, 250 300 S 380 330, 410 230 S 470 110, 520 60" fill="none" stroke="url(#t)" stroke-width="14" stroke-linecap="round"/>
  <circle cx="520" cy="60" r="16" fill="#D4F545"/><circle cx="520" cy="60" r="34" fill="#D4F545" fill-opacity=".14"/>
</svg>
<div class="wm">${wordmark}</div>
<div class="tag">Ton effort, ta trace.</div>
<div class="sub">Vélo · course · marche · muscu — 100 % local</div>
</body></html>`;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1024, height: 500 } });
await page.setContent(html);
await page.evaluate(() => document.fonts.ready);
await page.screenshot({ path: OUT, omitBackground: false });
await browser.close();
console.log(`OK → ${OUT}`);
