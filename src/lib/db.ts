// Accès SQLite local. Toutes les données restent sur l'appareil.
import * as SQLite from 'expo-sqlite';

import type {
  ActivityType,
  MuscuSet,
  PeriodStats,
  Profile,
  Session,
  TrackPoint,
} from '@/lib/types';

const DB_NAME = 'suivi-sport.db';

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

/** Ouvre (une seule fois) la base et applique les migrations. */
export function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await SQLite.openDatabaseAsync(DB_NAME);
      await migrate(db);
      return db;
    })();
  }
  return dbPromise;
}

async function migrate(db: SQLite.SQLiteDatabase) {
  await db.execAsync('PRAGMA journal_mode = WAL;');
  await db.execAsync('PRAGMA foreign_keys = ON;');

  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version;');
  const version = row?.user_version ?? 0;

  if (version < 1) {
    await db.execAsync(`
      CREATE TABLE sessions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        type TEXT NOT NULL,
        startedAt INTEGER NOT NULL,
        endedAt INTEGER,
        durationSec INTEGER NOT NULL DEFAULT 0,
        notes TEXT,
        avgHr REAL,
        maxHr REAL,
        distanceM REAL,
        avgSpeedKmh REAL,
        maxSpeedKmh REAL,
        elevationGainM REAL,
        calories REAL
      );
      CREATE INDEX idx_sessions_startedAt ON sessions (startedAt DESC);

      CREATE TABLE track_points (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionId INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
        ts INTEGER NOT NULL,
        lat REAL NOT NULL,
        lon REAL NOT NULL,
        altitude REAL,
        speedKmh REAL,
        hr REAL
      );
      CREATE INDEX idx_track_session ON track_points (sessionId, ts);

      CREATE TABLE muscu_sets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sessionId INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
        exercise TEXT NOT NULL,
        setIndex INTEGER NOT NULL,
        reps INTEGER NOT NULL,
        weightKg REAL NOT NULL
      );
      CREATE INDEX idx_sets_session ON muscu_sets (sessionId, exercise, setIndex);

      CREATE TABLE settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);
    await db.execAsync('PRAGMA user_version = 1;');
  }

  if (version < 2) {
    // Capteurs de cadence/vitesse vélo (profil BLE CSC).
    await db.execAsync(`
      ALTER TABLE sessions ADD COLUMN avgCadence REAL;
      ALTER TABLE sessions ADD COLUMN maxCadence REAL;
      ALTER TABLE track_points ADD COLUMN cadence REAL;
    `);
    await db.execAsync('PRAGMA user_version = 2;');
  }

  if (version < 3) {
    // Import Strava : provenance + clé de déduplication (index unique partiel
    // pour rendre une ré-importation idempotente, sans gêner les séances natives
    // dont externalId reste NULL).
    await db.execAsync(`
      ALTER TABLE sessions ADD COLUMN source TEXT;
      ALTER TABLE sessions ADD COLUMN externalId TEXT;
      CREATE UNIQUE INDEX idx_sessions_external ON sessions (externalId) WHERE externalId IS NOT NULL;
    `);
    await db.execAsync('PRAGMA user_version = 3;');
  }
}

// ---------------------------------------------------------------------------
// Réglages clé/valeur (profil, ceinture appairée…)
// ---------------------------------------------------------------------------

export async function getSetting(key: string): Promise<string | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ value: string }>(
    'SELECT value FROM settings WHERE key = ?;',
    key,
  );
  return row?.value ?? null;
}

export async function setSetting(key: string, value: string): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value;',
    key,
    value,
  );
}

const DEFAULT_PROFILE: Profile = { weightKg: 70, maxHr: 190 };

export async function getProfile(): Promise<Profile> {
  const raw = await getSetting('profile');
  if (!raw) return DEFAULT_PROFILE;
  try {
    return { ...DEFAULT_PROFILE, ...JSON.parse(raw) };
  } catch {
    return DEFAULT_PROFILE;
  }
}

export async function saveProfile(profile: Profile): Promise<void> {
  await setSetting('profile', JSON.stringify(profile));
}

// ---------------------------------------------------------------------------
// Séances
// ---------------------------------------------------------------------------

/** Crée une séance « en cours » et renvoie son id. */
export async function createSession(type: ActivityType, startedAt: number): Promise<number> {
  const db = await getDb();
  const res = await db.runAsync(
    'INSERT INTO sessions (type, startedAt, durationSec) VALUES (?, ?, 0);',
    type,
    startedAt,
  );
  return res.lastInsertRowId;
}

export type SessionUpdate = Partial<
  Pick<
    Session,
    | 'endedAt'
    | 'durationSec'
    | 'notes'
    | 'avgHr'
    | 'maxHr'
    | 'distanceM'
    | 'avgSpeedKmh'
    | 'maxSpeedKmh'
    | 'elevationGainM'
    | 'avgCadence'
    | 'maxCadence'
    | 'calories'
  >
>;

export async function updateSession(id: number, patch: SessionUpdate): Promise<void> {
  const keys = Object.keys(patch) as (keyof SessionUpdate)[];
  if (keys.length === 0) return;
  const db = await getDb();
  const assignments = keys.map((k) => `${k} = ?`).join(', ');
  const values = keys.map((k) => patch[k] ?? null);
  await db.runAsync(`UPDATE sessions SET ${assignments} WHERE id = ?;`, ...values, id);
}

export async function getSession(id: number): Promise<Session | null> {
  const db = await getDb();
  return db.getFirstAsync<Session>('SELECT * FROM sessions WHERE id = ?;', id);
}

export async function listSessions(limit = 100): Promise<Session[]> {
  const db = await getDb();
  return db.getAllAsync<Session>(
    'SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT ?;',
    limit,
  );
}

export async function deleteSession(id: number): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM sessions WHERE id = ?;', id);
}

// ---------------------------------------------------------------------------
// Points GPS
// ---------------------------------------------------------------------------

export async function insertTrackPoints(
  sessionId: number,
  points: Omit<TrackPoint, 'id' | 'sessionId'>[],
): Promise<void> {
  if (points.length === 0) return;
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    for (const p of points) {
      await db.runAsync(
        'INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence) VALUES (?, ?, ?, ?, ?, ?, ?, ?);',
        sessionId,
        p.ts,
        p.lat,
        p.lon,
        p.altitude,
        p.speedKmh,
        p.hr,
        p.cadence,
      );
    }
  });
}

// ---------------------------------------------------------------------------
// Import de séances externes (Strava) — déduplication par externalId
// ---------------------------------------------------------------------------

/** Une séance prête à insérer depuis une source externe (fichier Strava). */
export type ImportedSessionRow = {
  type: ActivityType;
  startedAt: number;
  endedAt: number;
  durationSec: number;
  notes: string | null;
  avgHr: number | null;
  maxHr: number | null;
  distanceM: number | null;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  elevationGainM: number | null;
  avgCadence: number | null;
  maxCadence: number | null;
  calories: number | null;
  source: string;
  externalId: string;
};

/**
 * Insère une séance importée et ses points GPS de façon atomique. Renvoie
 * `'duplicate'` (sans rien écrire) si une séance avec le même `externalId`
 * existe déjà — l'index unique partiel rend la ré-importation idempotente.
 */
export async function insertImportedSession(
  session: ImportedSessionRow,
  points: Omit<TrackPoint, 'id' | 'sessionId'>[],
): Promise<'imported' | 'duplicate'> {
  const db = await getDb();
  let result: 'imported' | 'duplicate' = 'imported';
  await db.withTransactionAsync(async () => {
    const res = await db.runAsync(
      `INSERT INTO sessions
         (type, startedAt, endedAt, durationSec, notes, avgHr, maxHr, distanceM,
          avgSpeedKmh, maxSpeedKmh, elevationGainM, avgCadence, maxCadence, calories,
          source, externalId)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(externalId) WHERE externalId IS NOT NULL DO NOTHING;`,
      session.type,
      session.startedAt,
      session.endedAt,
      session.durationSec,
      session.notes,
      session.avgHr,
      session.maxHr,
      session.distanceM,
      session.avgSpeedKmh,
      session.maxSpeedKmh,
      session.elevationGainM,
      session.avgCadence,
      session.maxCadence,
      session.calories,
      session.source,
      session.externalId,
    );
    if (res.changes === 0) {
      result = 'duplicate';
      return;
    }
    const sessionId = res.lastInsertRowId;
    for (const p of points) {
      await db.runAsync(
        'INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence) VALUES (?, ?, ?, ?, ?, ?, ?, ?);',
        sessionId,
        p.ts,
        p.lat,
        p.lon,
        p.altitude,
        p.speedKmh,
        p.hr,
        p.cadence,
      );
    }
  });
  return result;
}

export async function getTrackPoints(sessionId: number): Promise<TrackPoint[]> {
  const db = await getDb();
  return db.getAllAsync<TrackPoint>(
    'SELECT * FROM track_points WHERE sessionId = ? ORDER BY ts ASC;',
    sessionId,
  );
}

// ---------------------------------------------------------------------------
// Séries de musculation
// ---------------------------------------------------------------------------

export async function replaceMuscuSets(
  sessionId: number,
  sets: Omit<MuscuSet, 'id' | 'sessionId'>[],
): Promise<void> {
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await db.runAsync('DELETE FROM muscu_sets WHERE sessionId = ?;', sessionId);
    for (const s of sets) {
      await db.runAsync(
        'INSERT INTO muscu_sets (sessionId, exercise, setIndex, reps, weightKg) VALUES (?, ?, ?, ?, ?);',
        sessionId,
        s.exercise,
        s.setIndex,
        s.reps,
        s.weightKg,
      );
    }
  });
}

export async function getMuscuSets(sessionId: number): Promise<MuscuSet[]> {
  const db = await getDb();
  return db.getAllAsync<MuscuSet>(
    'SELECT * FROM muscu_sets WHERE sessionId = ? ORDER BY setIndex ASC, id ASC;',
    sessionId,
  );
}

/**
 * Dernière charge enregistrée par exercice : la série la plus lourde de la
 * séance muscu terminée la plus récente contenant cet exercice. Sert d'amorce
 * de progression au chargement d'un programme. La correspondance se fait sur le
 * libellé exact — les templates réutilisent les mêmes noms.
 */
export async function lastWeightByExercise(
  names: string[],
): Promise<Record<string, number>> {
  const db = await getDb();
  const out: Record<string, number> = {};
  for (const name of names) {
    const row = await db.getFirstAsync<{ weightKg: number }>(
      `SELECT ms.weightKg
         FROM muscu_sets ms
         JOIN sessions s ON s.id = ms.sessionId
        WHERE s.type = 'muscu' AND s.endedAt IS NOT NULL AND ms.exercise = ?
        ORDER BY s.startedAt DESC, ms.weightKg DESC
        LIMIT 1;`,
      name,
    );
    if (row) out[name] = row.weightKg;
  }
  return out;
}

/** Ligne d'index : un exercice connu et son dernier état. */
export type ExerciseSummary = {
  exercise: string;
  sessions: number;
  lastWeightKg: number;
  lastAt: number;
};

/** Liste des exercices muscu déjà enregistrés, les plus récents d'abord. */
export async function listMuscuExercises(): Promise<ExerciseSummary[]> {
  const db = await getDb();
  return db.getAllAsync<ExerciseSummary>(
    `SELECT ms.exercise AS exercise,
            COUNT(DISTINCT ms.sessionId) AS sessions,
            MAX(s.startedAt) AS lastAt,
            (SELECT m2.weightKg
               FROM muscu_sets m2
               JOIN sessions s2 ON s2.id = m2.sessionId
              WHERE m2.exercise = ms.exercise AND s2.endedAt IS NOT NULL
              ORDER BY s2.startedAt DESC, m2.weightKg DESC
              LIMIT 1) AS lastWeightKg
       FROM muscu_sets ms
       JOIN sessions s ON s.id = ms.sessionId
      WHERE s.endedAt IS NOT NULL
      GROUP BY ms.exercise
      ORDER BY lastAt DESC;`,
  );
}

/** Un point de progression : l'état d'un exercice sur une séance donnée. */
export type ExercisePoint = {
  sessionId: number;
  startedAt: number;
  maxWeightKg: number;
  topReps: number;
  volume: number;
  sets: number;
};

/**
 * Historique d'un exercice, une ligne par séance terminée, du plus ancien au
 * plus récent. `topReps` correspond aux reps de la série la plus lourde
 * (SQLite renvoie la valeur de la ligne portant le MAX pour les colonnes nues).
 */
export async function exerciseHistory(name: string): Promise<ExercisePoint[]> {
  const db = await getDb();
  return db.getAllAsync<ExercisePoint>(
    `SELECT s.id AS sessionId,
            s.startedAt AS startedAt,
            MAX(ms.weightKg) AS maxWeightKg,
            ms.reps AS topReps,
            SUM(ms.reps * ms.weightKg) AS volume,
            COUNT(*) AS sets
       FROM muscu_sets ms
       JOIN sessions s ON s.id = ms.sessionId
      WHERE s.endedAt IS NOT NULL AND ms.exercise = ?
      GROUP BY s.id
      ORDER BY s.startedAt ASC;`,
    name,
  );
}

// ---------------------------------------------------------------------------
// Statistiques
// ---------------------------------------------------------------------------

export async function statsSince(sinceMs: number): Promise<PeriodStats> {
  const db = await getDb();
  const row = await db.getFirstAsync<PeriodStats>(
    `SELECT
       COUNT(*) AS sessionCount,
       COALESCE(SUM(durationSec), 0) AS totalDurationSec,
       COALESCE(SUM(distanceM), 0) AS totalDistanceM,
       COALESCE(SUM(calories), 0) AS totalCalories
     FROM sessions
     WHERE endedAt IS NOT NULL AND startedAt >= ?;`,
    sinceMs,
  );
  return (
    row ?? { sessionCount: 0, totalDurationSec: 0, totalDistanceM: 0, totalCalories: 0 }
  );
}

/** Durée totale d'effort par jour sur les N derniers jours (pour le graphe). */
export async function dailyDurations(days: number): Promise<{ day: string; durationSec: number }[]> {
  const db = await getDb();
  return db.getAllAsync<{ day: string; durationSec: number }>(
    `SELECT date(startedAt / 1000, 'unixepoch', 'localtime') AS day,
            COALESCE(SUM(durationSec), 0) AS durationSec
     FROM sessions
     WHERE endedAt IS NOT NULL
       AND startedAt >= ?
     GROUP BY day
     ORDER BY day ASC;`,
    Date.now() - days * 86400_000,
  );
}

/** Efface toutes les données (séances, points, séries) — garde les réglages. */
export async function clearAllData(): Promise<void> {
  const db = await getDb();
  await db.execAsync('DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions;');
}

// ---------------------------------------------------------------------------
// Sauvegarde / restauration (export complet de la base)
// ---------------------------------------------------------------------------

/** Clés de réglages exclues des sauvegardes (secrets, propres à l'appareil). */
const BACKUP_EXCLUDED_KEYS = new Set(['backup_s3', 'backup_last']);

export type DbSnapshot = {
  sessions: Session[];
  trackPoints: TrackPoint[];
  muscuSets: MuscuSet[];
  settings: { key: string; value: string }[];
};

/** Lit l'intégralité de la base pour une sauvegarde (hors réglages secrets). */
export async function exportAll(): Promise<DbSnapshot> {
  const db = await getDb();
  const [sessions, trackPoints, muscuSets, allSettings] = await Promise.all([
    db.getAllAsync<Session>('SELECT * FROM sessions;'),
    db.getAllAsync<TrackPoint>('SELECT * FROM track_points;'),
    db.getAllAsync<MuscuSet>('SELECT * FROM muscu_sets;'),
    db.getAllAsync<{ key: string; value: string }>('SELECT key, value FROM settings;'),
  ]);
  const settings = allSettings.filter((s) => !BACKUP_EXCLUDED_KEYS.has(s.key));
  return { sessions, trackPoints, muscuSets, settings };
}

/**
 * Remplace toutes les données locales par celles d'une sauvegarde. Les ids
 * sont conservés (la base est vidée au préalable, dans une transaction).
 * Les réglages secrets de la sauvegarde elle-même ne sont jamais réécrits.
 */
export async function importAll(snap: DbSnapshot): Promise<void> {
  const db = await getDb();
  const n = (v: unknown): SQLite.SQLiteBindValue => (v === undefined ? null : (v as SQLite.SQLiteBindValue));
  await db.withTransactionAsync(async () => {
    await db.execAsync('DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions;');

    for (const s of snap.sessions ?? []) {
      await db.runAsync(
        `INSERT INTO sessions
           (id, type, startedAt, endedAt, durationSec, notes, avgHr, maxHr,
            distanceM, avgSpeedKmh, maxSpeedKmh, elevationGainM, avgCadence, maxCadence, calories,
            source, externalId)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);`,
        n(s.id), n(s.type), n(s.startedAt), n(s.endedAt), n(s.durationSec), n(s.notes),
        n(s.avgHr), n(s.maxHr), n(s.distanceM), n(s.avgSpeedKmh), n(s.maxSpeedKmh),
        n(s.elevationGainM), n(s.avgCadence), n(s.maxCadence), n(s.calories),
        n(s.source), n(s.externalId),
      );
    }
    for (const p of snap.trackPoints ?? []) {
      await db.runAsync(
        `INSERT INTO track_points (id, sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);`,
        n(p.id), n(p.sessionId), n(p.ts), n(p.lat), n(p.lon),
        n(p.altitude), n(p.speedKmh), n(p.hr), n(p.cadence),
      );
    }
    for (const m of snap.muscuSets ?? []) {
      await db.runAsync(
        `INSERT INTO muscu_sets (id, sessionId, exercise, setIndex, reps, weightKg)
         VALUES (?, ?, ?, ?, ?, ?);`,
        n(m.id), n(m.sessionId), n(m.exercise), n(m.setIndex), n(m.reps), n(m.weightKg),
      );
    }
    for (const st of snap.settings ?? []) {
      if (BACKUP_EXCLUDED_KEYS.has(st.key)) continue;
      await db.runAsync(
        'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value;',
        st.key,
        st.value,
      );
    }
  });
}
