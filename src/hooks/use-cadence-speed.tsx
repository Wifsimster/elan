// Contexte global pour les capteurs vélo BLE « Cycling Speed and Cadence »
// (profil GATT CSC) : cadence (pédalier) et vitesse (roue). Gère un ou deux
// capteurs simultanés — typiquement un iGPSPORT CAD70 (cadence) + un SPD70
// (vitesse), qui sont deux périphériques distincts.
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
import type { Device, Subscription } from 'react-native-ble-plx';

import {
  CSC_MEASUREMENT,
  CSC_SERVICE,
  getManager,
  parseCsc,
  requestBlePermissions,
  type CscRaw,
} from '@/lib/ble';
import { getSetting, setSetting } from '@/lib/db';
import { nowMs } from '@/lib/time';

export type CscStatus =
  | 'unsupported'
  | 'idle'
  | 'scanning'
  | 'connecting'
  | 'connected'
  | 'error';

export type CscDevice = { id: string; name: string };

export type CscSample = {
  ts: number;
  cadenceRpm: number | null;
  speedKmh: number | null;
};

type CadenceSpeedContextValue = {
  status: CscStatus;
  /** Cadence instantanée en tours/min (null si aucun capteur de cadence). */
  cadenceRpm: number | null;
  /** Vitesse roue instantanée en km/h (null si aucun capteur de vitesse). */
  speedKmh: number | null;
  /** Capteurs actuellement connectés. */
  devices: CscDevice[];
  /** Capteurs détectés pendant le scan. */
  scanned: CscDevice[];
  error: string | null;
  /** Circonférence de roue en mm (pour convertir les tours en distance). */
  wheelCircumferenceMm: number;
  setWheelCircumferenceMm: (mm: number) => void;
  startScan: () => Promise<void>;
  stopScan: () => void;
  connect: (deviceId: string) => Promise<void>;
  /** Déconnecte un capteur, ou tous si `deviceId` est omis. */
  disconnect: (deviceId?: string) => Promise<void>;
  /**
   * S'abonne aux mesures brutes (même valeur identique : nécessaire pour ne
   * pas perdre d'échantillons sur un palier de cadence/vitesse, que React
   * dédupliquerait via setState).
   */
  subscribe: (listener: (sample: CscSample) => void) => () => void;
};

const CadenceSpeedContext = createContext<CadenceSpeedContextValue | null>(null);

const SUPPORTED = Platform.OS === 'android' || Platform.OS === 'ios';
const DEVICES_KEY = 'csc_devices';
const WHEEL_MM_KEY = 'csc_wheel_mm';
const DEFAULT_WHEEL_MM = 2105; // 700×25c, valeur par défaut courante

/** Unité de temps CSC : 1/1024 s. */
const TIME_UNIT = 1024;
/** Au-delà, on considère le capteur silencieux et on retombe à zéro. */
const STALE_MS = 3000;

const delta = (curr: number, prev: number, mod: number) => (curr - prev + mod) % mod;

