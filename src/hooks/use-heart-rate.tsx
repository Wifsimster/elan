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

export type HrStatus =
  | 'unsupported'
  | 'idle'
  | 'scanning'
  | 'connecting'
  | 'connected'
  | 'error';

export type ScannedDevice = { id: string; name: string };

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
};

const HeartRateContext = createContext<HeartRateContextValue | null>(null);

const SUPPORTED = Platform.OS === 'android' || Platform.OS === 'ios';
const LAST_DEVICE_KEY = 'hr_device';

export function HeartRateProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<HrStatus>(SUPPORTED ? 'idle' : 'unsupported');
  const [bpm, setBpm] = useState<number | null>(null);
  const [device, setDevice] = useState<ScannedDevice | null>(null);
  const [scanned, setScanned] = useState<ScannedDevice[]>([]);
  const [error, setError] = useState<string | null>(null);

  const monitorRef = useRef<Subscription | null>(null);
  const connectedRef = useRef<Device | null>(null);

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
          setStatus('idle');
          setDevice(null);
        });

        monitorRef.current = dev.monitorCharacteristicForService(
          HEART_RATE_SERVICE,
          HEART_RATE_MEASUREMENT,
          (err, characteristic) => {
            if (err) return;
            const value = parseHeartRate(characteristic?.value ?? null);
            if (value != null) setBpm(value);
          },
        );

        const info: ScannedDevice = { id: dev.id, name: dev.name ?? 'Ceinture cardiaque' };
        setDevice(info);
        setStatus('connected');
        await setSetting(LAST_DEVICE_KEY, JSON.stringify(info));
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Échec de connexion.');
        setStatus('error');
        connectedRef.current = null;
      }
    },
    [stopScan],
  );

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
    () => ({ status, bpm, device, scanned, error, startScan, stopScan, connect, disconnect }),
    [status, bpm, device, scanned, error, startScan, stopScan, connect, disconnect],
  );

  return <HeartRateContext value={value}>{children}</HeartRateContext>;
}

export function useHeartRate(): HeartRateContextValue {
  const ctx = use(HeartRateContext);
  if (!ctx) throw new Error('useHeartRate doit être utilisé dans <HeartRateProvider>.');
  return ctx;
}
