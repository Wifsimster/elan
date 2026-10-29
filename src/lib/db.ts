import * as SQLite from 'expo-sqlite';

import { isGpsActivity } from '@/lib/activity';
import { estimateCalories } from '@/lib/calories';
import { movingTimeSec } from '@/lib/moving-time';
import { retypeChanges } from '@/lib/session-retype';
import type {
  ActivityType,
  BodyMeasurement,
  Difficulty,
  MuscuSet,
  PeriodStats,
  Profile,
  Session,
  TrackPoint,
} from '@/lib/types';

const DB_NAME = 'suivi-sport.db';

export const SCHEMA_VERSION = 7;

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

export function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await SQLite.openDatabaseAsync(DB_NAME);
      await migrate(db);
      return db;
    })().catch((e) => {
      dbPromise = null;
      throw e;
    });
  }
  return dbPromise;
}

async function hasColumn(
  db: SQLite.SQLiteDatabase,
  table: string,
  column: string,
): Promise<boolean> {
  const cols = await db.getAllAsync<{ name: string }>(`PRAGMA table_info(${table});`);
  return cols.some((c) => c.name === column);
}

async function addColumn(
  db: SQLite.SQLiteDatabase,
  table: string,
  column: string,
  decl: string,
): Promise<void> {
  if (!(await hasColumn(db, table, column))) {
    await db.execAsync(`ALTER TABLE ${table} ADD COLUMN ${column} ${decl};`);
  }
}

async function migrate(db: SQLite.SQLiteDatabase) {
  await db.execAsync('PRAGMA journal_mode = WAL;');
  await db.execAsync('PRAGMA foreign_keys = ON;');

  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version;');
  const version = row?.user_version ?? 0;

  if (version < 1) {
    await db.withTransactionAsync(async () => {
      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS sessions (
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
        CREATE INDEX IF NOT EXISTS idx_sessions_startedAt ON sessions (startedAt DESC);

        CREATE TABLE IF NOT EXISTS track_points (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          sessionId INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
          ts INTEGER NOT NULL,
          lat REAL NOT NULL,
          lon REAL NOT NULL,
          altitude REAL,
          speedKmh REAL,
          hr REAL
        );
        CREATE INDEX IF NOT EXISTS idx_track_session ON track_points (sessionId, ts);

        CREATE TABLE IF NOT EXISTS muscu_sets (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          sessionId INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
          exercise TEXT NOT NULL,
          setIndex INTEGER NOT NULL,
          reps INTEGER NOT NULL,
          weightKg REAL NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_sets_session ON muscu_sets (sessionId, exercise, setIndex);

        CREATE TABLE IF NOT EXISTS settings (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL
        );
      `);
      await db.execAsync('PRAGMA user_version = 1;');
    });
  }

  if (version < 2) {
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'sessions', 'avgCadence', 'REAL');
      await addColumn(db, 'sessions', 'maxCadence', 'REAL');
      await addColumn(db, 'track_points', 'cadence', 'REAL');
      await db.execAsync('PRAGMA user_version = 2;');
    });
  }

  if (version < 3) {
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'sessions', 'source', 'TEXT');
      await addColumn(db, 'sessions', 'externalId', 'TEXT');
      await db.execAsync(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_external ON sessions (externalId) WHERE externalId IS NOT NULL;',
      );
      await db.execAsync('PRAGMA user_version = 3;');
    });
  }

  if (version < 4) {
    await db.withTransactionAsync(async () => {
      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS body_measurements (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          measuredAt INTEGER NOT NULL,
          weightKg REAL NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_body_measuredAt ON body_measurements (measuredAt DESC);
      `);
      await db.execAsync('PRAGMA user_version = 4;');
    });
  }

  if (version < 5) {
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'sessions', 'movingTimeSec', 'INTEGER');

      let weightKg = DEFAULT_PROFILE.weightKg;
      let profileMaxHr = DEFAULT_PROFILE.maxHr;
      const profileRow = await db.getFirstAsync<{ value: string }>(
        "SELECT value FROM settings WHERE key = 'profile';",
      );
      if (profileRow) {
        try {
          const p = JSON.parse(profileRow.value);
          if (typeof p.weightKg === 'number') weightKg = p.weightKg;
          if (typeof p.maxHr === 'number') profileMaxHr = p.maxHr;
        } catch {
        }
      }

      const velos = await db.getAllAsync<{
        id: number;
        distanceM: number | null;
        elevationGainM: number | null;
        avgHr: number | null;
        source: string | null;
      }>("SELECT id, distanceM, elevationGainM, avgHr, source FROM sessions WHERE type = 'velo';");

      for (const s of velos) {
        const pts = await db.getAllAsync<{ ts: number; lat: number; lon: number; speedKmh: number | null }>(
          'SELECT ts, lat, lon, speedKmh FROM track_points WHERE sessionId = ? ORDER BY ts ASC;',
          s.id,
        );
        const moving = movingTimeSec(pts);
        if (moving <= 0) continue;

        const avgSpeedKmh =
          s.distanceM != null ? s.distanceM / 1000 / (moving / 3600) : null;
        const calories =
          s.source == null
            ? estimateCalories({
                type: 'velo',
                weightKg,
                durationSec: moving,
                avgSpeedKmh,
                elevationGainM: s.elevationGainM,
                avgHr: s.avgHr,
                maxHr: profileMaxHr,
              })
            : null;

        if (avgSpeedKmh != null && calories != null) {
          await db.runAsync(
            'UPDATE sessions SET movingTimeSec = ?, avgSpeedKmh = ?, calories = ? WHERE id = ?;',
            moving,
            avgSpeedKmh,
            calories,
            s.id,
          );
        } else if (avgSpeedKmh != null) {
          await db.runAsync(
            'UPDATE sessions SET movingTimeSec = ?, avgSpeedKmh = ? WHERE id = ?;',
            moving,
            avgSpeedKmh,
            s.id,
          );
        } else {
          await db.runAsync('UPDATE sessions SET movingTimeSec = ? WHERE id = ?;', moving, s.id);
        }
      }
      await db.execAsync('PRAGMA user_version = 5;');
    });
  }

  if (version < 6) {
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'muscu_sets', 'difficulty', 'TEXT');
      await db.execAsync('PRAGMA user_version = 6;');
    });
  }

  if (version < 7) {
    await db.withTransactionAsync(async () => {
      await db.execAsync('CREATE INDEX IF NOT EXISTS idx_sets_exercise ON muscu_sets (exercise);');
      await db.execAsync('PRAGMA user_version = 7;');
    });
  }
}

