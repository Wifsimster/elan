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
const SECURE_AK_KEY = 'backup_s3_accessKeyId';
const SECURE_SK_KEY = 'backup_s3_secretAccessKey';

const SECRET_FIELDS = ['accessKeyId', 'secretAccessKey'] as const;

let secureAvailable: boolean | null = null;
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

async function persistSecrets(accessKeyId: string, secretAccessKey: string): Promise<void> {
  if (accessKeyId) await SecureStore.setItemAsync(SECURE_AK_KEY, accessKeyId);
  else await SecureStore.deleteItemAsync(SECURE_AK_KEY);
  if (secretAccessKey) await SecureStore.setItemAsync(SECURE_SK_KEY, secretAccessKey);
  else await SecureStore.deleteItemAsync(SECURE_SK_KEY);
}

const BACKUP_FORMAT = 1;

let backupChain: Promise<unknown> = Promise.resolve();

function withBackupLock<T>(fn: () => Promise<T>): Promise<T> {
  const run = backupChain.then(fn, fn);
  backupChain = run.then(
    () => undefined,
    () => undefined,
  );
  return run;
}

export type BackupConfig = S3Config & {
  enabled: boolean;
};

export type BackupSnapshot = {
  format: number;
  app: 'suivi-sport';
  exportedAt: number;
  schema?: number;
  data: DbSnapshot;
};

export type BackupLast = { at: number; ok: boolean; error?: string };

export const DEFAULT_REGION = 'us-east-1';
export const DEFAULT_OBJECT_KEY = 'elan-backup.json';

const DEFAULT_CONFIG: BackupConfig = {
  enabled: false,
  endpoint: '',
  region: DEFAULT_REGION,
  bucket: '',
  accessKeyId: '',
  secretAccessKey: '',
  objectKey: DEFAULT_OBJECT_KEY,
};

function withDefaults(stored: Partial<BackupConfig>): BackupConfig {
  const merged = { ...DEFAULT_CONFIG, ...stored };
  return {
    ...merged,
    region: merged.region || DEFAULT_REGION,
    objectKey: merged.objectKey || DEFAULT_OBJECT_KEY,
  };
}

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
    return withDefaults(stored);
  }

  if (stored.accessKeyId || stored.secretAccessKey) {
    await persistSecrets(stored.accessKeyId ?? '', stored.secretAccessKey ?? '');
    stored = stripSecrets(stored);
    await setSetting(CONFIG_KEY, JSON.stringify(stored));
  }

  const accessKeyId = (await SecureStore.getItemAsync(SECURE_AK_KEY)) ?? '';
  const secretAccessKey = (await SecureStore.getItemAsync(SECURE_SK_KEY)) ?? '';
  return { ...withDefaults(stored), accessKeyId, secretAccessKey };
}

export async function saveBackupConfig(config: BackupConfig): Promise<void> {
  if (!(await hasSecureStore())) {
    await setSetting(CONFIG_KEY, JSON.stringify(config));
    return;
  }
  await persistSecrets(config.accessKeyId, config.secretAccessKey);
  await setSetting(CONFIG_KEY, JSON.stringify(stripSecrets(config)));
}

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

export function effectiveConfig(c: BackupConfig): BackupConfig {
  const t = (v: string) => v.trim();
  return {
    ...c,
    endpoint: t(c.endpoint),
    bucket: t(c.bucket),
    accessKeyId: t(c.accessKeyId),
    secretAccessKey: t(c.secretAccessKey),
    region: t(c.region) || DEFAULT_REGION,
    objectKey: t(c.objectKey) || DEFAULT_OBJECT_KEY,
  };
}

export function isConfigComplete(c: BackupConfig): boolean {
  const e = effectiveConfig(c);
  return Boolean(e.endpoint && e.bucket && e.accessKeyId && e.secretAccessKey);
}

async function recordLast(last: BackupLast): Promise<void> {
  await setSetting(LAST_KEY, JSON.stringify(last));
}

export async function runBackup(config?: BackupConfig): Promise<BackupLast> {
  return withBackupLock(async () => {
    const cfg = effectiveConfig(config ?? (await getBackupConfig()));
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
  });
}

export async function autoBackup(): Promise<void> {
  try {
    const cfg = await getBackupConfig();
    if (!cfg.enabled || !isConfigComplete(cfg)) return;
    await runBackup(cfg);
  } catch {
  }
}

export async function restoreBackup(config?: BackupConfig): Promise<number> {
  return withBackupLock(() => restoreBackupInner(config));
}

async function restoreBackupInner(config?: BackupConfig): Promise<number> {
  const cfg = effectiveConfig(config ?? (await getBackupConfig()));
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
  if ((parsed.format ?? 1) > BACKUP_FORMAT) {
    throw new Error(
      `Sauvegarde créée par une version plus récente (format ${parsed.format}). Mets l'application à jour.`,
    );
  }
  const currentSchema = await getSchemaVersion();
  if ((parsed.schema ?? currentSchema) > currentSchema) {
    throw new Error(
      `Sauvegarde créée par une version plus récente (schéma ${parsed.schema}). Mets l'application à jour.`,
    );
  }

  await importAll(parsed.data);
  return parsed.data.sessions?.length ?? 0;
}
