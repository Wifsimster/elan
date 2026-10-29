const { withAndroidManifest, withMainActivity } = require('@expo/config-plugins');
const { mergeContents } = require('@expo/config-plugins/build/utils/generateCode');

const ALIAS_NAME = 'ViewPermissionUsageActivity';

const withPermissionDelegate = (config) =>
  withMainActivity(config, (cfg) => {
    if (cfg.modResults.language !== 'kt') {
      throw new Error('withHealthConnect : MainActivity Java non gérée (Kotlin attendu).');
    }
    let src = cfg.modResults.contents;
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
