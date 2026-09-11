// Accès SQLite local. Toutes les données restent sur l'appareil.
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

/**
 * Version courante du schéma SQLite — doit suivre la dernière migration de
 * `migrate()` ci-dessous (bump à chaque nouveau bloc `if (version < N)`).
 * Sert à estampiller les sauvegardes pour refuser une restauration issue d'une
 * version plus récente (cf. lib/backup.ts).
 */
export const SCHEMA_VERSION = 7;

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

/** Ouvre (une seule fois) la base et applique les migrations. */
export function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await SQLite.openDatabaseAsync(DB_NAME);
      await migrate(db);
      return db;
    })().catch((e) => {
      // Ne jamais mettre en cache une promesse rejetée : sinon un échec de
      // migration (ou une ouverture ratée) condamnerait TOUS les accès base à
      // chaque lancement, pour toujours. On réarme pour permettre une nouvelle
      // tentative au prochain getDb() (ex. après un redémarrage de l'app).
      dbPromise = null;
      throw e;
    });
  }
  return dbPromise;
}

/** Vrai si `table` possède déjà la colonne `column`. */
async function hasColumn(
  db: SQLite.SQLiteDatabase,
  table: string,
  column: string,
): Promise<boolean> {
  const cols = await db.getAllAsync<{ name: string }>(`PRAGMA table_info(${table});`);
  return cols.some((c) => c.name === column);
}

/**
 * Ajoute une colonne seulement si elle n'existe pas déjà. SQLite ne connaît pas
 * `ADD COLUMN IF NOT EXISTS` : sans cette garde, un bloc de migration rejoué
 * après une interruption (app tuée entre l'ALTER et le bump du pragma) lèverait
 * « duplicate column name » et briquerait la base.
 */
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
  // Hors transaction : WAL/foreign_keys sont des pragmas de connexion.
  await db.execAsync('PRAGMA journal_mode = WAL;');
  await db.execAsync('PRAGMA foreign_keys = ON;');

  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version;');
  const version = row?.user_version ?? 0;

  // Chaque bloc (DDL + backfill + bump du pragma) tourne dans UNE transaction :
  // si l'app est tuée en cours de route, tout est annulé et le bloc rejoue
  // proprement au prochain lancement (au lieu de laisser un schéma à moitié
  // migré avec un `user_version` incohérent). Les ajouts de colonnes passent par
  // `addColumn` (idempotents) en défense de profondeur pour les bases déjà
  // partiellement migrées par une version antérieure à ce correctif.
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
    // Capteurs de cadence/vitesse vélo (profil BLE CSC).
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'sessions', 'avgCadence', 'REAL');
      await addColumn(db, 'sessions', 'maxCadence', 'REAL');
      await addColumn(db, 'track_points', 'cadence', 'REAL');
      await db.execAsync('PRAGMA user_version = 2;');
    });
  }

  if (version < 3) {
    // Import Strava : provenance + clé de déduplication (index unique partiel
    // pour rendre une ré-importation idempotente, sans gêner les séances natives
    // dont externalId reste NULL).
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
    // Journal de poids corporel : une ligne par pesée. La pesée la plus
    // récente sert de poids de référence (calories, charges conseillées) via
    // la synchronisation du profil dans logBodyWeight().
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
    // Temps « en mouvement » (vélo) : durée hors arrêts, façon Strava. On ajoute
    // la colonne puis on la rétro-calcule depuis les points GPS des sorties déjà
    // enregistrées — et on en dérive une vitesse moyenne « en mouvement » plus
    // honnête que la moyenne sur le temps total (cas du chrono oublié à l'arrêt).
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'sessions', 'movingTimeSec', 'INTEGER');

      // Poids/FC max courants pour ré-estimer les calories des séances natives
      // (lecture directe du réglage : appeler getProfile() rouvrirait getDb(),
      // dont la promesse est encore en cours ici → interblocage).
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
          // réglage illisible : on garde les valeurs par défaut.
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

        // Vitesse moyenne sur le temps en mouvement (uniquement si la distance est
        // connue, pour ne pas écraser une valeur par un 0 trompeur).
        const avgSpeedKmh =
          s.distanceM != null ? s.distanceM / 1000 / (moving / 3600) : null;
        // Calories : on ne ré-estime que les séances natives — pour un import
        // Strava la valeur du fichier fait foi, on ne la remplace pas.
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
    // Ressenti d'effort par exercice (facile / moyen / dur), dénormalisé sur
    // chaque série de l'exercice dans la séance. Nullable : séances anciennes et
    // imports Strava restent à NULL. Alimente le conseil de progression
    // (lib/progression-advice.ts). 100 % local, aucune dépendance réseau.
    await db.withTransactionAsync(async () => {
      await addColumn(db, 'muscu_sets', 'difficulty', 'TEXT');
      await db.execAsync('PRAGMA user_version = 6;');
    });
  }

  if (version < 7) {
    // Index sur `muscu_sets(exercise)` : les requêtes par exercice
    // (`exerciseHistory`, `listMuscuExercises`, export coach) filtrent sur cette
    // colonne seule, que l'index composite `idx_sets_session` (préfixe
    // sessionId) ne couvre pas. Sans migration de données — simple index.
    await db.withTransactionAsync(async () => {
      await db.execAsync('CREATE INDEX IF NOT EXISTS idx_sets_exercise ON muscu_sets (exercise);');
      await db.execAsync('PRAGMA user_version = 7;');
    });
  }
}

