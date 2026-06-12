// Export opt-in des séances vers Android Health Connect — la base de données
// santé ON-DEVICE d'Android (aucun cloud, aucun compte, dans la ligne du projet).
// Désactivé par défaut ; l'utilisateur l'active dans Réglages, ce qui déclenche
// la demande de permissions Health Connect (écriture seule). À la fin d'une
// séance, `exportSessionToHealthConnect()` écrit l'ExerciseSession (+ distance,
// calories, série FC si dispo) en best-effort : toute erreur est avalée, la
// séance locale reste la source de vérité.
//
// Le module natif (react-native-health-connect) n'existe que sur Android et
// uniquement en development build — il est donc chargé paresseusement via
// `import()` et chaque fonction dégrade proprement (no-op) ailleurs.

import { Platform } from 'react-native';

import { getSetting, setSetting } from '@/lib/db';
import type { ActivityType } from '@/lib/types';
import type { HealthConnectRecord, Permission } from 'react-native-health-connect';

const KEY = 'health_connect';

// Constantes Health Connect (androidx) — recopiées ici pour garder
// `buildHealthRecords` pur et testable sans charger le module natif.
// Voir `ExerciseType` dans react-native-health-connect/constants.
const EXERCISE_TYPE_BIKING = 8;
const EXERCISE_TYPE_STRENGTH_TRAINING = 70;
const SDK_AVAILABLE = 3; // SdkAvailabilityStatus.SDK_AVAILABLE

// Écriture seule : Élan n'a pas besoin de lire les données des autres apps.
const WRITE_PERMISSIONS: Permission[] = [
  { accessType: 'write', recordType: 'ExerciseSession' },
  { accessType: 'write', recordType: 'Distance' },
  { accessType: 'write', recordType: 'ActiveCaloriesBurned' },
  { accessType: 'write', recordType: 'HeartRate' },
];

type HealthConnectModule = typeof import('react-native-health-connect');

let modulePromise: Promise<HealthConnectModule | null> | null = null;

/** Charge le module natif, ou null hors Android / si le module est absent. */
function getModule(): Promise<HealthConnectModule | null> {
  if (Platform.OS !== 'android') return Promise.resolve(null);
  if (!modulePromise) {
    modulePromise = import('react-native-health-connect').catch(() => null);
  }
  return modulePromise;
}

/** Health Connect n'existe que sur Android (8.0+). */
export function isHealthConnectSupported(): boolean {
  return Platform.OS === 'android';
}

/** Opt-in mémorisé dans `settings` — false par défaut, jamais vrai hors Android. */
export async function getHealthConnectEnabled(): Promise<boolean> {
  if (!isHealthConnectSupported()) return false;
  return (await getSetting(KEY)) === '1';
}

export async function disableHealthConnect(): Promise<void> {
  await setSetting(KEY, '');
}

export type EnableResult = 'granted' | 'denied' | 'unavailable';

/**
 * Active l'export : vérifie la disponibilité de Health Connect puis demande les
 * permissions d'écriture (l'UI système ne s'affiche qu'à ce moment-là, jamais
 * avant — c'est le contrat de l'opt-in). N'enregistre l'activation que si
 * toutes les permissions sont accordées.
 */
export async function enableHealthConnect(): Promise<EnableResult> {
  const hc = await getModule();
  if (!hc) return 'unavailable';
  try {
    const status = await hc.getSdkStatus();
    if (status !== SDK_AVAILABLE) return 'unavailable';
    if (!(await hc.initialize())) return 'unavailable';
    const granted = await hc.requestPermission(WRITE_PERMISSIONS);
    const ok = WRITE_PERMISSIONS.every((p) =>
      granted.some((g) => g.accessType === p.accessType && g.recordType === p.recordType),
    );
    if (!ok) return 'denied';
    await setSetting(KEY, '1');
    return 'granted';
  } catch {
    return 'unavailable';
  }
}

/** Données minimales d'une séance terminée, prêtes à écrire dans Health Connect. */
export type HealthSessionData = {
  type: ActivityType;
  startedAt: number; // ms epoch
  endedAt: number; // ms epoch
  distanceM?: number | null;
  calories?: number | null;
  hrSamples?: { ts: number; hr: number }[];
};

/**
 * Construit les enregistrements Health Connect d'une séance. Fonction pure
 * (testée dans __tests__/lib/health-connect.test.ts) : aucun accès natif.
 * Retourne [] si l'intervalle est invalide — Health Connect rejette les
 * enregistrements dont startTime >= endTime.
 */
export function buildHealthRecords(data: HealthSessionData): HealthConnectRecord[] {
  if (!Number.isFinite(data.startedAt) || !Number.isFinite(data.endedAt)) return [];
  if (data.endedAt <= data.startedAt) return [];
  const startTime = new Date(data.startedAt).toISOString();
  const endTime = new Date(data.endedAt).toISOString();

  const records: HealthConnectRecord[] = [
    {
      recordType: 'ExerciseSession',
      exerciseType:
        data.type === 'velo' ? EXERCISE_TYPE_BIKING : EXERCISE_TYPE_STRENGTH_TRAINING,
      title: data.type === 'velo' ? 'Sortie vélo' : 'Séance musculation',
      startTime,
      endTime,
    },
  ];

  if (data.distanceM != null && data.distanceM > 0) {
    records.push({
      recordType: 'Distance',
      distance: { unit: 'meters', value: data.distanceM },
      startTime,
      endTime,
    });
  }

  if (data.calories != null && data.calories > 0) {
    records.push({
      recordType: 'ActiveCaloriesBurned',
      energy: { unit: 'kilocalories', value: data.calories },
      startTime,
      endTime,
    });
  }

  // Health Connect exige des échantillons dans [startTime, endTime] — on écarte
  // ce qui déborde (horloge BLE en avance, échantillons reçus après l'arrêt).
  const samples = (data.hrSamples ?? [])
    .filter((s) => s.ts >= data.startedAt && s.ts <= data.endedAt && s.hr > 0 && s.hr < 300)
    .map((s) => ({ time: new Date(s.ts).toISOString(), beatsPerMinute: Math.round(s.hr) }));
  if (samples.length > 0) {
    records.push({ recordType: 'HeartRate', samples, startTime, endTime });
  }

  return records;
}

/**
 * Écrit une séance terminée dans Health Connect. Best-effort et fire-and-forget
 * (même contrat qu'`autoBackup()`) : no-op si l'opt-in est désactivé, si Health
 * Connect est indisponible ou si une permission a été révoquée entre-temps.
 * Ne lève jamais — l'enregistrement local a déjà réussi, on ne bloque ni ne
 * casse la navigation pour un miroir santé.
 */
export async function exportSessionToHealthConnect(data: HealthSessionData): Promise<void> {
  try {
    if (!(await getHealthConnectEnabled())) return;
    const hc = await getModule();
    if (!hc) return;
    if ((await hc.getSdkStatus()) !== SDK_AVAILABLE) return;
    if (!(await hc.initialize())) return;

    // Permissions revérifiées à chaque export : l'utilisateur peut les révoquer
    // depuis Health Connect sans passer par Élan.
    const granted = await hc.getGrantedPermissions();
    const can = (recordType: Permission['recordType']) =>
      granted.some((g) => g.accessType === 'write' && g.recordType === recordType);
    if (!can('ExerciseSession')) return;

    const records = buildHealthRecords(data).filter((r) => can(r.recordType));
    if (records.length === 0) return;
    await hc.insertRecords(records);
  } catch {
    // Best-effort : jamais bloquant pour l'utilisateur.
  }
}
