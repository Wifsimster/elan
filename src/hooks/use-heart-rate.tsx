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
  acquireScan,
  CONNECT_TIMEOUT_MS,
  getManager,
  HEART_RATE_MEASUREMENT,
  HEART_RATE_SERVICE,
  parseHeartRate,
  releaseScan,
  requestBlePermissions,
  SCAN_TIMEOUT_MS,
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
  subscribe: (listener: (sample: HrSample) => void) => () => void;
};

const HeartRateContext = createContext<HeartRateContextValue | null>(null);

const SUPPORTED = Platform.OS === 'android' || Platform.OS === 'ios';
const LAST_DEVICE_KEY = 'hr_device';
const MAX_RECONNECT_ATTEMPTS = 5;

export function HeartRateProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<HrStatus>(SUPPORTED ? 'idle' : 'unsupported');
  const [bpm, setBpm] = useState<number | null>(null);
  const [device, setDevice] = useState<ScannedDevice | null>(null);
  const [scanned, setScanned] = useState<ScannedDevice[]>([]);
  const [error, setError] = useState<string | null>(null);

  const monitorRef = useRef<Subscription | null>(null);
  const disconnectSubRef = useRef<Subscription | null>(null);
  const connectedRef = useRef<Device | null>(null);
  const connectingRef = useRef(false);
  const listenersRef = useRef<Set<(s: HrSample) => void>>(new Set());
  const userDisconnectedRef = useRef(false);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const connectRef = useRef<((deviceId: string) => Promise<void>) | null>(null);
  const [scanOwner] = useState(() => Symbol('hr-scan'));
  const scanTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const subscribe = useCallback((listener: (sample: HrSample) => void) => {
    listenersRef.current.add(listener);
    return () => {
      listenersRef.current.delete(listener);
    };
  }, []);

  const stopScan = useCallback(() => {
    if (!SUPPORTED) return;
    if (scanTimerRef.current) {
      clearTimeout(scanTimerRef.current);
      scanTimerRef.current = null;
    }
    releaseScan(scanOwner);
    setStatus((s) => (s === 'scanning' ? 'idle' : s));
  }, [scanOwner]);

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
    acquireScan(scanOwner, () => {
      setStatus((s) => (s === 'scanning' ? 'idle' : s));
    });
    if (scanTimerRef.current) clearTimeout(scanTimerRef.current);
    scanTimerRef.current = setTimeout(() => {
      scanTimerRef.current = null;
      releaseScan(scanOwner);
      setStatus((s) => (s === 'scanning' ? 'idle' : s));
    }, SCAN_TIMEOUT_MS);
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
  }, [scanOwner]);

  const disconnect = useCallback(async () => {
    userDisconnectedRef.current = true;
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    disconnectSubRef.current?.remove();
    disconnectSubRef.current = null;
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
      }
    }
  }, []);

  const scheduleReconnect = useCallback((deviceId: string) => {
    if (userDisconnectedRef.current || reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) {
      setStatus('idle');
      return;
    }
    const attempt = reconnectAttemptsRef.current++;
    setStatus('reconnecting');
    const delay = Math.min(1000 * 2 ** attempt, 15000);
    if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    reconnectTimerRef.current = setTimeout(() => {
      reconnectTimerRef.current = null;
      connectRef.current?.(deviceId);
    }, delay);
  }, []);

  const connect = useCallback(
    async (deviceId: string) => {
      if (!SUPPORTED) return;
      if (connectingRef.current) return;
      if (connectedRef.current?.id === deviceId) return;
      connectingRef.current = true;
      if (connectedRef.current) {
        const prev = connectedRef.current;
        connectedRef.current = null;
        disconnectSubRef.current?.remove();
        disconnectSubRef.current = null;
        monitorRef.current?.remove();
        monitorRef.current = null;
        try {
          await prev.cancelConnection();
        } catch {
        }
      }
      setError(null);
      userDisconnectedRef.current = false;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      stopScan();
      setStatus('connecting');
      let dev: Device | null = null;
      try {
        const ok = await requestBlePermissions();
        if (!ok) throw new Error('Permissions Bluetooth refusées.');

        const manager = getManager();
        dev = await manager.connectToDevice(deviceId, {
          autoConnect: false,
          timeout: CONNECT_TIMEOUT_MS,
        });
        dev = await dev.discoverAllServicesAndCharacteristics();
        connectedRef.current = dev;

        disconnectSubRef.current?.remove();
        disconnectSubRef.current = dev.onDisconnected(() => {
          disconnectSubRef.current?.remove();
          disconnectSubRef.current = null;
          monitorRef.current?.remove();
          monitorRef.current = null;
          connectedRef.current = null;
          setBpm(null);
          setDevice(null);
          scheduleReconnect(deviceId);
        });

        monitorRef.current?.remove();
        monitorRef.current = dev.monitorCharacteristicForService(
          HEART_RATE_SERVICE,
          HEART_RATE_MEASUREMENT,
          (err, characteristic) => {
            if (err) return;
            const value = parseHeartRate(characteristic?.value ?? null);
            if (value == null) return;
            setBpm(value);
            const sample: HrSample = { ts: nowMs(), hr: value };
            for (const cb of listenersRef.current) cb(sample);
          },
        );

        const info: ScannedDevice = { id: dev.id, name: dev.name ?? 'Ceinture cardiaque' };
        setDevice(info);
        setScanned([]);
        setStatus('connected');
        reconnectAttemptsRef.current = 0;
        await setSetting(LAST_DEVICE_KEY, JSON.stringify(info));
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Échec de connexion.');
        connectedRef.current = null;
        if (dev) {
          try {
            await dev.cancelConnection();
          } catch {
          }
        }
        connectingRef.current = false;
        scheduleReconnect(deviceId);
        return;
      }
      connectingRef.current = false;
    },
    [scheduleReconnect, stopScan],
  );

  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  useEffect(() => {
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      if (scanTimerRef.current) clearTimeout(scanTimerRef.current);
      releaseScan(scanOwner);
    };
  }, [scanOwner]);

  useEffect(() => {
    if (!SUPPORTED) return;
    const reconnectLast = () => {
      if (connectedRef.current || connectingRef.current) return;
      getSetting(LAST_DEVICE_KEY).then((raw) => {
        if (!raw) return;
        try {
          const saved: ScannedDevice = JSON.parse(raw);
          reconnectAttemptsRef.current = 0;
          connectRef.current?.(saved.id);
        } catch {
        }
      });
    };
    const sub = getManager().onStateChange((state) => {
      if (state === 'PoweredOff') {
        setBpm(null);
        setError('Bluetooth désactivé.');
        setStatus((s) =>
          s === 'connected' || s === 'connecting' || s === 'reconnecting' ? 'error' : s,
        );
      } else if (state === 'PoweredOn') {
        setError(null);
        reconnectLast();
      }
    }, true);
    return () => sub.remove();
  }, []);

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
