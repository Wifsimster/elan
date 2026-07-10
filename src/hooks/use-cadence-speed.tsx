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
  acquireScan,
  CONNECT_TIMEOUT_MS,
  CSC_MEASUREMENT,
  CSC_SERVICE,
  getManager,
  parseCsc,
  releaseScan,
  requestBlePermissions,
  SCAN_TIMEOUT_MS,
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
  | 'reconnecting'
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
/** Reconnexion auto : nombre max de tentatives par capteur après coupure involontaire. */
const MAX_RECONNECT_ATTEMPTS = 5;

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
  // `disconnectSub` est conservé pour être retiré (sinon les handlers
  // onDisconnected s'accumulent à chaque reconnexion d'un capteur).
  const connRef = useRef<
    Map<string, { device: Device; sub: Subscription; disconnectSub: Subscription | null }>
  >(new Map());
  const lastSampleRef = useRef<Map<string, CscRaw>>(new Map());
  // Connexions en vol (garde anti-double-connexion, par capteur).
  const connectingRef = useRef<Set<string>>(new Set());
  // Scan partagé (coordinateur lib/ble) : propriétaire + timeout d'auto-arrêt.
  // useState (initialiseur paresseux) : jeton stable propre à cette instance.
  const [scanOwner] = useState(() => Symbol('csc-scan'));
  const scanTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const circMmRef = useRef(DEFAULT_WHEEL_MM);
  const crankMoveTsRef = useRef(0);
  const wheelMoveTsRef = useRef(0);
  const listenersRef = useRef<Set<(s: CscSample) => void>>(new Set());
  const lastCadenceRef = useRef<number | null>(null);
  const lastSpeedRef = useRef<number | null>(null);
  // Reconnexion auto par capteur (coupure involontaire d'un des deux périphériques).
  const intentionalDisconnectRef = useRef<Set<string>>(new Set());
  const reconnectTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());
  const reconnectAttemptsRef = useRef<Map<string, number>>(new Map());
  const connectRef = useRef<((deviceId: string) => Promise<void>) | null>(null);

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
        // Zéro AUSSI la ref : sinon un notify() déclenché par l'autre capteur
        // re-diffuserait une cadence fantôme périmée alors que l'UI affiche 0.
        lastCadenceRef.current = 0;
      }
      if (wheelMoveTsRef.current && now - wheelMoveTsRef.current > STALE_MS) {
        setSpeedKmh((s) => (s ? 0 : s));
        lastSpeedRef.current = 0;
      }
    }, 1000);
    return () => clearInterval(t);
  }, []);

  const idleStatus = useCallback(
    () => (connRef.current.size > 0 ? 'connected' : SUPPORTED ? 'idle' : 'unsupported'),
    [],
  );

  const stopScan = useCallback(() => {
    if (!SUPPORTED) return;
    if (scanTimerRef.current) {
      clearTimeout(scanTimerRef.current);
      scanTimerRef.current = null;
    }
    releaseScan(scanOwner);
    setStatus((s) => (s === 'scanning' ? idleStatus() : s));
  }, [idleStatus, scanOwner]);

  const startScan = useCallback(async () => {
    if (!SUPPORTED) return;
    setError(null);
    const ok = await requestBlePermissions();
    if (!ok) {
      setError('Permissions Bluetooth refusées.');
      setStatus('error');
      return;
    }
    // Coordination du scan partagé : notifie/arrête le scan de la ceinture s'il
    // tournait ; « scan volé » nous ramène à l'arrêt dans le cas inverse.
    acquireScan(scanOwner, () => {
      setStatus((s) => (s === 'scanning' ? idleStatus() : s));
    });
    setScanned([]);
    setStatus('scanning');
    if (scanTimerRef.current) clearTimeout(scanTimerRef.current);
    scanTimerRef.current = setTimeout(() => {
      scanTimerRef.current = null;
      releaseScan(scanOwner);
      setStatus((s) => (s === 'scanning' ? idleStatus() : s));
    }, SCAN_TIMEOUT_MS);
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
  }, [idleStatus, scanOwner]);

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
        // Déconnexion volontaire : désarme la reconnexion auto de ce capteur
        // (marqué AVANT cancelConnection, car onDisconnected suit aussitôt).
        intentionalDisconnectRef.current.add(id);
        const timer = reconnectTimersRef.current.get(id);
        if (timer) {
          clearTimeout(timer);
          reconnectTimersRef.current.delete(id);
        }
        reconnectAttemptsRef.current.delete(id);
        connectingRef.current.delete(id);
        conn.sub.remove();
        conn.disconnectSub?.remove();
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

  const scheduleReconnect = useCallback(
    (deviceId: string) => {
      const attempts = reconnectAttemptsRef.current.get(deviceId) ?? 0;
      if (intentionalDisconnectRef.current.has(deviceId) || attempts >= MAX_RECONNECT_ATTEMPTS) {
        syncDevices();
        return;
      }
      reconnectAttemptsRef.current.set(deviceId, attempts + 1);
      const delay = Math.min(1000 * 2 ** attempts, 15000);
      const prev = reconnectTimersRef.current.get(deviceId);
      if (prev) clearTimeout(prev);
      const timer = setTimeout(() => {
        reconnectTimersRef.current.delete(deviceId);
        connectRef.current?.(deviceId);
      }, delay);
      reconnectTimersRef.current.set(deviceId, timer);
      if (connRef.current.size === 0) setStatus('reconnecting');
      else syncDevices();
    },
    [syncDevices],
  );

  const connect = useCallback(
    async (deviceId: string) => {
      if (!SUPPORTED) return;
      // Garde anti-double-connexion : déjà connecté OU tentative en vol.
      if (connRef.current.has(deviceId) || connectingRef.current.has(deviceId)) return;
      connectingRef.current.add(deviceId);
      setError(null);
      // Tentative volontaire : réarme la reconnexion auto et annule une tentative
      // différée pour ce capteur.
      intentionalDisconnectRef.current.delete(deviceId);
      const pending = reconnectTimersRef.current.get(deviceId);
      if (pending) {
        clearTimeout(pending);
        reconnectTimersRef.current.delete(deviceId);
      }
      stopScan();
      setStatus('connecting');
      let dev: Device | null = null;
      try {
        const ok = await requestBlePermissions();
        if (!ok) throw new Error('Permissions Bluetooth refusées.');

        const manager = getManager();
        // Timeout : un capteur endormi ne doit pas bloquer ~30 s au lancement.
        dev = await manager.connectToDevice(deviceId, {
          autoConnect: false,
          timeout: CONNECT_TIMEOUT_MS,
        });
        dev = await dev.discoverAllServicesAndCharacteristics();
        const device = dev;

        const sub = device.monitorCharacteristicForService(
          CSC_SERVICE,
          CSC_MEASUREMENT,
          (err, characteristic) => {
            if (err) return;
            handleSample(device.id, characteristic?.value ?? null);
          },
        );

        const disconnectSub = device.onDisconnected(() => {
          const c = connRef.current.get(device.id);
          c?.sub.remove();
          c?.disconnectSub?.remove();
          connRef.current.delete(device.id);
          lastSampleRef.current.delete(device.id);
          if (connRef.current.size === 0) {
            setCadenceRpm(null);
            setSpeedKmh(null);
            lastCadenceRef.current = null;
            lastSpeedRef.current = null;
          }
          scheduleReconnect(device.id); // coupure involontaire : back-off borné
        });

        connRef.current.set(device.id, { device, sub, disconnectSub });
        reconnectAttemptsRef.current.delete(device.id); // connexion établie
        syncDevices();
        await persistDevices();
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Échec de connexion.');
        // Découverte/monitoring échoué (GATT 133) : ferme le GATT semi-connecté,
        // sinon l'appareil devient indétectable au scan.
        if (dev) {
          try {
            await dev.cancelConnection();
          } catch {
            // déjà fermé
          }
        }
        connectingRef.current.delete(deviceId);
        // Replanifie tant que le budget de tentatives n'est pas épuisé.
        scheduleReconnect(deviceId);
        return;
      }
      connectingRef.current.delete(deviceId);
    },
    [handleSample, persistDevices, scheduleReconnect, stopScan, syncDevices],
  );

  const setWheelCircumferenceMm = useCallback((mm: number) => {
    circMmRef.current = mm;
    setWheelMm(mm);
    setSetting(WHEEL_MM_KEY, String(mm));
  }, []);

  // Réf vers le dernier `connect` pour la reconnexion différée.
  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  // Nettoyage : annule toutes les reconnexions / le scan en attente au démontage.
  useEffect(() => {
    const timers = reconnectTimersRef.current;
    return () => {
      for (const t of timers.values()) clearTimeout(t);
      if (scanTimerRef.current) clearTimeout(scanTimerRef.current);
      releaseScan(scanOwner);
    };
  }, [scanOwner]);

  // Chargement initial de la circonférence de roue (préférence locale).
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
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Reconnexion aux capteurs mémorisés, pilotée par l'état de l'adaptateur BT.
  // `emitCurrentState` couvre le lancement ; un cycle BT off→on re-déclenche la
  // reconnexion. On appelle toujours connect() (même si un capteur est déjà
  // connecté au niveau BLE, il faut réattacher le moniteur), en PARALLÈLE pour
  // ne pas cumuler les timeouts capteur par capteur au démarrage.
  useEffect(() => {
    if (!SUPPORTED) return;
    const reconnectSaved = () => {
      getSetting(DEVICES_KEY).then((raw) => {
        if (!raw) return;
        try {
          const saved: CscDevice[] = JSON.parse(raw);
          for (const d of saved) {
            reconnectAttemptsRef.current.delete(d.id);
            connectRef.current?.(d.id);
          }
        } catch {
          // réglage illisible : on attend l'utilisateur
        }
      });
    };
    const sub = getManager().onStateChange((state) => {
      if (state === 'PoweredOff') {
        setCadenceRpm(null);
        setSpeedKmh(null);
        lastCadenceRef.current = null;
        lastSpeedRef.current = null;
        setError('Bluetooth désactivé.');
        setStatus((s) => (s === 'connected' || s === 'reconnecting' ? 'error' : s));
      } else if (state === 'PoweredOn') {
        setError(null);
        reconnectSaved();
      }
    }, true);
    return () => sub.remove();
  }, []);

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
