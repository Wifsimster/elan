// Config plugin Expo — durcissement de sécurité Android, ré-appliqué à chaque
// `expo prebuild` (le dossier android/ est régénéré et gitignoré, donc on ne
// peut pas éditer le manifeste à la main de façon durable).
//
//   1. android:allowBackup="false" sur <application> — la base SQLite locale
//      (tracés GPS, fréquence cardiaque, et la clé secrète S3 de sauvegarde)
//      ne doit pas pouvoir être extraite via `adb backup` / l'auto-backup cloud
//      Android puis restaurée sur un autre appareil.
//   2. Retrait de SYSTEM_ALERT_WINDOW (« dessiner par-dessus les autres apps »),
//      tirée transitivement (overlay dev/redbox RN) : inutile en production,
//      signalée par la revue Play, surface d'attaque superflue.
//   3. R8 en release : minification + obfuscation + shrink des ressources. La
//      couche native serait sinon livrée non obfusquée (le JS est déjà du
//      bytecode Hermes). À vérifier par un build release avant publication.

const { withAndroidManifest, withGradleProperties } = require('@expo/config-plugins');

const SYSTEM_ALERT_WINDOW = 'android.permission.SYSTEM_ALERT_WINDOW';

// Composants Firebase Cloud Messaging / Google datatransport tirés
// transitivement par expo-notifications. L'app n'utilise QUE des rappels locaux
// (AlarmManager, cf. lib/notifications.ts) — aucun jeton push, aucun envoi. On
// les retire du manifeste fusionné pour coller à la posture « aucun réseau /
// aucun tiers / aucune télémétrie » (formulaire Sécurité Play) et empêcher
// l'auto-init Firebase au démarrage. Le chemin de notification locale est intact.
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

// Permissions Bluetooth héritées (injectées par react-native-ble-plx) : l'app
// utilise le modèle moderne BLUETOOTH_SCAN/CONNECT dès Android 12 (SDK 31), donc
// on borne ces legacy à Android 11- (maxSdkVersion=30).
const CAP_SDK30_PERMISSIONS = ['android.permission.BLUETOOTH', 'android.permission.BLUETOOTH_ADMIN'];

/** Ajoute (ou marque) une directive `tools:node="remove"` pour un composant nommé. */
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

    // Namespace `tools` (présent dans le template, mais on le garantit).
    manifest.$ = manifest.$ || {};
    manifest.$['xmlns:tools'] = 'http://schemas.android.com/tools';

    // 1. allowBackup = false (on écrit l'attribut du manifeste principal, prioritaire).
    const application = manifest.application && manifest.application[0];
    if (application) {
      application.$ = application.$ || {};
      application.$['android:allowBackup'] = 'false';

      // 3. Retrait des composants FCM / datatransport (cf. constantes ci-dessus).
      application.service = REMOVE_SERVICES.reduce(markRemove, application.service || []);
      application.receiver = REMOVE_RECEIVERS.reduce(markRemove, application.receiver || []);
      application.provider = REMOVE_PROVIDERS.reduce(markRemove, application.provider || []);
    }

    // 2. Retrait de SYSTEM_ALERT_WINDOW du manifeste fusionné (tools:node="remove").
    manifest['uses-permission'] = manifest['uses-permission'] || [];
    const perms = manifest['uses-permission'];
    const existing = perms.find((p) => p.$ && p.$['android:name'] === SYSTEM_ALERT_WINDOW);
    if (existing) {
      existing.$['tools:node'] = 'remove';
    } else {
      perms.push({ $: { 'android:name': SYSTEM_ALERT_WINDOW, 'tools:node': 'remove' } });
    }

    // 4. Bornage des permissions Bluetooth héritées à maxSdkVersion=30.
    for (const name of CAP_SDK30_PERMISSIONS) {
      const node = perms.find((p) => p.$ && p.$['android:name'] === name);
      if (node) {
        node.$['android:maxSdkVersion'] = '30';
        node.$['tools:replace'] = node.$['tools:replace']
          ? `${node.$['tools:replace']},android:maxSdkVersion`
          : 'android:maxSdkVersion';
      }
    }

    return cfg;
  });

const withReleaseR8 = (config) =>
  withGradleProperties(config, (cfg) => {
    // 3. R8 release (lu par android/app/build.gradle via findProperty).
    setGradleProperty(cfg.modResults, 'android.enableMinifyInReleaseBuilds', 'true');
    setGradleProperty(cfg.modResults, 'android.enableShrinkResourcesInReleaseBuilds', 'true');
    return cfg;
  });

const withAndroidSecurity = (config) => withReleaseR8(withManifestHardening(config));

module.exports = withAndroidSecurity;
