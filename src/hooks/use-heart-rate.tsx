// Contexte global pour la ceinture cardiaque : une seule connexion BLE,
// partagée par les écrans de séance et les réglages.
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
import { Platform } from 'react-native';
import type { Subscription } from 'react-native-ble-plx';

import {
  getManager,
  HEART_RATE_MEASUREMENT,
  HEART_RATE_SERVICE,
  parseHeartRate,
  requestBlePermissions,
  type Device,
} from '@/lib/ble';
import { getSetting, setSetting } from '@/lib/db';
import { nowMs } from '@/lib/time';
import type { HrSample } from '@/lib/types';

export type HrStatus =
  | 'unsupported'
  | 'idle'
  | 'scanning'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'error';

export type ScannedDevice = { id: string; name: string };

// Type du domaine, défini dans lib/types.ts ; ré-exporté ici par commodité pour
// les consommateurs du contexte cardiaque.
export type { HrSample };

type HeartRateContextValue = {
  status: HrStatus;
  bpm: number | null;
  device: ScannedDevice | null;
  scanned: ScannedDevice[];
  error: string | null;
  startScan: () => Promise<void>;
  stopScan: () => void;
  connect: (deviceId: string) => Promise<void>;
  disconnect: () => Promise<void>;
  /**
   * S'abonne aux mesures brutes : appelé à chaque trame BLE reçue, **y compris
   * lorsque la valeur est identique à la précédente** (cas typique du palier
   * cardiaque). Un `useEffect([bpm])` côté écran raterait ces trames car
   * React déduplique les setStates identiques — ce qui appauvrissait les
   * statistiques (moyenne biaisée, points GPS sans FC attachée).
   */
  subscribe: (listener: (sample: HrSample) => void) => () => void;
};

const HeartRateContext = createContext<HeartRateContextValue | null>(null);

const SUPPORTED = Platform.OS === 'android' || Platform.OS === 'ios';
const LAST_DEVICE_KEY = 'hr_device';
/** Reconnexion auto : nombre max de tentatives après une coupure involontaire. */
const MAX_RECONNECT_ATTEMPTS = 5;

