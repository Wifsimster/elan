// Contexte de sauvegarde homelab : configuration S3, état de la dernière
// sauvegarde, et actions manuelles (sauvegarder / restaurer).
import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import {
  getBackupConfig,
  getBackupLast,
  isConfigComplete,
  restoreBackup,
  runBackup,
  saveBackupConfig,
  type BackupConfig,
  type BackupLast,
} from '@/lib/backup';

type BackupStatus = 'idle' | 'saving' | 'restoring';

type BackupContextValue = {
  config: BackupConfig | null;
  last: BackupLast | null;
  status: BackupStatus;
  error: string | null;
  /** Indique si la config permet de contacter le serveur. */
  ready: boolean;
  /** Met à jour la config (fusion partielle) et la persiste. */
  update: (patch: Partial<BackupConfig>) => void;
  /** Sauvegarde immédiate ; remonte une erreur dans `error`. */
  backupNow: () => Promise<void>;
  /** Restaure depuis le serveur (écrase les données locales). */
  restore: () => Promise<number | null>;
};

const BackupContext = createContext<BackupContextValue | null>(null);

export function BackupProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<BackupConfig | null>(null);
  const [last, setLast] = useState<BackupLast | null>(null);
  const [status, setStatus] = useState<BackupStatus>('idle');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getBackupConfig().then(setConfig);
    getBackupLast().then(setLast);
  }, []);

  const update = useCallback((patch: Partial<BackupConfig>) => {
    setConfig((prev) => {
      const next = { ...(prev ?? ({} as BackupConfig)), ...patch };
      saveBackupConfig(next);
      return next;
    });
  }, []);

  const backupNow = useCallback(async () => {
    setError(null);
    setStatus('saving');
    try {
      const result = await runBackup();
      setLast(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Échec de la sauvegarde.');
      setLast(await getBackupLast());
    } finally {
      setStatus('idle');
    }
  }, []);

  const restore = useCallback(async () => {
    setError(null);
    setStatus('restoring');
    try {
      return await restoreBackup();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Échec de la restauration.');
      return null;
    } finally {
      setStatus('idle');
    }
  }, []);

  const ready = config ? isConfigComplete(config) : false;

  const value = useMemo<BackupContextValue>(
    () => ({ config, last, status, error, ready, update, backupNow, restore }),
    [config, last, status, error, ready, update, backupNow, restore],
  );

  return <BackupContext value={value}>{children}</BackupContext>;
}

export function useBackup(): BackupContextValue {
  const ctx = use(BackupContext);
  if (!ctx) throw new Error('useBackup doit être utilisé dans <BackupProvider>.');
  return ctx;
}