export async function getSchemaVersion(): Promise<number> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version;');
  return row?.user_version ?? 0;
}

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

export async function deleteSetting(key: string): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM settings WHERE key = ?;', key);
}

const DEFAULT_PROFILE: Profile = {
  weightKg: 70,
  heightCm: 175,
  maxHr: 190,
  goal: 'hypertrophie',
  sex: null,
};

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
    | 'movingTimeSec'
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

export async function changeSessionType(id: number, to: ActivityType): Promise<Session | null> {
  const session = await getSession(id);
  if (!session) return null;
  const changes = retypeChanges(session, to, await getProfile());
  if (!changes) return null;

  const db = await getDb();
  await db.runAsync(
    'UPDATE sessions SET type = ?, calories = ?, avgCadence = ?, maxCadence = ? WHERE id = ?;',
    changes.type,
    changes.calories,
    changes.avgCadence,
    changes.maxCadence,
    id,
  );
  return { ...session, ...changes };
}

export async function getSession(id: number): Promise<Session | null> {
  const db = await getDb();
  return db.getFirstAsync<Session>('SELECT * FROM sessions WHERE id = ?;', id);
}

export async function finalizeSession(
  id: number,
  patch: SessionUpdate,
  points: Omit<TrackPoint, 'id' | 'sessionId'>[],
): Promise<void> {
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await db.runAsync('DELETE FROM track_points WHERE sessionId = ?;', id);
    await insertTrackPointRows(db, id, points);
    const keys = Object.keys(patch) as (keyof SessionUpdate)[];
    if (keys.length > 0) {
      const assignments = keys.map((k) => `${k} = ?`).join(', ');
      const values = keys.map((k) => patch[k] ?? null);
      await db.runAsync(`UPDATE sessions SET ${assignments} WHERE id = ?;`, ...values, id);
    }
  });
}

