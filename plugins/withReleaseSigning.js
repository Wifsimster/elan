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

// Garde-fou : un AAB destiné au Play Store (`bundleRelease`) DOIT être signé avec
// la clé d'upload. Sans les propriétés ELAN_UPLOAD_*, on échoue le build au lieu
// de produire un artefact signé avec la clé debug publique (re-signable, rejeté
// par Play). Les builds APK de sideload (`assembleRelease`) gardent le repli debug.
const RELEASE_GUARD_MARKER = 'ELAN_BUNDLE_RELEASE_UPLOAD_KEY_GUARD';
const RELEASE_GUARD_BLOCK = `
// ${RELEASE_GUARD_MARKER} — garde-fou cle d'upload Play Store (bundleRelease).
gradle.taskGraph.whenReady { graph ->
    def isPlayBundle = graph.allTasks.any { it.path == ':app:bundleRelease' }
    if (isPlayBundle && !project.hasProperty('ELAN_UPLOAD_STORE_FILE')) {
        throw new GradleException(
            "bundleRelease exige la cle d'upload Play Store. Definis ELAN_UPLOAD_STORE_FILE, " +
            "ELAN_UPLOAD_KEY_ALIAS, ELAN_UPLOAD_STORE_PASSWORD et ELAN_UPLOAD_KEY_PASSWORD " +
            "dans ~/.gradle/gradle.properties (cf. plugins/withReleaseSigning.js)."
        )
    }
}
`;

function applyReleaseSigning(contents) {
  let out = contents;

  // Bloc signature — idempotent via le marqueur ELAN_UPLOAD_STORE_FILE.
  if (!out.includes('ELAN_UPLOAD_STORE_FILE')) {
    if (!out.includes(DEBUG_SIGNING_BLOCK)) {
      throw new Error(
        "withReleaseSigning : bloc signingConfigs.debug introuvable — template Expo modifié ?"
      );
    }
    if (!out.includes(RELEASE_BUILDTYPE_DEBUG)) {
      throw new Error(
        "withReleaseSigning : bloc buildTypes.release introuvable — template Expo modifié ?"
      );
    }
    out = out
      .replace(DEBUG_SIGNING_BLOCK, DEBUG_SIGNING_BLOCK + '\n' + RELEASE_SIGNING_BLOCK)
      .replace(RELEASE_BUILDTYPE_DEBUG, RELEASE_BUILDTYPE_SIGNED);
  }

  // Garde-fou bundleRelease — idempotent via son propre marqueur (indépendant du
  // bloc signature, pour qu'il s'ajoute aussi à un build.gradle déjà migré).
  if (!out.includes(RELEASE_GUARD_MARKER)) {
    out = out + '\n' + RELEASE_GUARD_BLOCK;
  }

  return out;
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
