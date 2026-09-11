// Tests de la construction des enregistrements Health Connect
// (lib/health-connect.ts — buildHealthRecords, fonction pure).
//
// Le module importe lib/db (pour le réglage opt-in) qui importe expo-sqlite :
// on le neutralise, buildHealthRecords ne touche jamais la base.
jest.mock('expo-sqlite', () => ({ openDatabaseAsync: jest.fn() }));

import { buildHealthRecords, healthClientRecordId } from '@/lib/health-connect';

const T0 = Date.UTC(2026, 5, 1, 10, 0, 0); // 2026-06-01T10:00:00Z
const T1 = T0 + 3600_000; // +1 h

describe('buildHealthRecords', () => {
  it('vélo complet : ExerciseSession + Distance + ActiveCaloriesBurned + HeartRate', () => {
    const records = buildHealthRecords({
      type: 'velo',
      startedAt: T0,
      endedAt: T1,
      distanceM: 25_000,
      calories: 600,
      hrSamples: [
        { ts: T0 + 60_000, hr: 120 },
        { ts: T0 + 120_000, hr: 145.6 },
      ],
    });

    expect(records.map((r) => r.recordType)).toEqual([
      'ExerciseSession',
      'Distance',
      'ActiveCaloriesBurned',
      'HeartRate',
    ]);

    const session = records[0];
    if (session.recordType !== 'ExerciseSession') throw new Error('type inattendu');
    expect(session.exerciseType).toBe(8); // ExerciseType.BIKING
    expect(session.startTime).toBe('2026-06-01T10:00:00.000Z');
    expect(session.endTime).toBe('2026-06-01T11:00:00.000Z');

    const distance = records[1];
    if (distance.recordType !== 'Distance') throw new Error('type inattendu');
    expect(distance.distance).toEqual({ unit: 'meters', value: 25_000 });

    const energy = records[2];
    if (energy.recordType !== 'ActiveCaloriesBurned') throw new Error('type inattendu');
    expect(energy.energy).toEqual({ unit: 'kilocalories', value: 600 });

    const hr = records[3];
    if (hr.recordType !== 'HeartRate') throw new Error('type inattendu');
    // Les BPM sont arrondis (Health Connect attend des entiers).
    expect(hr.samples).toEqual([
      { time: new Date(T0 + 60_000).toISOString(), beatsPerMinute: 120 },
      { time: new Date(T0 + 120_000).toISOString(), beatsPerMinute: 146 },
    ]);
  });

  it('muscu sans distance ni FC : ExerciseSession + calories seulement', () => {
    const records = buildHealthRecords({
      type: 'muscu',
      startedAt: T0,
      endedAt: T1,
      calories: 250,
    });

    expect(records.map((r) => r.recordType)).toEqual([
      'ExerciseSession',
      'ActiveCaloriesBurned',
    ]);
    const session = records[0];
    if (session.recordType !== 'ExerciseSession') throw new Error('type inattendu');
    expect(session.exerciseType).toBe(70); // ExerciseType.STRENGTH_TRAINING
  });

  it('ignore distance et calories nulles, absentes ou à zéro', () => {
    const records = buildHealthRecords({
      type: 'velo',
      startedAt: T0,
      endedAt: T1,
      distanceM: 0,
      calories: null,
    });
    expect(records.map((r) => r.recordType)).toEqual(['ExerciseSession']);
  });

  it('écarte les échantillons FC hors intervalle ou aberrants', () => {
    const records = buildHealthRecords({
      type: 'velo',
      startedAt: T0,
      endedAt: T1,
      hrSamples: [
        { ts: T0 - 1000, hr: 110 }, // avant le départ
        { ts: T0 + 1000, hr: 0 }, // bpm nul
        { ts: T0 + 2000, hr: 320 }, // bpm aberrant
        { ts: T1 + 1000, hr: 130 }, // après l'arrivée
        { ts: T0 + 3000, hr: 142 }, // seul valide
      ],
    });
    const hr = records.find((r) => r.recordType === 'HeartRate');
    if (!hr || hr.recordType !== 'HeartRate') throw new Error('HeartRate manquant');
    expect(hr.samples).toHaveLength(1);
    expect(hr.samples[0].beatsPerMinute).toBe(142);
  });

  it("n'émet pas de bloc HeartRate si aucun échantillon valide", () => {
    const records = buildHealthRecords({
      type: 'velo',
      startedAt: T0,
      endedAt: T1,
      hrSamples: [{ ts: T0 - 1000, hr: 110 }],
    });
    expect(records.some((r) => r.recordType === 'HeartRate')).toBe(false);
  });

  it('retourne [] pour un intervalle invalide (Health Connect le rejetterait)', () => {
    expect(buildHealthRecords({ type: 'velo', startedAt: T1, endedAt: T0 })).toEqual([]);
    expect(buildHealthRecords({ type: 'velo', startedAt: T0, endedAt: T0 })).toEqual([]);
    expect(buildHealthRecords({ type: 'velo', startedAt: NaN, endedAt: T1 })).toEqual([]);
  });

  it('estampille un clientRecordId stable par (séance × type) — idempotence', () => {
    const data = { type: 'velo' as const, startedAt: T0, endedAt: T1, distanceM: 1000, calories: 100 };
    const a = buildHealthRecords(data);
    const b = buildHealthRecords(data); // ré-export de la même séance
    // Deux exports → mêmes identifiants client (Health Connect réécrit, ne double pas).
    const ids = (recs: typeof a) => recs.map((r) => r.metadata?.clientRecordId);
    expect(ids(a)).toEqual(ids(b));
    expect(ids(a)).toEqual([
      `elan-velo-${T0}-session`,
      `elan-velo-${T0}-distance`,
      `elan-velo-${T0}-calories`,
    ]);
    // Une autre séance (autre instant de début) a d'autres identifiants.
    const other = buildHealthRecords({ ...data, startedAt: T0 + 5000, endedAt: T1 + 5000 });
    expect(other[0].metadata?.clientRecordId).not.toBe(a[0].metadata?.clientRecordId);
  });
});