/** Version du schéma effectivement appliquée à la base (PRAGMA user_version). */
export async function getSchemaVersion(): Promise<number> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version;');
  return row?.user_version ?? 0;
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

/**
 * Change le type d'une séance déjà enregistrée (vélo ↔ course ↔ marche).
 *
 * Le type n'est pas qu'une étiquette : les calories sont ré-estimées avec la
 * table MET de la nouvelle activité et la cadence du capteur vélo est effacée
 * si on passe à pied (cf. `lib/session-retype.ts`). Tout part dans un seul
 * UPDATE, donc pas d'état intermédiaire incohérent.
 *
 * Renvoie la séance mise à jour, ou `null` si elle n'existe pas ou si le
 * changement n'est pas permis (même type, ou musculation impliquée) — l'appelant
 * n'a alors rien à rafraîchir.
 */
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

/**
 * Finalise une séance vélo pré-créée (au `begin()`) de façon ATOMIQUE : réécrit
 * l'intégralité de ses points GPS et applique les agrégats + `endedAt` dans une
 * seule transaction. Idempotent : un réessai après un échec (ou un flush
 * incrémental antérieur) réécrit proprement le même résultat, sans doublon de
 * séance ni point orphelin. C'est le remplaçant du triptyque non transactionnel
 * createSession → updateSession → insertTrackPoints côté écran.
 */
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

/** Séances « en cours » (endedAt NULL), optionnellement filtrées par type. */
export async function listInProgressSessions(type?: ActivityType): Promise<Session[]> {
  const db = await getDb();
  const clause = type ? ' AND type = ?' : '';
  const params = type ? [type] : [];
  return db.getAllAsync<Session>(
    `SELECT * FROM sessions WHERE endedAt IS NULL${clause} ORDER BY startedAt ASC;`,
    ...params,
  );
}

/** Options de filtrage de l'historique. Compatibilité ascendante : passer un
 * nombre garde l'ancien comportement (limite seule, pas d'offset, pas de filtre). */
export type ListSessionsOptions = {
  limit?: number;
  /** Décale la fenêtre (pour la pagination). */
  offset?: number;
  /** Restreint à un type d'activité. */
  type?: ActivityType;
  /** Recherche libre, insensible à la casse, sur :
   *  - le code du type (« velo » / « muscu »),
   *  - les notes de séance,
   *  - les noms d'exercices muscu rattachés. */
  search?: string;
  /** Borne basse de `startedAt` (ms epoch, inclus). */
  fromMs?: number;
  /** Borne haute de `startedAt` (ms epoch, exclus). */
  toMs?: number;
};

