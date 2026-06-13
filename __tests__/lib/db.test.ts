// Test d'intégration de la couche SQLite (lib/db.ts) : round-trip
// exportAll → clear → importAll, exclusion des réglages secrets, et
// idempotence de l'import Strava (index unique partiel sur externalId).
//
// Le harnais Jest n'exécute pas le module natif expo-sqlite (RN). On le
// remplace ici par un adaptateur minimal au-dessus de better-sqlite3
// (SQLite réel, en mémoire) qui implémente la surface d'API utilisée par
// db.ts : execAsync / getFirstAsync / getAllAsync / runAsync /
// prepareAsync / withTransactionAsync.
jest.mock('expo-sqlite', () => {
  const Database = require('better-sqlite3');
  const num = (v: number | bigint) => (typeof v === 'bigint' ? Number(v) : v);

  function open() {
    const db = new Database(':memory:');
    return {
      execAsync: async (sql: string) => {
        db.exec(sql);
      },
      getFirstAsync: async (sql: string, ...params: unknown[]) =>
        db.prepare(sql).get(...params) ?? null,
      getAllAsync: async (sql: string, ...params: unknown[]) => db.prepare(sql).all(...params),
      runAsync: async (sql: string, ...params: unknown[]) => {
        const info = db.prepare(sql).run(...params);
        return { lastInsertRowId: num(info.lastInsertRowid), changes: num(info.changes) };
      },
      prepareAsync: async (sql: string) => {
        const stmt = db.prepare(sql);
        return {
          executeAsync: async (...params: unknown[]) => {
            const info = stmt.run(...params);
            return { lastInsertRowId: num(info.lastInsertRowid), changes: num(info.changes) };
          },
          finalizeAsync: async () => {},
        };
      },
      withTransactionAsync: async (fn: () => Promise<void>) => {
        db.exec('BEGIN');
        try {
          await fn();
          db.exec('COMMIT');
        } catch (e) {
          db.exec('ROLLBACK');
          throw e;
        }
      },
    };
  }

  return { openDatabaseAsync: async () => open() };
});

import {
  clearAllData,
  createSession,
  exportAll,
  getMuscuSets,
  getSession,
  getTrackPoints,
  importAll,
  insertImportedSession,
  insertTrackPoints,
  replaceMuscuSets,
  sessionRecords,
  setSetting,
  statsBetween,
  tonnageBetween,
  updateSession,
  type DbSnapshot,
  type ImportedSessionRow,
} from '@/lib/db';

/** Trie les tableaux d'un snapshot pour comparer indépendamment de l'ordre SQL. */
function normalize(snap: DbSnapshot): DbSnapshot {
  return {
    sessions: [...snap.sessions].sort((a, b) => a.id - b.id),
    trackPoints: [...snap.trackPoints].sort((a, b) => a.id - b.id),
    muscuSets: [...snap.muscuSets].sort((a, b) => a.id - b.id),
    settings: [...snap.settings].sort((a, b) => a.key.localeCompare(b.key)),
  };
}

const importedRow = (externalId: string): ImportedSessionRow => ({
  type: 'velo',
  startedAt: 1_700_500_000_000,
  endedAt: 1_700_503_600_000,
  durationSec: 3600,
  movingTimeSec: 3400,
  notes: 'Importé depuis Strava',
  avgHr: 140,
  maxHr: 165,
  distanceM: 25_000,
  avgSpeedKmh: 25,
  maxSpeedKmh: 48,
  elevationGainM: 320,
  avgCadence: 85,
  maxCadence: 100,
  calories: 600,
  source: 'strava',
  externalId,
});

beforeAll(async () => {
  // Une séance vélo terminée + ses points GPS.
  const veloId = await createSession('velo', 1_700_000_000_000);
  await updateSession(veloId, {
    endedAt: 1_700_003_600_000,
    durationSec: 3600,
    distanceM: 30_000,
    avgSpeedKmh: 30,
    maxSpeedKmh: 55,
    elevationGainM: 400,
    calories: 700,
  });
  await insertTrackPoints(veloId, [
    { ts: 1_700_000_000_000, lat: 48.8566, lon: 2.3522, altitude: 35, speedKmh: 0, hr: 110, cadence: null },
    { ts: 1_700_000_010_000, lat: 48.857, lon: 2.3525, altitude: 36, speedKmh: 18, hr: 120, cadence: 85 },
  ]);

  // Une séance muscu terminée + ses séries.
  const muscuId = await createSession('muscu', 1_700_100_000_000);
  await updateSession(muscuId, { endedAt: 1_700_103_000_000, durationSec: 3000 });
  await replaceMuscuSets(muscuId, [
    { exercise: 'Goblet squat', setIndex: 1, reps: 10, weightKg: 20 },
    { exercise: 'Goblet squat', setIndex: 2, reps: 9, weightKg: 22 },
  ]);

  // Réglages : un public (conservé) et deux secrets (exclus des sauvegardes).
  await setSetting('profile', JSON.stringify({ weightKg: 78, maxHr: 188 }));
  await setSetting('backup_s3', 'SECRET-CREDENTIALS');
  await setSetting('backup_last', '{"ok":true}');
});