describe('buildHealthRecords — activités à pied', () => {
  const base = { startedAt: T0, endedAt: T1, distanceM: 8000, calories: 500, hrSamples: [] };

  /** Extrait l'ExerciseSession de l'union d'enregistrements. */
  const exerciseSession = (type: 'velo' | 'course' | 'marche' | 'muscu') => {
    const record = buildHealthRecords({ ...base, type })[0];
    if (record.recordType !== 'ExerciseSession') throw new Error('ExerciseSession attendue');
    return record;
  };

  it('associe chaque activité à son type d’exercice Health Connect', () => {
    // Constantes androidx ExerciseType : BIKING 8, RUNNING 56,
    // STRENGTH_TRAINING 70, WALKING 79.
    const typeOf = (type: 'velo' | 'course' | 'marche' | 'muscu') =>
      exerciseSession(type).exerciseType;
    expect(typeOf('velo')).toBe(8);
    expect(typeOf('course')).toBe(56);
    expect(typeOf('marche')).toBe(79);
    expect(typeOf('muscu')).toBe(70);
  });

  it('titre l’enregistrement selon l’activité', () => {
    expect(exerciseSession('course').title).toBe('Course à pied');
    expect(exerciseSession('marche').title).toBe('Marche');
  });

  it('l’identifiant de déduplication porte le type', () => {
    expect(exerciseSession('course').metadata?.clientRecordId).toBe(`elan-course-${T0}-session`);
  });
});

describe('healthClientRecordId', () => {
  it('porte le type d’activité, l’instant de début et le rôle', () => {
    expect(healthClientRecordId('course', T0, 'session')).toBe(`elan-course-${T0}-session`);
    expect(healthClientRecordId('velo', T0, 'distance')).toBe(`elan-velo-${T0}-distance`);
  });

  it('change avec le type — c’est pourquoi un retype doit supprimer l’ancien miroir', () => {
    expect(healthClientRecordId('velo', T0, 'session')).not.toBe(
      healthClientRecordId('marche', T0, 'session'),
    );
  });

  it('est bien l’identifiant écrit par buildHealthRecords', () => {
    const records = buildHealthRecords({
      type: 'marche',
      startedAt: T0,
      endedAt: T1,
      distanceM: 5000,
      calories: 200,
    });
    expect(records.map((r) => r.metadata?.clientRecordId)).toEqual([
      healthClientRecordId('marche', T0, 'session'),
      healthClientRecordId('marche', T0, 'distance'),
      healthClientRecordId('marche', T0, 'calories'),
    ]);
  });
});
