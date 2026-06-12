// Config plugin Expo — intégration Android Health Connect, ré-appliquée à
// chaque `expo prebuild` (android/ est régénéré et gitignoré).
//
// Trois responsabilités :
//   1. MainActivity.kt : enregistrer le HealthConnectPermissionDelegate dans
//      onCreate — sans lui, requestPermission() crashe avec « lateinit property
//      requestPermission has not been initialized » (le launcher de contrat de
//      permissions doit être créé avant le démarrage de l'activité).
//   2. Manifeste, Android 14+ : <activity-alias> ViewPermissionUsageActivity
//      pointant vers la MainActivity pour l'écran système « utilisation des
//      permissions » (Health Connect fait partie du framework à partir de 14).
//
// Le plugin de react-native-health-connect (app.plugin.js, déclaré dans
// app.json) ajoute déjà l'intent-filter ACTION_SHOW_PERMISSIONS_RATIONALE sur
// la MainActivity (écran « pourquoi ces permissions », exigé jusqu'à Android 13).
// Les <uses-permission> android.permission.health.WRITE_* sont déclarées via
// android.permissions dans app.json (mécanisme Expo standard).

const { withAndroidManifest, withMainActivity } = require('@expo/config-plugins');
const { mergeContents } = require('@expo/config-plugins/build/utils/generateCode');

const ALIAS_NAME = 'ViewPermissionUsageActivity';

const withPermissionDelegate = (config) =>
  withMainActivity(config, (cfg) => {
    if (cfg.modResults.language !== 'kt') {
      throw new Error('withHealthConnect : MainActivity Java non gérée (Kotlin attendu).');
    }
    let src = cfg.modResults.contents;
    // mergeContents pose des marqueurs @generated → idempotent au re-prebuild.
    src = mergeContents({
      src,
      newSrc: 'import dev.matinzd.healthconnect.permissions.HealthConnectPermissionDelegate',
      anchor: /import expo\.modules\.ReactActivityDelegateWrapper/,
      offset: 1,
      tag: 'health-connect-import',
      comment: '//',
    }).contents;
    src = mergeContents({
      src,
      newSrc: '    HealthConnectPermissionDelegate.setPermissionDelegate(this)',
      anchor: /super\.onCreate\(null\)/,
      offset: 1,
      tag: 'health-connect-delegate',
      comment: '//',
    }).contents;
    cfg.modResults.contents = src;
    return cfg;
  });

const withPermissionUsageAlias = (config) =>
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

const withHealthConnect = (config) => withPermissionUsageAlias(withPermissionDelegate(config));

module.exports = withHealthConnect;
