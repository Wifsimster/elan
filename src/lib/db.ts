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
        'INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr) VALUES (?, ?, ?, ?, ?, ?, ?);',
        sessionId,
        p.ts,
        p.lat,
        p.lon,
        p.altitude,
        p.speedKmh,
        p.hr,
      );
    }
  });
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
