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
