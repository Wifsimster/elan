import { Platform } from 'react-native';

import { getSetting, setSetting } from '@/lib/db';
import type { ActivityType, HrSample } from '@/lib/types';
import type { HealthConnectRecord, Permission } from 'react-native-health-connect';

const KEY = 'health_connect';

const EXERCISE_TYPE_BIKING = 8;
const EXERCISE_TYPE_RUNNING = 56;
const EXERCISE_TYPE_STRENGTH_TRAINING = 70;
const EXERCISE_TYPE_WALKING = 79;

const HEALTH_EXERCISE: Record<ActivityType, { type: number; title: string }> = {
  velo: { type: EXERCISE_TYPE_BIKING, title: 'Sortie vélo' },
  course: { type: EXERCISE_TYPE_RUNNING, title: 'Course à pied' },
  marche: { type: EXERCISE_TYPE_WALKING, title: 'Marche' },
  muscu: { type: EXERCISE_TYPE_STRENGTH_TRAINING, title: 'Séance musculation' },
};
const SDK_AVAILABLE = 3;

const SESSION_RECORDS: { recordType: Permission['recordType']; suffix: string }[] = [
  { recordType: 'ExerciseSession', suffix: 'session' },
  { recordType: 'Distance', suffix: 'distance' },
  { recordType: 'ActiveCaloriesBurned', suffix: 'calories' },
  { recordType: 'HeartRate', suffix: 'hr' },
];

export function healthClientRecordId(
  type: ActivityType,
  startedAt: number,
  suffix: string,
): string {
  return `elan-${type}-${startedAt}-${suffix}`;
}

const WRITE_PERMISSIONS: Permission[] = [
  { accessType: 'write', recordType: 'ExerciseSession' },
  { accessType: 'write', recordType: 'Distance' },
  { accessType: 'write', recordType: 'ActiveCaloriesBurned' },
  { accessType: 'write', recordType: 'HeartRate' },
];

type HealthConnectModule = typeof import('react-native-health-connect');

let modulePromise: Promise<HealthConnectModule | null> | null = null;

function getModule(): Promise<HealthConnectModule | null> {
  if (Platform.OS !== 'android') return Promise.resolve(null);
  if (!modulePromise) {
    modulePromise = import('react-native-health-connect').catch(() => null);
  }
  return modulePromise;
}

export function isHealthConnectSupported(): boolean {
  return Platform.OS === 'android';
}

export async function getHealthConnectEnabled(): Promise<boolean> {
  if (!isHealthConnectSupported()) return false;
  return (await getSetting(KEY)) === '1';
}

export async function disableHealthConnect(): Promise<void> {
  await setSetting(KEY, '');
}

export type EnableResult = 'granted' | 'denied' | 'unavailable';

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

export type HealthSessionData = {
  type: ActivityType;
  startedAt: number;
  endedAt: number;
  distanceM?: number | null;
  calories?: number | null;
  hrSamples?: HrSample[];
};

export function buildHealthRecords(data: HealthSessionData): HealthConnectRecord[] {
  if (!Number.isFinite(data.startedAt) || !Number.isFinite(data.endedAt)) return [];
  if (data.endedAt <= data.startedAt) return [];
  const startTime = new Date(data.startedAt).toISOString();
  const endTime = new Date(data.endedAt).toISOString();

  const meta = (suffix: string) => ({
    metadata: { clientRecordId: healthClientRecordId(data.type, data.startedAt, suffix) },
  });

  const records: HealthConnectRecord[] = [
    {
      recordType: 'ExerciseSession',
      exerciseType: HEALTH_EXERCISE[data.type].type,
      title: HEALTH_EXERCISE[data.type].title,
      startTime,
      endTime,
      ...meta('session'),
    },
  ];

  if (data.distanceM != null && data.distanceM > 0) {
    records.push({
      recordType: 'Distance',
      distance: { unit: 'meters', value: data.distanceM },
      startTime,
      endTime,
      ...meta('distance'),
    });
  }

  if (data.calories != null && data.calories > 0) {
    records.push({
      recordType: 'ActiveCaloriesBurned',
      energy: { unit: 'kilocalories', value: data.calories },
      startTime,
      endTime,
      ...meta('calories'),
    });
  }

  const samples = (data.hrSamples ?? [])
    .filter((s) => s.ts >= data.startedAt && s.ts <= data.endedAt && s.hr > 0 && s.hr < 300)
    .map((s) => ({ time: new Date(s.ts).toISOString(), beatsPerMinute: Math.round(s.hr) }));
  if (samples.length > 0) {
    records.push({ recordType: 'HeartRate', samples, startTime, endTime, ...meta('hr') });
  }

  return records;
}

export async function exportSessionToHealthConnect(data: HealthSessionData): Promise<void> {
  try {
    if (!(await getHealthConnectEnabled())) return;
    const hc = await getModule();
    if (!hc) return;
    if ((await hc.getSdkStatus()) !== SDK_AVAILABLE) return;
    if (!(await hc.initialize())) return;

    const granted = await hc.getGrantedPermissions();
    const can = (recordType: Permission['recordType']) =>
      granted.some((g) => g.accessType === 'write' && g.recordType === recordType);
    if (!can('ExerciseSession')) return;

    const records = buildHealthRecords(data).filter((r) => can(r.recordType));
    for (const record of records) {
      await hc.insertRecords([record]).catch(() => {});
    }
  } catch {
  }
}

export async function removeSessionFromHealthConnect(
  type: ActivityType,
  startedAt: number,
): Promise<void> {
  try {
    if (!(await getHealthConnectEnabled())) return;
    const hc = await getModule();
    if (!hc) return;
    if ((await hc.getSdkStatus()) !== SDK_AVAILABLE) return;
    if (!(await hc.initialize())) return;

    for (const { recordType, suffix } of SESSION_RECORDS) {
      await hc
        .deleteRecordsByUuids(recordType, [], [healthClientRecordId(type, startedAt, suffix)])
        .catch(() => {});
    }
  } catch {
  }
}
