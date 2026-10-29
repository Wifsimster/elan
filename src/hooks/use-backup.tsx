import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useRef,
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
  ready: boolean;
  update: (patch: Partial<BackupConfig>) => void;
  backupNow: () => Promise<void>;
  restore: () => Promise<number | null>;
  refreshLast: () => Promise<void>;
};

const BackupContext = createContext<BackupContextValue | null>(null);

export function BackupProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<BackupConfig | null>(null);
  const [last, setLast] = useState<BackupLast | null>(null);
  const [status, setStatus] = useState<BackupStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const configRef = useRef<BackupConfig | null>(null);

  useEffect(() => {
    getBackupConfig().then((c) => {
      configRef.current = c;
      setConfig(c);
    });
    getBackupLast().then(setLast);
  }, []);

  const update = useCallback((patch: Partial<BackupConfig>) => {
    const prev = configRef.current;
    if (prev == null) return;
    const next = { ...prev, ...patch };
    configRef.current = next;
    setConfig(next);
    saveBackupConfig(next).catch(() => {
    });
  }, []);

  const refreshLast = useCallback(async () => {
    setLast(await getBackupLast());
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
    () => ({ config, last, status, error, ready, update, backupNow, restore, refreshLast }),
    [config, last, status, error, ready, update, backupNow, restore, refreshLast],
  );

  return <BackupContext value={value}>{children}</BackupContext>;
}

export function useBackup(): BackupContextValue {
  const ctx = use(BackupContext);
  if (!ctx) throw new Error('useBackup doit être utilisé dans <BackupProvider>.');
  return ctx;
}