export async function listSessions(
  optsOrLimit: number | ListSessionsOptions = 100,
): Promise<Session[]> {
  const opts: ListSessionsOptions =
    typeof optsOrLimit === 'number' ? { limit: optsOrLimit } : optsOrLimit;
  const { limit = 100, offset = 0, type, search, fromMs, toMs } = opts;

  // Prédicats qualifiés par l'alias `s` : la requête joint `muscu_sets` (alias
  // `ms`) pour agréger la complétion, donc les colonnes nues seraient ambiguës.
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
    // SQLite : LIKE est insensible à la casse pour l'ASCII par défaut. On
    // matche sur le code de type (« velo »/« muscu »), les notes ou un
    // exercice muscu rattaché. Les jokers `%` `_` (et l'échappement `\`) sont
    // neutralisés dans la saisie utilisateur — sinon taper « 50 % » ou « a_b »
    // matcherait n'importe quoi. `ESCAPE '\'` active la séquence d'échappement.
    const escaped = trimmed.replace(/[\\%_]/g, '\\$&');
    const like = `%${escaped}%`;
    where.push(
      `(s.type LIKE ? ESCAPE '\\' OR IFNULL(s.notes, '') LIKE ? ESCAPE '\\' OR s.id IN (
         SELECT sessionId FROM muscu_sets WHERE exercise LIKE ? ESCAPE '\\'
       ))`,
    );
    params.push(like, like, like);
  }

  // Agrégat de lecture : nombre de séries et d'exercices distincts par séance
  // (complétion muscu, affichée à la place de la durée). Vélo → 0. Pas de
  // migration : c'est purement un calcul à la lecture.
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

// ---------------------------------------------------------------------------
// Points GPS
// ---------------------------------------------------------------------------

/** Lignes par INSERT multi-valeurs : 100 × 8 colonnes = 800 paramètres liés,
 *  sous la limite SQLite (999) tout en réduisant les allers-retours du pont. */
const TRACK_POINT_CHUNK = 100;

/**
 * Insère en lot des points GPS rattachés à une séance (l'id est auto-incrémenté).
 * À appeler dans une transaction déjà ouverte. Une longue sortie peut compter
 * plusieurs milliers de points : on regroupe les lignes en INSERT multi-valeurs
 * (une exécution par paquet de 100 au lieu d'un aller-retour du pont par point,
 * soit ~12 000 → ~120 pour 3 h de sortie). Partagé par `insertTrackPoints` et
 * `insertImportedSession`.
 */
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

// ---------------------------------------------------------------------------
// Import de séances externes (Strava) — déduplication par externalId
// ---------------------------------------------------------------------------

/** Une séance prête à insérer depuis une source externe (fichier Strava). */
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

// ---------------------------------------------------------------------------
// Séries de musculation
// ---------------------------------------------------------------------------

/** Une série muscu prête à écrire (difficulty facultatif). */
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
  // `difficulty` est facultatif à l'écriture (séries sans ressenti noté) ; il
  // reste un champ plein (nullable) à la lecture via `MuscuSet`.
  sets: MuscuSetInput[],
): Promise<void> {
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await replaceMuscuSetsIn(db, sessionId, sets);
  });
}

/**
 * Enregistre une séance muscu terminée de façon ATOMIQUE : création de la ligne
 * (si `id` absent), agrégats + `endedAt`, et remplacement des séries, le tout
 * dans une seule transaction. En cas d'échec, tout est annulé : ni séance
 * visible sans séries, ni séance orpheline `endedAt IS NULL`. Renvoie l'id créé
 * (ou réutilisé). Remplace le triptyque createSession → updateSession →
 * replaceMuscuSets côté écran.
 */
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

/**
 * Dernière charge enregistrée par exercice : la série la plus lourde de la
 * séance muscu terminée la plus récente contenant cet exercice. Sert d'amorce
 * de progression au chargement d'un programme. La correspondance se fait sur le
 * libellé exact — les templates réutilisent les mêmes noms.
 */
