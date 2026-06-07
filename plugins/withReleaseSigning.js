// Config plugin Expo — câble la signature de release du Play Store dans le
// build.gradle régénéré par `expo prebuild`, pour qu'elle survive aux prebuild.
//
// Le plugin n'injecte AUCUN secret : il lit les credentials via des propriétés
// Gradle `ELAN_UPLOAD_*` que tu déclares dans `~/.gradle/gradle.properties`
// (hors du repo, jamais regénéré par prebuild) :
//
//   ELAN_UPLOAD_STORE_FILE=/chemin/absolu/credentials/elan-upload.jks
//   ELAN_UPLOAD_KEY_ALIAS=elan-upload
//   ELAN_UPLOAD_STORE_PASSWORD=...
//   ELAN_UPLOAD_KEY_PASSWORD=...
//
// Sans ces propriétés, le build release retombe sur la clé debug (sideload local).
// Build Play Store : cd android && ./gradlew :app:bundleRelease

const { withAppBuildGradle } = require('@expo/config-plugins');

const DEBUG_SIGNING_BLOCK = `        debug {
            storeFile file('debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }`;

const RELEASE_SIGNING_BLOCK = `
        // Upload key Play Store (Elan) — injecté par plugins/withReleaseSigning.js.
        // Credentials via ~/.gradle/gradle.properties (ELAN_UPLOAD_*).
        release {
            if (project.hasProperty('ELAN_UPLOAD_STORE_FILE')) {
                storeFile file(ELAN_UPLOAD_STORE_FILE)
                storePassword ELAN_UPLOAD_STORE_PASSWORD
                keyAlias ELAN_UPLOAD_KEY_ALIAS
                keyPassword ELAN_UPLOAD_KEY_PASSWORD
            }
        }`;

const RELEASE_BUILDTYPE_DEBUG = `        release {
            // Caution! In production, you need to generate your own keystore file.
            // see https://reactnative.dev/docs/signed-apk-android.
            signingConfig signingConfigs.debug`;

const RELEASE_BUILDTYPE_SIGNED = `        release {
            // Upload key si dispo (build Play Store), sinon debug (sideload local).
            signingConfig project.hasProperty('ELAN_UPLOAD_STORE_FILE') ? signingConfigs.release : signingConfigs.debug`;

function applyReleaseSigning(contents) {
  // Idempotent : déjà appliqué (prebuild ne réexécute qu'une fois, mais on protège).
  if (contents.includes('ELAN_UPLOAD_STORE_FILE')) {
    return contents;
  }
  if (!contents.includes(DEBUG_SIGNING_BLOCK)) {
    throw new Error(
      "withReleaseSigning : bloc signingConfigs.debug introuvable — template Expo modifié ?"
    );
  }
  if (!contents.includes(RELEASE_BUILDTYPE_DEBUG)) {
    throw new Error(
      "withReleaseSigning : bloc buildTypes.release introuvable — template Expo modifié ?"
    );
  }
  return contents
    .replace(DEBUG_SIGNING_BLOCK, DEBUG_SIGNING_BLOCK + '\n' + RELEASE_SIGNING_BLOCK)
    .replace(RELEASE_BUILDTYPE_DEBUG, RELEASE_BUILDTYPE_SIGNED);
}

const withReleaseSigning = (config) => {
  return withAppBuildGradle(config, (cfg) => {
    if (cfg.modResults.language !== 'groovy') {
      throw new Error('withReleaseSigning : build.gradle non-groovy non supporté.');
    }
    cfg.modResults.contents = applyReleaseSigning(cfg.modResults.contents);
    return cfg;
  });
};

module.exports = withReleaseSigning;
