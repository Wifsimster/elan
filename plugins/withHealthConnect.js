// Config plugin Expo — déclarations manifeste pour Android Health Connect,
// ré-appliquées à chaque `expo prebuild` (android/ est régénéré et gitignoré).
//
// Le plugin de react-native-health-connect (app.plugin.js, déclaré dans
// app.json) ajoute déjà l'intent-filter ACTION_SHOW_PERMISSIONS_RATIONALE sur
// la MainActivity (écran « pourquoi ces permissions » exigé jusqu'à Android 13).
// Celui-ci complète pour Android 14+, où Health Connect fait partie du
// framework : un <activity-alias> ViewPermissionUsageActivity qui pointe vers
// la MainActivity pour l'écran « utilisation des permissions » du système.
//
// Les <uses-permission> android.permission.health.WRITE_* sont déclarées via
// android.permissions dans app.json (mécanisme Expo standard).

const { withAndroidManifest } = require('@expo/config-plugins');

const ALIAS_NAME = 'ViewPermissionUsageActivity';

const withHealthConnect = (config) =>
  withAndroidManifest(config, (cfg) => {
    const application = cfg.modResults.manifest.application?.[0];
    if (!application) return cfg;

    // Idempotent : prebuild peut relire un manifeste déjà modifié.
    const aliases = application['activity-alias'] || [];
    if (!aliases.some((a) => a.$ && a.$['android:name'] === ALIAS_NAME)) {
      aliases.push({
        $: {
          'android:name': ALIAS_NAME,
          'android:exported': 'true',
          'android:targetActivity': '.MainActivity',
          'android:permission': 'android.permission.START_VIEW_PERMISSION_USAGE',
        },
        'intent-filter': [
          {
            action: [{ $: { 'android:name': 'android.intent.action.VIEW_PERMISSION_USAGE' } }],
            category: [{ $: { 'android:name': 'android.intent.category.HEALTH_PERMISSIONS' } }],
          },
        ],
      });
    }
    application['activity-alias'] = aliases;

    return cfg;
  });

module.exports = withHealthConnect;
