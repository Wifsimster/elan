// Sauvegarde des données vers un stockage S3-compatible auto-hébergé (homelab).
// Sérialise toute la base en un seul objet JSON (écrasé à chaque sauvegarde).
import { exportAll, getSetting, importAll, setSetting, type DbSnapshot } from '@/lib/db';
import { getObject, putObject, type S3Config } from '@/lib/s3';
import { nowMs } from '@/lib/time';

const CONFIG_KEY = 'backup_s3';
const LAST_KEY = 'backup_last';

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
  data: DbSnapshot;
};

export type BackupLast = { at: number; ok: boolean; error?: string };

const DEFAULT_CONFIG: BackupConfig = {
  // Pré-rempli pour le homelab SeaweedFS (s3.battistella.ovh). Les clés d'accès
  // restent vides : à saisir une fois sur l'appareil (jamais commitées — dépôt public).
  enabled: true,
  endpoint: 'https://s3.battistella.ovh',
  region: 'us-east-1',
  bucket: 'elan',
  accessKeyId: '',
  secretAccessKey: '',
  objectKey: 'elan-backup.json',
};

export async function getBackupConfig(): Promise<BackupConfig> {
  const raw = await getSetting(CONFIG_KEY);
  if (!raw) return DEFAULT_CONFIG;
  try {
    return { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
  } catch {
    return DEFAULT_CONFIG;
  }
}

export async function saveBackupConfig(config: BackupConfig): Promise<void> {
  await setSetting(CONFIG_KEY, JSON.stringify(config));
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

  await importAll(parsed.data);
  return parsed.data.sessions?.length ?? 0;
}
