// Sauvegarde des données vers un stockage S3-compatible auto-hébergé (homelab).
// Sérialise toute la base en un seul objet JSON (écrasé à chaque sauvegarde).
import * as SecureStore from 'expo-secure-store';

import {
  exportAll,
  getSchemaVersion,
  getSetting,
  importAll,
  SCHEMA_VERSION,
  setSetting,
  type DbSnapshot,
} from '@/lib/db';
import { getObject, putObject, type S3Config } from '@/lib/s3';
import { nowMs } from '@/lib/time';

const CONFIG_KEY = 'backup_s3';
const LAST_KEY = 'backup_last';
// Clés du stockage sécurisé (Keystore/Keychain) pour les identifiants S3 :
// on ne laisse pas un secret durable en clair dans la base SQLite. Le reste de
// la config (endpoint, bucket, région…) reste dans `settings` (non secret).
const SECURE_AK_KEY = 'backup_s3_accessKeyId';
const SECURE_SK_KEY = 'backup_s3_secretAccessKey';

/** Champs secrets de la config, isolés du JSON `settings`. */
const SECRET_FIELDS = ['accessKeyId', 'secretAccessKey'] as const;

let secureAvailable: boolean | null = null;
/**
 * Vrai si le stockage sécurisé natif est disponible (Android/iOS). Sur web il
 * ne l'est pas : on retombe alors sur l'ancien comportement (secrets dans le
 * JSON), le web n'étant pas la cible sécurisée de l'app.
 */
async function hasSecureStore(): Promise<boolean> {
  if (secureAvailable == null) {
    try {
      secureAvailable = await SecureStore.isAvailableAsync();
    } catch {
      secureAvailable = false;
    }
  }
  return secureAvailable;
}

/** Écrit (ou efface, si vide) les identifiants S3 dans le stockage sécurisé. */
async function persistSecrets(accessKeyId: string, secretAccessKey: string): Promise<void> {
  if (accessKeyId) await SecureStore.setItemAsync(SECURE_AK_KEY, accessKeyId);
  else await SecureStore.deleteItemAsync(SECURE_AK_KEY);
  if (secretAccessKey) await SecureStore.setItemAsync(SECURE_SK_KEY, secretAccessKey);
  else await SecureStore.deleteItemAsync(SECURE_SK_KEY);
}

/** Version du format de sauvegarde (indépendante du schéma SQLite). */
const BACKUP_FORMAT = 1;

export type BackupConfig = S3Config & {
  /** Sauvegarde automatique après chaque séance. */
  enabled: boolean;
};

export type BackupSnapshot = {
  format: number;
  app: 'suivi-sport';
  exportedAt: number;
  /** Version du schéma SQLite à l'export (absente = sauvegarde héritée). */
  schema?: number;
  data: DbSnapshot;
};

export type BackupLast = { at: number; ok: boolean; error?: string };

const DEFAULT_CONFIG: BackupConfig = {
  // Sauvegarde désactivée et non configurée par défaut : c'est une fonction
  // opt-in « votre propre serveur ». Aucune valeur personnelle n'est pré-remplie
  // (les champs vides affichent les placeholders de l'écran Réglages) ; rien ne
  // part sur le réseau tant que l'utilisateur n'a pas saisi ses identifiants
  // (runBackup/autoBackup sont gardés par isConfigComplete()).
  enabled: false,
  endpoint: '',
  region: 'us-east-1',
  bucket: '',
  accessKeyId: '',
  secretAccessKey: '',
  objectKey: '',
};

export async function getBackupConfig(): Promise<BackupConfig> {
  const raw = await getSetting(CONFIG_KEY);
  let stored: Partial<BackupConfig> = {};
  if (raw) {
    try {
      stored = JSON.parse(raw);
    } catch {
      stored = {};
    }
  }

  if (!(await hasSecureStore())) {
    // Web / plateforme sans stockage sécurisé : comportement historique.
    return { ...DEFAULT_CONFIG, ...stored };
  }

  // Migration héritée : si une ancienne version a laissé les secrets dans le
  // JSON `settings`, on les déplace une fois pour toutes dans le stockage
  // sécurisé puis on réécrit la config sans eux.
  if (stored.accessKeyId || stored.secretAccessKey) {
    await persistSecrets(stored.accessKeyId ?? '', stored.secretAccessKey ?? '');
    stored = stripSecrets(stored);
    await setSetting(CONFIG_KEY, JSON.stringify(stored));
  }

  const accessKeyId = (await SecureStore.getItemAsync(SECURE_AK_KEY)) ?? '';
  const secretAccessKey = (await SecureStore.getItemAsync(SECURE_SK_KEY)) ?? '';
  return { ...DEFAULT_CONFIG, ...stored, accessKeyId, secretAccessKey };
}

