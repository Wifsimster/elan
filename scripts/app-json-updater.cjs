// Updater personnalisé pour commit-and-tag-version.
// app.json est la source de vérité des versions Expo :
//   - expo.version       → versionName (ex. "1.2.3"), aligné sur la version semver ;
//   - expo.android.versionCode → entier monotone, +1 à chaque release.
// On édite par remplacement ciblé pour préserver la mise en forme du fichier.

module.exports = {
  /** Lit la version courante (non utilisée ici : app.json est un bumpFile, pas un packageFile). */
  readVersion(contents) {
    return JSON.parse(contents).expo.version;
  },

  /** Écrit la nouvelle version et incrémente le versionCode Android. */
  writeVersion(contents, version) {
    let out = contents.replace(/("version":\s*)"[^"]*"/, `$1"${version}"`);
    out = out.replace(
      /("versionCode":\s*)(\d+)/,
      (_m, prefix, n) => `${prefix}${parseInt(n, 10) + 1}`,
    );
    return out;
  },
};
