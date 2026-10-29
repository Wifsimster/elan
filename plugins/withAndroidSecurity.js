const { withAndroidManifest, withGradleProperties } = require('@expo/config-plugins');

const SYSTEM_ALERT_WINDOW = 'android.permission.SYSTEM_ALERT_WINDOW';

const REMOVE_SERVICES = [
  'expo.modules.notifications.service.ExpoFirebaseMessagingService',
  'com.google.firebase.messaging.FirebaseMessagingService',
  'com.google.firebase.components.ComponentDiscoveryService',
  'com.google.android.datatransport.runtime.backends.TransportBackendDiscovery',
  'com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService',
];
const REMOVE_RECEIVERS = [
  'com.google.firebase.iid.FirebaseInstanceIdReceiver',
  'com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver',
];
const REMOVE_PROVIDERS = ['com.google.firebase.provider.FirebaseInitProvider'];

const CAP_SDK30_PERMISSIONS = ['android.permission.BLUETOOTH', 'android.permission.BLUETOOTH_ADMIN'];

function markRemove(list, name) {
  const arr = list || [];
  const existing = arr.find((c) => c.$ && c.$['android:name'] === name);
  if (existing) existing.$['tools:node'] = 'remove';
  else arr.push({ $: { 'android:name': name, 'tools:node': 'remove' } });
  return arr;
}

function setGradleProperty(props, key, value) {
  const existing = props.find((p) => p.type === 'property' && p.key === key);
  if (existing) {
    existing.value = value;
  } else {
    props.push({ type: 'property', key, value });
  }
}

const withManifestHardening = (config) =>
  withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults.manifest;

    manifest.$ = manifest.$ || {};
    manifest.$['xmlns:tools'] = 'http://schemas.android.com/tools';

    const application = manifest.application && manifest.application[0];
    if (application) {
      application.$ = application.$ || {};
      application.$['android:allowBackup'] = 'false';

      application.service = REMOVE_SERVICES.reduce(markRemove, application.service || []);
      application.receiver = REMOVE_RECEIVERS.reduce(markRemove, application.receiver || []);
      application.provider = REMOVE_PROVIDERS.reduce(markRemove, application.provider || []);
    }

    manifest['uses-permission'] = manifest['uses-permission'] || [];
    const perms = manifest['uses-permission'];
    const existing = perms.find((p) => p.$ && p.$['android:name'] === SYSTEM_ALERT_WINDOW);
    if (existing) {
      existing.$['tools:node'] = 'remove';
    } else {
      perms.push({ $: { 'android:name': SYSTEM_ALERT_WINDOW, 'tools:node': 'remove' } });
    }

    const LOCATION_PERMISSIONS = [
      'android.permission.ACCESS_COARSE_LOCATION',
      'android.permission.ACCESS_FINE_LOCATION',
    ];
    manifest['uses-permission-sdk-23'] = (manifest['uses-permission-sdk-23'] || []).filter(
      (p) => !(p.$ && LOCATION_PERMISSIONS.includes(p.$['android:name']))
    );
    for (const name of LOCATION_PERMISSIONS) {
      manifest['uses-permission-sdk-23'].push({ $: { 'android:name': name, 'tools:node': 'remove' } });
    }

    for (const name of CAP_SDK30_PERMISSIONS) {
      const node = perms.find((p) => p.$ && p.$['android:name'] === name);
      if (node) {
        node.$['android:maxSdkVersion'] = '30';
        const raw = node.$['tools:replace']
          ? node.$['tools:replace'].split(',').map((s) => s.trim()).filter(Boolean)
          : [];
        const parts = [...new Set([...raw, 'android:maxSdkVersion'])];
        node.$['tools:replace'] = parts.join(',');
      }
    }

    return cfg;
  });

const withReleaseR8 = (config) =>
  withGradleProperties(config, (cfg) => {
    setGradleProperty(cfg.modResults, 'android.enableMinifyInReleaseBuilds', 'true');
    setGradleProperty(cfg.modResults, 'android.enableShrinkResourcesInReleaseBuilds', 'true');
    return cfg;
  });

const withAndroidSecurity = (config) => withReleaseR8(withManifestHardening(config));

module.exports = withAndroidSecurity;