describe('exportAll', () => {
  it('exporte séances, points et séries, et exclut les réglages secrets', async () => {
    const snap = await exportAll();
    expect(snap.sessions).toHaveLength(2);
    expect(snap.trackPoints).toHaveLength(2);
    expect(snap.muscuSets).toHaveLength(2);

    const keys = snap.settings.map((s) => s.key);
    expect(keys).toContain('profile');
    expect(keys).not.toContain('backup_s3');
    expect(keys).not.toContain('backup_last');
  });
});

describe('round-trip exportAll → clear → importAll', () => {
  it('restaure des données identiques (ids préservés)', async () => {
    const before = normalize(await exportAll());

    await clearAllData();
    const cleared = await exportAll();
    expect(cleared.sessions).toHaveLength(0);
    expect(cleared.trackPoints).toHaveLength(0);
    expect(cleared.muscuSets).toHaveLength(0);

    await importAll(before);
    const after = normalize(await exportAll());

    expect(after).toEqual(before);
  });

  it('les points et séries restent rattachés à leur séance après restauration', async () => {
    const snap = normalize(await exportAll());
    const velo = snap.sessions.find((s) => s.type === 'velo')!;
    const muscu = snap.sessions.find((s) => s.type === 'muscu')!;

    expect(await getTrackPoints(velo.id)).toHaveLength(2);
    expect(await getMuscuSets(muscu.id)).toHaveLength(2);
  });
});

describe('insertImportedSession — déduplication par externalId', () => {
  it('insère la première fois puis signale un doublon pour le même externalId', async () => {
    const first = await insertImportedSession(importedRow('strava-abc123'), []);
    expect(first).toBe('imported');

    const second = await insertImportedSession(importedRow('strava-abc123'), []);
    expect(second).toBe('duplicate');

    // Un externalId différent passe.
    const other = await insertImportedSession(importedRow('strava-def456'), []);
    expect(other).toBe('imported');
  });
});

describe('sessionRecords — record de durée vélo sur le temps en mouvement', () => {
  it('un chrono oublié à l’arrêt ne rafle pas le record de durée', async () => {
    await clearAllData();
    // Sortie A : 1 h roulée d'affilée (temps en mouvement = temps total).
    const a = await createSession('velo', 1_800_000_000_000);
    await updateSession(a, {
      endedAt: 1_800_003_600_000,
      durationSec: 3600,
      movingTimeSec: 3600,
      distanceM: 30_000,
    });
    // Sortie B : 3 h de temps total mais 10 min en mouvement (chrono oublié).
    const b = await createSession('velo', 1_800_100_000_000);
    await updateSession(b, {
      endedAt: 1_800_110_800_000,
      durationSec: 10_800,
      movingTimeSec: 600,
      distanceM: 4_000,
    });

    const recA = await sessionRecords((await getSession(a))!);
    const recB = await sessionRecords((await getSession(b))!);
    // A (1 h en mouvement) détient le record de durée ; B (10 min) ne l'a pas,
    // malgré ses 3 h de temps total.
    expect(recA.some((r) => r.kind === 'duration')).toBe(true);
    expect(recB.some((r) => r.kind === 'duration')).toBe(false);
  });
});

describe('statsBetween (filtre par type) & tonnageBetween — suivi d’objectifs', () => {
  const T = 2_000_000_000_000;

  beforeAll(async () => {
    await clearAllData();
    // Deux sorties vélo terminées (distances connues).
    const v1 = await createSession('velo', T);
    await updateSession(v1, { endedAt: T + 100, durationSec: 100, distanceM: 10_000 });
    const v2 = await createSession('velo', T + 1000);
    await updateSession(v2, { endedAt: T + 1100, durationSec: 100, distanceM: 5_000 });
    // Une séance muscu terminée (tonnage = 10×20 + 8×30 = 440).
    const m1 = await createSession('muscu', T + 2000);
    await updateSession(m1, { endedAt: T + 2100, durationSec: 100 });
    await replaceMuscuSets(m1, [
      { exercise: 'Squat', setIndex: 1, reps: 10, weightKg: 20 },
      { exercise: 'Squat', setIndex: 2, reps: 8, weightKg: 30 },
    ]);
    // Séance en cours (endedAt NULL) : ne doit jamais être comptée.
    await createSession('velo', T + 3000);
  });

  it('sans filtre : compte toutes les séances terminées de la fenêtre', async () => {
    const s = await statsBetween(T, T + 10_000);
    expect(s.sessionCount).toBe(3);
    expect(s.totalDistanceM).toBe(15_000);
  });

  it('filtre vélo : ignore la muscu et la séance en cours', async () => {
    const s = await statsBetween(T, T + 10_000, 'velo');
    expect(s.sessionCount).toBe(2);
    expect(s.totalDistanceM).toBe(15_000);
  });

  it('filtre muscu : une séance, sans distance', async () => {
    const s = await statsBetween(T, T + 10_000, 'muscu');
    expect(s.sessionCount).toBe(1);
    expect(s.totalDistanceM).toBe(0);
  });

  it('fenêtre exclut les bornes hautes (toMs exclusif)', async () => {
    // [T, T+1000) ne contient que la première sortie vélo.
    const s = await statsBetween(T, T + 1000, 'velo');
    expect(s.sessionCount).toBe(1);
  });

  it('tonnageBetween : somme reps × charge des séances muscu de la fenêtre', async () => {
    expect(await tonnageBetween(T, T + 10_000)).toBe(440);
    // Avant la séance muscu (à T+2000) : aucun tonnage.
    expect(await tonnageBetween(T, T + 2000)).toBe(0);
  });
});