export function HeartRateProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<HrStatus>(SUPPORTED ? 'idle' : 'unsupported');
  const [bpm, setBpm] = useState<number | null>(null);
  const [device, setDevice] = useState<ScannedDevice | null>(null);
  const [scanned, setScanned] = useState<ScannedDevice[]>([]);
  const [error, setError] = useState<string | null>(null);

  const monitorRef = useRef<Subscription | null>(null);
  const connectedRef = useRef<Device | null>(null);
  const listenersRef = useRef<Set<(s: HrSample) => void>>(new Set());
  // Reconnexion auto sur coupure involontaire (capteur hors de portée, etc.).
  const userDisconnectedRef = useRef(false);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const connectRef = useRef<((deviceId: string) => Promise<void>) | null>(null);

  const subscribe = useCallback((listener: (sample: HrSample) => void) => {
    listenersRef.current.add(listener);
    return () => {
      listenersRef.current.delete(listener);
    };
  }, []);

  const stopScan = useCallback(() => {
    if (!SUPPORTED) return;
    getManager().stopDeviceScan();
    setStatus((s) => (s === 'scanning' ? 'idle' : s));
  }, []);

  const startScan = useCallback(async () => {
    if (!SUPPORTED) return;
    setError(null);
    const ok = await requestBlePermissions();
    if (!ok) {
      setError('Permissions Bluetooth refusées.');
      setStatus('error');
      return;
    }
    setScanned([]);
    setStatus('scanning');
    const seen = new Set<string>();
    getManager().startDeviceScan([HEART_RATE_SERVICE], null, (err, dev) => {
      if (err) {
        setError(err.message);
        setStatus('error');
        return;
      }
      if (dev && !seen.has(dev.id)) {
        seen.add(dev.id);
        setScanned((prev) => [...prev, { id: dev.id, name: dev.name ?? 'Capteur inconnu' }]);
      }
    });
  }, []);

  const disconnect = useCallback(async () => {
    // Déconnexion volontaire : on désarme la reconnexion auto et on annule une
    // tentative en attente AVANT cancelConnection (onDisconnected suit aussitôt).
    userDisconnectedRef.current = true;
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    monitorRef.current?.remove();
    monitorRef.current = null;
    const dev = connectedRef.current;
    connectedRef.current = null;
    setBpm(null);
    setDevice(null);
    setStatus(SUPPORTED ? 'idle' : 'unsupported');
    if (dev) {
      try {
        await dev.cancelConnection();
      } catch {
        // déjà déconnecté
      }
    }
  }, []);

  const connect = useCallback(
    async (deviceId: string) => {
      if (!SUPPORTED) return;
      setError(null);
      // Tentative volontaire : on réarme la reconnexion auto et on annule une
      // tentative différée éventuellement en cours.
      userDisconnectedRef.current = false;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      stopScan();
      setStatus('connecting');
      try {
        const ok = await requestBlePermissions();
        if (!ok) throw new Error('Permissions Bluetooth refusées.');

        const manager = getManager();
        let dev = await manager.connectToDevice(deviceId, { autoConnect: false });
        dev = await dev.discoverAllServicesAndCharacteristics();
        connectedRef.current = dev;

        dev.onDisconnected(() => {
          monitorRef.current?.remove();
          monitorRef.current = null;
          connectedRef.current = null;
          setBpm(null);
          setDevice(null);
          // Coupure involontaire : on retente avec un back-off exponentiel borné.
          if (
            !userDisconnectedRef.current &&
            reconnectAttemptsRef.current < MAX_RECONNECT_ATTEMPTS
          ) {
            const attempt = reconnectAttemptsRef.current++;
            setStatus('reconnecting');
            const delay = Math.min(1000 * 2 ** attempt, 15000);
            reconnectTimerRef.current = setTimeout(() => {
              reconnectTimerRef.current = null;
              connectRef.current?.(deviceId);
            }, delay);
          } else {
            setStatus('idle');
          }
        });

        monitorRef.current = dev.monitorCharacteristicForService(
          HEART_RATE_SERVICE,
          HEART_RATE_MEASUREMENT,
          (err, characteristic) => {
            if (err) return;
            const value = parseHeartRate(characteristic?.value ?? null);
            if (value == null) return;
            setBpm(value);
            // Notifie chaque trame brute (même valeur identique) pour éviter
            // les trous d'échantillonnage pendant un palier cardiaque.
            const sample: HrSample = { ts: nowMs(), hr: value };
            for (const cb of listenersRef.current) cb(sample);
          },
        );

        const info: ScannedDevice = { id: dev.id, name: dev.name ?? 'Ceinture cardiaque' };
        setDevice(info);
        setStatus('connected');
        reconnectAttemptsRef.current = 0; // connexion établie : compteur remis à zéro
        await setSetting(LAST_DEVICE_KEY, JSON.stringify(info));
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Échec de connexion.');
        setStatus('error');
        connectedRef.current = null;
      }
    },
    [stopScan],
  );

  // Réf vers le dernier `connect` (appelé par la reconnexion différée, sans
  // recréer le timer à chaque changement d'identité de connect).
  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  // Nettoyage : annule une reconnexion en attente au démontage du provider.
  useEffect(() => {
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    };
  }, []);

  // Tentative de reconnexion à la dernière ceinture au lancement.
  useEffect(() => {
    if (!SUPPORTED) return;
    let cancelled = false;
    (async () => {
      const raw = await getSetting(LAST_DEVICE_KEY);
      if (!raw || cancelled) return;
      try {
        const saved: ScannedDevice = JSON.parse(raw);
        const connected = await getManager().isDeviceConnected(saved.id);
        if (!connected && !cancelled) await connect(saved.id);
      } catch {
        // pas de reconnexion automatique possible, on attend l'action utilisateur
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [connect]);

  const value = useMemo<HeartRateContextValue>(
    () => ({
      status,
      bpm,
      device,
      scanned,
      error,
      startScan,
      stopScan,
      connect,
      disconnect,
      subscribe,
    }),
    [status, bpm, device, scanned, error, startScan, stopScan, connect, disconnect, subscribe],
  );

  return <HeartRateContext value={value}>{children}</HeartRateContext>;
}

export function useHeartRate(): HeartRateContextValue {
  const ctx = use(HeartRateContext);
  if (!ctx) throw new Error('useHeartRate doit être utilisé dans <HeartRateProvider>.');
  return ctx;
}