export async function saveBackupConfig(config: BackupConfig): Promise<void> {
  if (!(await hasSecureStore())) {
    await setSetting(CONFIG_KEY, JSON.stringify(config));
    return;
  }
  await persistSecrets(config.accessKeyId, config.secretAccessKey);
  await setSetting(CONFIG_KEY, JSON.stringify(stripSecrets(config)));
}

/** Retire les champs secrets d'une config avant de l'écrire en clair dans `settings`. */
function stripSecrets(config: Partial<BackupConfig>): Partial<BackupConfig> {
  const rest = { ...config };
  for (const f of SECRET_FIELDS) delete rest[f];
  return rest;
}

export async function getBackupLast(): Promise<BackupLast | null> {
  const raw = await getSetting(LAST_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** Vrai si la config contient le minimum requis pour contacter le serveur. */
export function isConfigComplete(c: BackupConfig): boolean {
  return Boolean(
    c.endpoint && c.bucket && c.accessKeyId && c.secretAccessKey && c.objectKey && c.region,
  );
}

async function recordLast(last: BackupLast): Promise<void> {
  await setSetting(LAST_KEY, JSON.stringify(last));
}

/** Téléverse une sauvegarde complète. Lève en cas d'erreur réseau/HTTP. */
export async function runBackup(config?: BackupConfig): Promise<BackupLast> {
  const cfg = config ?? (await getBackupConfig());
  if (!isConfigComplete(cfg)) throw new Error('Configuration S3 incomplète.');

  const snapshot: BackupSnapshot = {
    format: BACKUP_FORMAT,
    app: 'suivi-sport',
    exportedAt: nowMs(),
    schema: SCHEMA_VERSION,
    data: await exportAll(),
  };

  try {
    await putObject(cfg, JSON.stringify(snapshot));
    const last: BackupLast = { at: nowMs(), ok: true };
    await recordLast(last);
    return last;
  } catch (e) {
    const last: BackupLast = {
      at: nowMs(),
      ok: false,
      error: e instanceof Error ? e.message : 'Échec de la sauvegarde.',
    };
    await recordLast(last);
    throw e;
  }
}

/**
 * Sauvegarde automatique « best-effort » : ne lève jamais, ne fait rien si la
 * sauvegarde auto est désactivée ou mal configurée. À appeler après une séance.
 */
export async function autoBackup(): Promise<void> {
  try {
    const cfg = await getBackupConfig();
    if (!cfg.enabled || !isConfigComplete(cfg)) return;
    await runBackup(cfg);
  } catch {
    // silencieux : l'échec est consigné dans `backup_last`, l'UI le montrera
  }
}

/** Télécharge la dernière sauvegarde et REMPLACE les données locales. */
export async function restoreBackup(config?: BackupConfig): Promise<number> {
  const cfg = config ?? (await getBackupConfig());
  if (!isConfigComplete(cfg)) throw new Error('Configuration S3 incomplète.');

  const raw = await getObject(cfg);
  if (raw == null) throw new Error('Aucune sauvegarde trouvée sur le serveur.');

  let parsed: BackupSnapshot;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error('Sauvegarde illisible (JSON invalide).');
  }
  if (parsed.app !== 'suivi-sport' || !parsed.data) {
    throw new Error('Format de sauvegarde non reconnu.');
  }
  // Refuse une sauvegarde d'un format plus récent que celui géré : l'importer
  // pourrait corrompre les données locales. (Champ absent = format 1 hérité.)
  if ((parsed.format ?? 1) > BACKUP_FORMAT) {
    throw new Error(
      `Sauvegarde créée par une version plus récente (format ${parsed.format}). Mettez l'application à jour.`,
    );
  }
  // Garde-fou schéma : une sauvegarde d'un schéma SQLite plus récent peut
  // contenir des colonnes/tables que cette version ne sait pas restaurer →
  // perte silencieuse. On la refuse. (Champ absent = sauvegarde héritée : on
  // laisse passer, son schéma est forcément ≤ courant.)
  const currentSchema = await getSchemaVersion();
  if ((parsed.schema ?? currentSchema) > currentSchema) {
    throw new Error(
      `Sauvegarde créée par une version plus récente (schéma ${parsed.schema}). Mettez l'application à jour.`,
    );
  }

  await importAll(parsed.data);
  return parsed.data.sessions?.length ?? 0;
}