export async function lastWeightByExercise(
  names: string[],
): Promise<Record<string, number>> {
  if (names.length === 0) return {};
  const db = await getDb();
  // Une seule requête pour tous les exercices : on prend la charge max de la
  // dernière séance muscu terminée qui contient chaque exercice (window function).
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

/** Ligne d'index : un exercice connu et son dernier état. */
export type ExerciseSummary = {
  exercise: string;
  sessions: number;
  lastWeightKg: number;
  lastAt: number;
  /** Ressenti noté à la dernière séance (indice de progression dans l'index). */
  lastDifficulty: Difficulty | null;
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

/** Un point de progression : l'état d'un exercice sur une séance donnée. */
export type ExercisePoint = {
  sessionId: number;
  startedAt: number;
  maxWeightKg: number;
  topReps: number;
  volume: number;
  sets: number;
  /** Ressenti noté pour cet exercice sur la séance (`null` si non noté). */
  difficulty: Difficulty | null;
};

/**
 * Historique d'un exercice, une ligne par séance terminée, du plus ancien au
 * plus récent. `topReps` correspond aux reps de la série la plus lourde.
 *
 * On NE s'appuie PAS sur « colonne nue = ligne du MAX » : cette garantie SQLite
 * ne vaut qu'avec un seul agrégat min/max dans la requête, or on a aussi besoin
 * de `difficulty`. On isole donc `topReps` et `difficulty` dans des sous-requêtes
 * corrélées explicites (sinon `topReps` pouvait venir d'une série d'échauffement,
 * gonflant le 1RM Epley et les records).
 */
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

// ---------------------------------------------------------------------------
// Journal de poids corporel
// ---------------------------------------------------------------------------

/**
 * Enregistre une pesée, puis aligne le poids du profil sur la pesée la plus
 * récente : le profil reste l'unique source consommée par les calculs
 * (calories des séances, charges conseillées, import Strava), le journal n'est
 * que l'historique.
 */
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

/** Recale le poids du profil sur la dernière pesée restante (s'il y en a une). */
async function syncProfileWeight(): Promise<void> {
  const latest = await latestBodyMeasurement();
  if (!latest) return;
  const profile = await getProfile();
  if (profile.weightKg !== latest.weightKg) {
    await saveProfile({ ...profile, weightKg: latest.weightKg });
  }
}

/** Pesées du journal, de la plus récente à la plus ancienne. */
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

// ---------------------------------------------------------------------------
// Statistiques
// ---------------------------------------------------------------------------

/** Agrégats sur une fenêtre `[fromMs, toMs)` (ms epoch), éventuellement
 * restreints à un type d'activité (sinon tous types confondus). */
export async function statsBetween(
  fromMs: number,
  toMs: number,
  type?: ActivityType,
): Promise<PeriodStats> {
  const db = await getDb();
  const typeClause = type ? ' AND type = ?' : '';
  const params: (string | number)[] = type ? [fromMs, toMs, type] : [fromMs, toMs];
  const row = await db.getFirstAsync<PeriodStats>(
    // Temps en mouvement quand il est connu (vélo), sinon durée totale (muscu) :
    // un chrono oublié à l'arrêt ne gonfle pas le cumul d'effort.
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

/** Agrégats depuis `sinceMs` jusqu'à maintenant. */
export async function statsSince(sinceMs: number): Promise<PeriodStats> {
  return statsBetween(sinceMs, Number.MAX_SAFE_INTEGER);
}

/**
 * Tonnage musculation (Σ reps × charge) des séances terminées dans la fenêtre
 * `[fromMs, toMs)`. Sert au suivi d'objectifs de volume — cohérent avec le
 * volume affiché ailleurs (mêmes séries `muscu_sets`).
 */
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

/** Durée totale d'effort par jour sur les N derniers jours (pour le graphe). */
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

// ---------------------------------------------------------------------------
// Records personnels (façon « PR » Strava)
// ---------------------------------------------------------------------------

/** Métrique sur laquelle une séance peut établir un record. */
export type RecordKind = 'distance' | 'elevation' | 'duration' | 'speed';
/** Portée d'un record : sur l'année de la séance, ou sur toute l'historique. */
export type SessionRecord = { kind: RecordKind; scope: 'year' | 'all' };

// Colonnes correspondantes — liste blanche fermée (jamais d'entrée utilisateur).
const RECORD_COLUMNS: Record<RecordKind, string> = {
  distance: 'distanceM',
  elevation: 'elevationGainM',
  duration: 'durationSec',
  speed: 'avgSpeedKmh',
};

/**
 * Expression SQL et valeur de la séance pour une métrique de record. Pour le
 * vélo, le record de durée se mesure sur le temps en mouvement (hors arrêts)
 * quand il est connu : une sortie dont le chrono a tourné à l'arrêt ne peut pas
 * s'octroyer un faux record de durée. La musculation (sans GPS) garde la durée
 * totale. L'expression vient d'une liste blanche fermée — jamais d'entrée
 * utilisateur, sûre à interpoler.
 */
function recordColumn(kind: RecordKind, s: Session): { expr: string; value: number | null } {
  if (kind === 'duration' && s.type === 'velo') {
    return { expr: 'COALESCE(movingTimeSec, durationSec)', value: s.movingTimeSec ?? s.durationSec };
  }
  const col = RECORD_COLUMNS[kind];
  return { expr: col, value: s[col as keyof Session] as number | null };
}

/**
 * Détermine, pour une séance donnée, les records qu'elle détient parmi les
 * séances du même type. Pour chaque métrique : record « all » si aucune autre
 * séance ne fait mieux, sinon record « year » si aucune ne fait mieux sur la
 * même année civile. La musculation n'a pas de distance/dénivelé → durée seule.
 */
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

/**
 * Efface les séances (points, séries) — garde les réglages ET le journal de
 * poids (page « Poids » distincte, avec sa propre gestion). Transactionnel :
 * une interruption ne laisse pas des points/séries orphelins de leur séance.
 */
export async function clearAllData(): Promise<void> {
  const db = await getDb();
  await db.withTransactionAsync(async () => {
    await db.execAsync('DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions;');
  });
}

/**
 * Réinitialisation complète : séances, points, séries ET réglages (profil,
 * FC max, capteurs appairés, planning, fond de carte…). Ne conserve que les
 * clés propres à l'appareil exclues des sauvegardes (identifiants S3, statut)
 * pour ne pas casser la configuration de sauvegarde locale. Permet d'honorer la
 * promesse « suppression de toutes les données » du formulaire Sécurité Play.
 */
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

// ---------------------------------------------------------------------------
// Sauvegarde / restauration (export complet de la base)
// ---------------------------------------------------------------------------

/**
 * Clés de réglages exclues des sauvegardes (secrets, propres à l'appareil).
 * `map_style_url` y figure car c'est un puits réseau sensible : une sauvegarde
 * falsifiée pourrait y injecter un hôte HTTPS arbitraire, qui recevrait alors
 * la zone du parcours + l'IP de l'appareil au prochain affichage de carte.
 * L'exclure de la restauration ferme ce canal (l'utilisateur ré-active le fond
 * de carte après une restauration) ; `map.ts` re-valide de toute façon à la
 * lecture en défense de profondeur.
 */
const BACKUP_EXCLUDED_KEYS = new Set(['backup_s3', 'backup_last', 'map_style_url']);

/**
 * Réglages préservés par une réinitialisation complète (`clearAllDataIncludingSettings`) :
 * la config de sauvegarde locale, pour ne pas la casser. À NE PAS confondre avec
 * les clés exclues des sauvegardes : `map_style_url` est exclu des sauvegardes
 * mais doit bien être effacé par une réinitialisation (c'est une préférence, pas
 * une config de sauvegarde).
 */
const RESET_KEEP_KEYS = new Set(['backup_s3', 'backup_last']);

export type DbSnapshot = {
  sessions: Session[];
  trackPoints: TrackPoint[];
  muscuSets: MuscuSet[];
  /** Absent des sauvegardes antérieures au schéma 4. */
  bodyMeasurements?: BodyMeasurement[];
  settings: { key: string; value: string }[];
};

/** Lit l'intégralité de la base pour une sauvegarde (hors réglages secrets). */
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

/**
 * Remplace toutes les données locales par celles d'une sauvegarde. Les ids
 * sont conservés (la base est vidée au préalable, dans une transaction).
 * Les réglages secrets de la sauvegarde elle-même ne sont jamais réécrits.
 */
export async function importAll(snap: DbSnapshot): Promise<void> {
  const db = await getDb();
  const n = (v: unknown): SQLite.SQLiteBindValue => (v === undefined ? null : (v as SQLite.SQLiteBindValue));
  await db.withTransactionAsync(async () => {
    await db.execAsync(
      'DELETE FROM track_points; DELETE FROM muscu_sets; DELETE FROM sessions; DELETE FROM body_measurements;',
    );

    // Statements préparés : une restauration peut comporter des dizaines de
    // milliers de points GPS, runAsync re-parse le SQL à chaque appel.
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

    // Sauvegardes d'avant le schéma 4 : pas de journal de poids, table vide.
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
            // `difficulty` est du texte nullable : ne pas passer par n() (force un number).
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