export async function listInProgressSessions(type?: ActivityType): Promise<Session[]> {
  const db = await getDb();
  const clause = type ? ' AND type = ?' : '';
  const params = type ? [type] : [];
  return db.getAllAsync<Session>(
    `SELECT * FROM sessions WHERE endedAt IS NULL${clause} ORDER BY startedAt ASC;`,
    ...params,
  );
}

export type ListSessionsOptions = {
  limit?: number;
  offset?: number;
  type?: ActivityType;
  search?: string;
  fromMs?: number;
  toMs?: number;
};

export async function listSessions(
  optsOrLimit: number | ListSessionsOptions = 100,
): Promise<Session[]> {
  const opts: ListSessionsOptions =
    typeof optsOrLimit === 'number' ? { limit: optsOrLimit } : optsOrLimit;
  const { limit = 100, offset = 0, type, search, fromMs, toMs } = opts;

  const where: string[] = ['s.endedAt IS NOT NULL'];
  const params: (string | number)[] = [];

  if (type) {
    where.push('s.type = ?');
    params.push(type);
  }
  if (fromMs != null) {
    where.push('s.startedAt >= ?');
    params.push(fromMs);
  }
  if (toMs != null) {
    where.push('s.startedAt < ?');
    params.push(toMs);
  }
  const trimmed = search?.trim();
  if (trimmed) {
    const escaped = trimmed.replace(/[\\%_]/g, '\\$&');
    const like = `%${escaped}%`;
    where.push(
      `(s.type LIKE ? ESCAPE '\\' OR IFNULL(s.notes, '') LIKE ? ESCAPE '\\' OR s.id IN (
         SELECT sessionId FROM muscu_sets WHERE exercise LIKE ? ESCAPE '\\'
       ))`,
    );
    params.push(like, like, like);
  }

  const sql = `SELECT s.*,
                      COUNT(ms.id)                AS setCount,
                      COUNT(DISTINCT ms.exercise) AS exerciseCount
                 FROM sessions s
                 LEFT JOIN muscu_sets ms ON ms.sessionId = s.id
                WHERE ${where.join(' AND ')}
                GROUP BY s.id
                ORDER BY s.startedAt DESC LIMIT ? OFFSET ?;`;
  params.push(limit, offset);

  const db = await getDb();
  return db.getAllAsync<Session>(sql, ...params);
}

export async function deleteSession(id: number): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM sessions WHERE id = ?;', id);
}

const TRACK_POINT_CHUNK = 100;

async function insertTrackPointRows(
  db: SQLite.SQLiteDatabase,
  sessionId: number,
  points: Omit<TrackPoint, 'id' | 'sessionId'>[],
): Promise<void> {
  if (points.length === 0) return;
  for (let i = 0; i < points.length; i += TRACK_POINT_CHUNK) {
    const chunk = points.slice(i, i + TRACK_POINT_CHUNK);
    const placeholders = chunk.map(() => '(?, ?, ?, ?, ?, ?, ?, ?)').join(', ');
    const values: SQLite.SQLiteBindValue[] = [];
    for (const p of chunk) {
      values.push(sessionId, p.ts, p.lat, p.lon, p.altitude, p.speedKmh, p.hr, p.cadence);
    }
    await db.runAsync(
      `INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence) VALUES ${placeholders};`,
      ...values,
    );
  }
}

export async function insertTrackPoints(
  sessionId: number,
  points: Omit<TrackPoint, 'id' | 'sessionId'>[],
): Promise<void> {
  if (points.length === 0) return;
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await insertTrackPointRows(db, sessionId, points);
  });
}

