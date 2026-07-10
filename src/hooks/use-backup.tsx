// Contexte de sauvegarde homelab : configuration S3, état de la dernière
// sauvegarde, et actions manuelles (sauvegarder / restaurer).
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
  /** Indique si la config permet de contacter le serveur. */
  ready: boolean;
  /** Met à jour la config (fusion partielle) et la persiste. */
  update: (patch: Partial<BackupConfig>) => void;
  /** Sauvegarde immédiate ; remonte une erreur dans `error`. */
  backupNow: () => Promise<void>;
  /** Restaure depuis le serveur (écrase les données locales). */
  restore: () => Promise<number | null>;
  /** Relit le statut de la dernière sauvegarde (ex. échec d'une auto-backup). */
  refreshLast: () => Promise<void>;
};

const BackupContext = createContext<BackupContextValue | null>(null);

export function BackupProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<BackupConfig | null>(null);
  const [last, setLast] = useState<BackupLast | null>(null);
  const [status, setStatus] = useState<BackupStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  // Miroir de `config` pour fusionner de façon fiable hors du updater setState.
  const configRef = useRef<BackupConfig | null>(null);

  useEffect(() => {
    getBackupConfig().then((c) => {
      configRef.current = c;
      setConfig(c);
    });
    getBackupLast().then(setLast);
  }, []);

  const update = useCallback((patch: Partial<BackupConfig>) => {
    // Tant que la config n'est pas hydratée, on IGNORE l'update : sinon on
    // écrirait un objet tronqué et `persistSecrets(undefined, undefined)`
    // effacerait access key + secret du SecureStore. La persistance est faite
    // hors du updater setState (un updater peut être rejoué — StrictMode /
    // React Compiler — ce qui doublait l'effet de bord).
    const prev = configRef.current;
    if (prev == null) return;
    const next = { ...prev, ...patch };
    configRef.current = next;
    setConfig(next);
    saveBackupConfig(next).catch(() => {
      // échec d'écriture des réglages : la config en mémoire reste la référence
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
