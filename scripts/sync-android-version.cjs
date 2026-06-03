// Synchronise la version de app.json vers android/app/build.gradle.
// Le build local (`./gradlew assembleRelease`) lit le versionCode/versionName
// statiques de build.gradle, alors que app.json est la source de vérité. Comme
// le flux de build n'exécute pas `expo prebuild` (qui réécrirait les réglages
// gradle.properties allégés), on injecte ici les versions après chaque bump.
// android/ étant gitignoré, cette modif reste locale (non commitée) — exactement
// ce qu'il faut pour le build/sideload local. Sans android/ (avant prebuild),
// le script ne fait rien.

const fs = require('fs');
const path = require('path');

const gradlePath = path.join(__dirname, '..', 'android', 'app', 'build.gradle');
if (!fs.existsSync(gradlePath)) {
  console.log('sync-android-version : android/ absent (prebuild non lancé) — ignoré.');
  process.exit(0);
}

const expo = require('../app.json').expo;
const versionName = expo.version;
const versionCode = expo.android.versionCode;

let gradle = fs.readFileSync(gradlePath, 'utf8');
gradle = gradle.replace(/versionCode\s+\d+/, `versionCode ${versionCode}`);
gradle = gradle.replace(/versionName\s+"[^"]*"/, `versionName "${versionName}"`);
fs.writeFileSync(gradlePath, gradle);

console.log(`sync-android-version : build.gradle → versionName "${versionName}", versionCode ${versionCode}.`);