export type ImportedSessionRow = {
  type: ActivityType;
  startedAt: number;
  endedAt: number;
  durationSec: number;
  movingTimeSec: number | null;
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

export async function insertImportedSession(
  session: ImportedSessionRow,
  points: Omit<TrackPoint, 'id' | 'sessionId'>[],
): Promise<'imported' | 'duplicate'> {
  const db = await getDb();
  let result: 'imported' | 'duplicate' = 'imported';
  await db.withTransactionAsync(async () => {
    const res = await db.runAsync(
      `INSERT INTO sessions
         (type, startedAt, endedAt, durationSec, movingTimeSec, notes, avgHr, maxHr, distanceM,
          avgSpeedKmh, maxSpeedKmh, elevationGainM, avgCadence, maxCadence, calories,
          source, externalId)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(externalId) WHERE externalId IS NOT NULL DO NOTHING;`,
      session.type,
      session.startedAt,
      session.endedAt,
      session.durationSec,
      session.movingTimeSec,
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
    await insertTrackPointRows(db, res.lastInsertRowId, points);
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

export type MuscuSetInput = Omit<MuscuSet, 'id' | 'sessionId' | 'difficulty'> & {
  difficulty?: Difficulty | null;
};

async function replaceMuscuSetsIn(
  db: SQLite.SQLiteDatabase,
  sessionId: number,
  sets: MuscuSetInput[],
): Promise<void> {
  await db.runAsync('DELETE FROM muscu_sets WHERE sessionId = ?;', sessionId);
  if (sets.length === 0) return;
  const stmt = await db.prepareAsync(
    'INSERT INTO muscu_sets (sessionId, exercise, setIndex, reps, weightKg, difficulty) VALUES (?, ?, ?, ?, ?, ?);',
  );
  try {
    for (const s of sets) {
      await stmt.executeAsync(sessionId, s.exercise, s.setIndex, s.reps, s.weightKg, s.difficulty ?? null);
    }
  } finally {
    await stmt.finalizeAsync();
  }
}

export async function replaceMuscuSets(
  sessionId: number,
  sets: MuscuSetInput[],
): Promise<void> {
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await replaceMuscuSetsIn(db, sessionId, sets);
  });
}

export async function saveMuscuSession(
  id: number | null,
  startedAt: number,
  patch: SessionUpdate,
  sets: MuscuSetInput[],
): Promise<number> {
  const db = await getDb();
  let sessionId = id;
  await db.withTransactionAsync(async () => {
    if (sessionId == null) {
      const res = await db.runAsync(
        'INSERT INTO sessions (type, startedAt, durationSec) VALUES (?, ?, 0);',
        'muscu',
        startedAt,
      );
      sessionId = res.lastInsertRowId;
    }
    const keys = Object.keys(patch) as (keyof SessionUpdate)[];
    if (keys.length > 0) {
      const assignments = keys.map((k) => `${k} = ?`).join(', ');
      const values = keys.map((k) => patch[k] ?? null);
      await db.runAsync(`UPDATE sessions SET ${assignments} WHERE id = ?;`, ...values, sessionId);
    }
    await replaceMuscuSetsIn(db, sessionId!, sets);
  });
  return sessionId!;
}

export async function getMuscuSets(sessionId: number): Promise<MuscuSet[]> {
  const db = await getDb();
  return db.getAllAsync<MuscuSet>(
    'SELECT * FROM muscu_sets WHERE sessionId = ? ORDER BY setIndex ASC, id ASC;',
    sessionId,
  );
}

export async function lastWeightByExercise(
  names: string[],
): Promise<Record<string, number>> {
  if (names.length === 0) return {};
  const db = await getDb();
  const placeholders = names.map(() => '?').join(', ');
  const rows = await db.getAllAsync<{ exercise: string; weightKg: number }>(
    `SELECT exercise, weightKg FROM (
       SELECT ms.exercise AS exercise,
              ms.weightKg AS weightKg,
              ROW_NUMBER() OVER (
                PARTITION BY ms.exercise
                ORDER BY s.startedAt DESC, ms.weightKg DESC
              ) AS rn
         FROM muscu_sets ms
         JOIN sessions s ON s.id = ms.sessionId
        WHERE s.type = 'muscu' AND s.endedAt IS NOT NULL AND ms.exercise IN (${placeholders})
     ) WHERE rn = 1;`,
    ...names,
  );
  const out: Record<string, number> = {};
  for (const r of rows) out[r.exercise] = r.weightKg;
  return out;
}

export type ExerciseSummary = {
  exercise: string;
  sessions: number;
  lastWeightKg: number;
  lastAt: number;
  lastDifficulty: Difficulty | null;
};

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
              LIMIT 1) AS lastWeightKg,
            (SELECT m3.difficulty
               FROM muscu_sets m3
               JOIN sessions s3 ON s3.id = m3.sessionId
              WHERE m3.exercise = ms.exercise AND s3.endedAt IS NOT NULL
              ORDER BY s3.startedAt DESC
              LIMIT 1) AS lastDifficulty
       FROM muscu_sets ms
       JOIN sessions s ON s.id = ms.sessionId
      WHERE s.endedAt IS NOT NULL
      GROUP BY ms.exercise
      ORDER BY lastAt DESC;`,
  );
}

export type ExercisePoint = {
  sessionId: number;
  startedAt: number;
  maxWeightKg: number;
  topReps: number;
  volume: number;
  sets: number;
  difficulty: Difficulty | null;
};

export async function exerciseHistory(name: string): Promise<ExercisePoint[]> {
  const db = await getDb();
  return db.getAllAsync<ExercisePoint>(
    `SELECT s.id AS sessionId,
            s.startedAt AS startedAt,
            MAX(ms.weightKg) AS maxWeightKg,
            (SELECT m2.reps
               FROM muscu_sets m2
              WHERE m2.sessionId = s.id AND m2.exercise = ms.exercise
              ORDER BY m2.weightKg DESC, m2.reps DESC
              LIMIT 1) AS topReps,
            SUM(ms.reps * ms.weightKg) AS volume,
            COUNT(*) AS sets,
            (SELECT m3.difficulty
               FROM muscu_sets m3
              WHERE m3.sessionId = s.id AND m3.exercise = ms.exercise
                AND m3.difficulty IS NOT NULL
              LIMIT 1) AS difficulty
       FROM muscu_sets ms
       JOIN sessions s ON s.id = ms.sessionId
      WHERE s.endedAt IS NOT NULL AND ms.exercise = ?
      GROUP BY s.id
      ORDER BY s.startedAt ASC;`,
    name,
  );
}

export async function logBodyWeight(weightKg: number, measuredAt: number): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    'INSERT INTO body_measurements (measuredAt, weightKg) VALUES (?, ?);',
    measuredAt,
    weightKg,
  );
  await syncProfileWeight();
}

export async function deleteBodyMeasurement(id: number): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM body_measurements WHERE id = ?;', id);
  await syncProfileWeight();
}

async function syncProfileWeight(): Promise<void> {
  const latest = await latestBodyMeasurement();
  if (!latest) return;
  const profile = await getProfile();
  if (profile.weightKg !== latest.weightKg) {
    await saveProfile({ ...profile, weightKg: latest.weightKg });
  }
}

export async function listBodyMeasurements(limit = 1000): Promise<BodyMeasurement[]> {
  const db = await getDb();
  return db.getAllAsync<BodyMeasurement>(
    'SELECT * FROM body_measurements ORDER BY measuredAt DESC, id DESC LIMIT ?;',
    limit,
  );
}

export async function latestBodyMeasurement(): Promise<BodyMeasurement | null> {
  const db = await getDb();
  return db.getFirstAsync<BodyMeasurement>(
    'SELECT * FROM body_measurements ORDER BY measuredAt DESC, id DESC LIMIT 1;',
  );
}

export async function statsBetween(
  fromMs: number,
  toMs: number,
  type?: ActivityType,
): Promise<PeriodStats> {
  const db = await getDb();
  const typeClause = type ? ' AND type = ?' : '';
  const params: (string | number)[] = type ? [fromMs, toMs, type] : [fromMs, toMs];
  const row = await db.getFirstAsync<PeriodStats>(
    `SELECT
       COUNT(*) AS sessionCount,
       COALESCE(SUM(COALESCE(movingTimeSec, durationSec)), 0) AS totalDurationSec,
       COALESCE(SUM(distanceM), 0) AS totalDistanceM,
       COALESCE(SUM(calories), 0) AS totalCalories
     FROM sessions
     WHERE endedAt IS NOT NULL AND startedAt >= ? AND startedAt < ?${typeClause};`,
    ...params,
  );
  return (
    row ?? { sessionCount: 0, totalDurationSec: 0, totalDistanceM: 0, totalCalories: 0 }
  );
}

export async function statsSince(sinceMs: number): Promise<PeriodStats> {
  return statsBetween(sinceMs, Number.MAX_SAFE_INTEGER);
}

export async function tonnageBetween(fromMs: number, toMs: number): Promise<number> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ tonnage: number }>(
    `SELECT COALESCE(SUM(ms.reps * ms.weightKg), 0) AS tonnage
       FROM muscu_sets ms
       JOIN sessions s ON s.id = ms.sessionId
      WHERE s.type = 'muscu' AND s.endedAt IS NOT NULL
        AND s.startedAt >= ? AND s.startedAt < ?;`,
    fromMs,
    toMs,
  );
  return row?.tonnage ?? 0;
}

export async function dailyDurations(days: number): Promise<{ day: string; durationSec: number }[]> {
  const db = await getDb();
  return db.getAllAsync<{ day: string; durationSec: number }>(
    `SELECT date(startedAt / 1000, 'unixepoch', 'localtime') AS day,
            COALESCE(SUM(COALESCE(movingTimeSec, durationSec)), 0) AS durationSec
     FROM sessions
     WHERE endedAt IS NOT NULL
       AND startedAt >= ?
     GROUP BY day
     ORDER BY day ASC;`,
    Date.now() - days * 86400_000,
  );
}

export type RecordKind = 'distance' | 'elevation' | 'duration' | 'speed';
export type SessionRecord = { kind: RecordKind; scope: 'year' | 'all' };

const RECORD_COLUMNS: Record<RecordKind, string> = {
  distance: 'distanceM',
  elevation: 'elevationGainM',
  duration: 'durationSec',
  speed: 'avgSpeedKmh',
};

function recordColumn(kind: RecordKind, s: Session): { expr: string; value: number | null } {
  if (kind === 'duration' && s.type === 'velo') {
    return { expr: 'COALESCE(movingTimeSec, durationSec)', value: s.movingTimeSec ?? s.durationSec };
  }
  const col = RECORD_COLUMNS[kind];
  return { expr: col, value: s[col as keyof Session] as number | null };
}

export async function sessionRecords(s: Session): Promise<SessionRecord[]> {
  const db = await getDb();
  const kinds: RecordKind[] = isGpsActivity(s.type)
    ? ['distance', 'elevation', 'duration', 'speed']
    : ['duration'];

  const year = new Date(s.startedAt).getFullYear();
  const yearStart = new Date(year, 0, 1).getTime();
  const yearEnd = new Date(year + 1, 0, 1).getTime();

  const out: SessionRecord[] = [];
  for (const kind of kinds) {
    const { expr: col, value } = recordColumn(kind, s);
    if (value == null || value <= 0) continue;

    const greaterAll = await db.getFirstAsync<{ n: number }>(
      `SELECT COUNT(*) AS n FROM sessions
       WHERE type = ? AND endedAt IS NOT NULL AND id <> ? AND ${col} > ?;`,
      s.type,
      s.id,
      value,
    );
    if ((greaterAll?.n ?? 0) === 0) {
      out.push({ kind, scope: 'all' });
      continue;
    }

    const greaterYear = await db.getFirstAsync<{ n: number }>(
      `SELECT COUNT(*) AS n FROM sessions
       WHERE type = ? AND endedAt IS NOT NULL AND id <> ?
         AND startedAt >= ? AND startedAt < ? AND ${col} > ?;`,
      s.type,
      s.id,
      yearStart,
      yearEnd,
      value,
    );
    if ((greaterYear?.n ?? 0) === 0) out.push({ kind, scope: 'year' });
  }
  return out;
}

export async function clearAllData(): Promise<void> {
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await db.execAsync('DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions;');
  });
}

export async function clearAllDataIncludingSettings(): Promise<void> {
  const db = await getDb();
  const keep = [...RESET_KEEP_KEYS];
  const placeholders = keep.map(() => '?').join(', ');
  await db.withTransactionAsync(async () => {
    await db.execAsync(
      'DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions; DELETE FROM body_measurements;',
    );
    await db.runAsync(`DELETE FROM settings WHERE key NOT IN (${placeholders});`, ...keep);
  });
}

const BACKUP_EXCLUDED_KEYS = new Set(['backup_s3', 'backup_last', 'map_style_url']);

const RESET_KEEP_KEYS = new Set(['backup_s3', 'backup_last']);

export type DbSnapshot = {
  sessions: Session[];
  trackPoints: TrackPoint[];
  muscuSets: MuscuSet[];
  bodyMeasurements?: BodyMeasurement[];
  settings: { key: string; value: string }[];
};

export async function exportAll(): Promise<DbSnapshot> {
  const db = await getDb();
  const [sessions, trackPoints, muscuSets, bodyMeasurements, allSettings] = await Promise.all([
    db.getAllAsync<Session>('SELECT * FROM sessions;'),
    db.getAllAsync<TrackPoint>('SELECT * FROM track_points;'),
    db.getAllAsync<MuscuSet>('SELECT * FROM muscu_sets;'),
    db.getAllAsync<BodyMeasurement>('SELECT * FROM body_measurements;'),
    db.getAllAsync<{ key: string; value: string }>('SELECT key, value FROM settings;'),
  ]);
  const settings = allSettings.filter((s) => !BACKUP_EXCLUDED_KEYS.has(s.key));
  return { sessions, trackPoints, muscuSets, bodyMeasurements, settings };
}

export async function importAll(snap: DbSnapshot): Promise<void> {
  const db = await getDb();
  const n = (v: unknown): SQLite.SQLiteBindValue => (v === undefined ? null : (v as SQLite.SQLiteBindValue));
  await db.withTransactionAsync(async () => {
    await db.execAsync(
      'DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions; DELETE FROM body_measurements;',
    );

    const sessions = snap.sessions ?? [];
    if (sessions.length > 0) {
      const stmt = await db.prepareAsync(
        `INSERT INTO sessions
           (id, type, startedAt, endedAt, durationSec, movingTimeSec, notes, avgHr, maxHr,
            distanceM, avgSpeedKmh, maxSpeedKmh, elevationGainM, avgCadence, maxCadence, calories,
            source, externalId)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);`,
      );
      try {
        for (const s of sessions) {
          await stmt.executeAsync(
            n(s.id), n(s.type), n(s.startedAt), n(s.endedAt), n(s.durationSec), n(s.movingTimeSec),
            n(s.notes), n(s.avgHr), n(s.maxHr), n(s.distanceM), n(s.avgSpeedKmh), n(s.maxSpeedKmh),
            n(s.elevationGainM), n(s.avgCadence), n(s.maxCadence), n(s.calories),
            n(s.source), n(s.externalId),
          );
        }
      } finally {
        await stmt.finalizeAsync();
      }
    }

    const trackPoints = snap.trackPoints ?? [];
    if (trackPoints.length > 0) {
      const stmt = await db.prepareAsync(
        `INSERT INTO track_points (id, sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);`,
      );
      try {
        for (const p of trackPoints) {
          await stmt.executeAsync(
            n(p.id), n(p.sessionId), n(p.ts), n(p.lat), n(p.lon),
            n(p.altitude), n(p.speedKmh), n(p.hr), n(p.cadence),
          );
        }
      } finally {
        await stmt.finalizeAsync();
      }
    }

    const bodyMeasurements = snap.bodyMeasurements ?? [];
    if (bodyMeasurements.length > 0) {
      const stmt = await db.prepareAsync(
        'INSERT INTO body_measurements (id, measuredAt, weightKg) VALUES (?, ?, ?);',
      );
      try {
        for (const b of bodyMeasurements) {
          await stmt.executeAsync(n(b.id), n(b.measuredAt), n(b.weightKg));
        }
      } finally {
        await stmt.finalizeAsync();
      }
    }

    const muscuSets = snap.muscuSets ?? [];
    if (muscuSets.length > 0) {
      const stmt = await db.prepareAsync(
        `INSERT INTO muscu_sets (id, sessionId, exercise, setIndex, reps, weightKg, difficulty)
         VALUES (?, ?, ?, ?, ?, ?, ?);`,
      );
      try {
        for (const m of muscuSets) {
          await stmt.executeAsync(
            n(m.id), n(m.sessionId), n(m.exercise), n(m.setIndex), n(m.reps), n(m.weightKg),
            m.difficulty ?? null,
          );
        }
      } finally {
        await stmt.finalizeAsync();
      }
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