export function CadenceSpeedProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<CscStatus>(SUPPORTED ? 'idle' : 'unsupported');
  const [cadenceRpm, setCadenceRpm] = useState<number | null>(null);
  const [speedKmh, setSpeedKmh] = useState<number | null>(null);
  const [devices, setDevices] = useState<CscDevice[]>([]);
  const [scanned, setScanned] = useState<CscDevice[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [wheelCircumferenceMm, setWheelMm] = useState(DEFAULT_WHEEL_MM);

  // Connexions actives + dernière mesure brute par capteur (pour les deltas).
  const connRef = useRef<Map<string, { device: Device; sub: Subscription }>>(new Map());
  const lastSampleRef = useRef<Map<string, CscRaw>>(new Map());
  const circMmRef = useRef(DEFAULT_WHEEL_MM);
  const crankMoveTsRef = useRef(0);
  const wheelMoveTsRef = useRef(0);
  const listenersRef = useRef<Set<(s: CscSample) => void>>(new Set());
  const lastCadenceRef = useRef<number | null>(null);
  const lastSpeedRef = useRef<number | null>(null);

  const subscribe = useCallback((listener: (sample: CscSample) => void) => {
    listenersRef.current.add(listener);
    return () => {
      listenersRef.current.delete(listener);
    };
  }, []);

  const notify = useCallback(() => {
    if (listenersRef.current.size === 0) return;
    const sample: CscSample = {
      ts: nowMs(),
      cadenceRpm: lastCadenceRef.current,
      speedKmh: lastSpeedRef.current,
    };
    for (const cb of listenersRef.current) cb(sample);
  }, []);

  const syncDevices = useCallback(() => {
    setDevices(
      [...connRef.current.values()].map((c) => ({
        id: c.device.id,
        name: c.device.name ?? 'Capteur vélo',
      })),
    );
    setStatus(connRef.current.size > 0 ? 'connected' : SUPPORTED ? 'idle' : 'unsupported');
  }, []);

  // Traite une mesure CSC d'un capteur donné et met à jour cadence/vitesse.
  const handleSample = useCallback(
    (id: string, value: string | null) => {
      const raw = parseCsc(value);
      if (!raw) return;
      const prev = lastSampleRef.current.get(id);
      lastSampleRef.current.set(id, raw);
      if (!prev) return; // il faut deux mesures pour un delta

      let updated = false;

      // Cadence (pédalier).
      if (
        raw.crankRevs != null &&
        prev.crankRevs != null &&
        raw.crankTime != null &&
        prev.crankTime != null
      ) {
        const dRev = delta(raw.crankRevs, prev.crankRevs, 0x10000);
        const dT = delta(raw.crankTime, prev.crankTime, 0x10000);
        if (dRev === 0) {
          setCadenceRpm(0);
          lastCadenceRef.current = 0;
          updated = true;
        } else if (dT > 0) {
          const rpm = Math.round((dRev / (dT / TIME_UNIT)) * 60);
          if (rpm <= 250) {
            setCadenceRpm(rpm);
            lastCadenceRef.current = rpm;
            crankMoveTsRef.current = nowMs();
            updated = true;
          }
        }
      }

      // Vitesse (roue).
      if (
        raw.wheelRevs != null &&
        prev.wheelRevs != null &&
        raw.wheelTime != null &&
        prev.wheelTime != null
      ) {
        const dRev = delta(raw.wheelRevs, prev.wheelRevs, 0x100000000);
        const dT = delta(raw.wheelTime, prev.wheelTime, 0x10000);
        if (dRev === 0) {
          setSpeedKmh(0);
          lastSpeedRef.current = 0;
          updated = true;
        } else if (dT > 0) {
          const mps = (dRev * (circMmRef.current / 1000)) / (dT / TIME_UNIT);
          const kmh = mps * 3.6;
          if (kmh <= 120) {
            setSpeedKmh(kmh);
            lastSpeedRef.current = kmh;
            wheelMoveTsRef.current = nowMs();
            updated = true;
          }
        }
      }

      // Notifie les abonnés à chaque trame exploitable (même valeur égale).
      if (updated) notify();
    },
    [notify],
  );

  // Retombe à zéro quand un capteur cesse d'émettre (arrêt prolongé).
  useEffect(() => {
    if (!SUPPORTED) return;
    const t = setInterval(() => {
      const now = nowMs();
      if (crankMoveTsRef.current && now - crankMoveTsRef.current > STALE_MS) {
        setCadenceRpm((c) => (c ? 0 : c));
      }
      if (wheelMoveTsRef.current && now - wheelMoveTsRef.current > STALE_MS) {
        setSpeedKmh((s) => (s ? 0 : s));
      }
    }, 1000);
    return () => clearInterval(t);
  }, []);

  const stopScan = useCallback(() => {
    if (!SUPPORTED) return;
    getManager().stopDeviceScan();
    setStatus((s) => (s === 'scanning' ? (connRef.current.size > 0 ? 'connected' : 'idle') : s));
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
    getManager().stopDeviceScan();
    setScanned([]);
    setStatus('scanning');
    const seen = new Set<string>();
    getManager().startDeviceScan([CSC_SERVICE], null, (err, dev) => {
      if (err) {
        setError(err.message);
        setStatus('error');
        return;
      }
      if (dev && !seen.has(dev.id) && !connRef.current.has(dev.id)) {
        seen.add(dev.id);
        setScanned((prev) => [...prev, { id: dev.id, name: dev.name ?? 'Capteur vélo' }]);
      }
    });
  }, []);

  const persistDevices = useCallback(async () => {
    const list = [...connRef.current.values()].map((c) => ({
      id: c.device.id,
      name: c.device.name ?? 'Capteur vélo',
    }));
    await setSetting(DEVICES_KEY, JSON.stringify(list));
  }, []);

  const disconnect = useCallback(
    async (deviceId?: string) => {
      const entries = deviceId
        ? ([deviceId, connRef.current.get(deviceId)] as const)
        : null;

      const targets = deviceId
        ? entries && entries[1]
          ? [[entries[0], entries[1]] as const]
          : []
        : [...connRef.current.entries()];

      for (const [id, conn] of targets) {
        conn.sub.remove();
        connRef.current.delete(id);
        lastSampleRef.current.delete(id);
        try {
          await conn.device.cancelConnection();
        } catch {
          // déjà déconnecté
        }
      }

      if (connRef.current.size === 0) {
        setCadenceRpm(null);
        setSpeedKmh(null);
        lastCadenceRef.current = null;
        lastSpeedRef.current = null;
      }
      syncDevices();
      await persistDevices();
    },
    [persistDevices, syncDevices],
  );

  const connect = useCallback(
    async (deviceId: string) => {
      if (!SUPPORTED) return;
      if (connRef.current.has(deviceId)) return;
      setError(null);
      stopScan();
      setStatus('connecting');
      try {
        const ok = await requestBlePermissions();
        if (!ok) throw new Error('Permissions Bluetooth refusées.');

        const manager = getManager();
        let dev = await manager.connectToDevice(deviceId, { autoConnect: false });
        dev = await dev.discoverAllServicesAndCharacteristics();

        const sub = dev.monitorCharacteristicForService(
          CSC_SERVICE,
          CSC_MEASUREMENT,
          (err, characteristic) => {
            if (err) return;
            handleSample(dev.id, characteristic?.value ?? null);
          },
        );

        dev.onDisconnected(() => {
          const c = connRef.current.get(dev.id);
          c?.sub.remove();
          connRef.current.delete(dev.id);
          lastSampleRef.current.delete(dev.id);
          if (connRef.current.size === 0) {
            setCadenceRpm(null);
            setSpeedKmh(null);
            lastCadenceRef.current = null;
            lastSpeedRef.current = null;
          }
          syncDevices();
        });

        connRef.current.set(dev.id, { device: dev, sub });
        syncDevices();
        await persistDevices();
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Échec de connexion.');
        setStatus(connRef.current.size > 0 ? 'connected' : 'error');
      }
    },
    [handleSample, persistDevices, stopScan, syncDevices],
  );

  const setWheelCircumferenceMm = useCallback((mm: number) => {
    circMmRef.current = mm;
    setWheelMm(mm);
    setSetting(WHEEL_MM_KEY, String(mm));
  }, []);

  // Chargement initial : circonférence + reconnexion aux capteurs mémorisés.
  useEffect(() => {
    if (!SUPPORTED) return;
    let cancelled = false;
    (async () => {
      const mmRaw = await getSetting(WHEEL_MM_KEY);
      if (!cancelled && mmRaw) {
        const mm = Number(mmRaw);
        if (Number.isFinite(mm)) {
          circMmRef.current = mm;
          setWheelMm(mm);
        }
      }
      const raw = await getSetting(DEVICES_KEY);
      if (!raw || cancelled) return;
      try {
        const saved: CscDevice[] = JSON.parse(raw);
        for (const d of saved) {
          if (cancelled) break;
          const already = await getManager().isDeviceConnected(d.id);
          if (!already) await connect(d.id);
        }
      } catch {
        // pas de reconnexion auto possible, on attend l'utilisateur
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [connect]);

  const value = useMemo<CadenceSpeedContextValue>(
    () => ({
      status,
      cadenceRpm,
      speedKmh,
      devices,
      scanned,
      error,
      wheelCircumferenceMm,
      setWheelCircumferenceMm,
      startScan,
      stopScan,
      connect,
      disconnect,
      subscribe,
    }),
    [
      status,
      cadenceRpm,
      speedKmh,
      devices,
      scanned,
      error,
      wheelCircumferenceMm,
      setWheelCircumferenceMm,
      startScan,
      stopScan,
      connect,
      disconnect,
      subscribe,
    ],
  );

  return <CadenceSpeedContext value={value}>{children}</CadenceSpeedContext>;
}

export function useCadenceSpeed(): CadenceSpeedContextValue {
  const ctx = use(CadenceSpeedContext);
  if (!ctx) throw new Error('useCadenceSpeed doit être utilisé dans <CadenceSpeedProvider>.');
  return ctx;
}
