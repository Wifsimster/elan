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
  // Abonnement onDisconnected : conservé pour être retiré (sinon les handlers
  // s'accumulent à chaque reconnexion et une seule coupure en déclenche N).
  const disconnectSubRef = useRef<Subscription | null>(null);
  const connectedRef = useRef<Device | null>(null);
  // Garde anti-double-connexion (tentative déjà en vol).
  const connectingRef = useRef(false);
  const listenersRef = useRef<Set<(s: HrSample) => void>>(new Set());
  // Reconnexion auto sur coupure involontaire (capteur hors de portée, etc.).
  const userDisconnectedRef = useRef(false);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const connectRef = useRef<((deviceId: string) => Promise<void>) | null>(null);
  // Propriétaire du scan partagé (coordinateur lib/ble) + timeout d'auto-arrêt.
  // useState (initialiseur paresseux) : jeton stable propre à cette instance.
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
    // Coordination du scan partagé : si les capteurs vélo scannaient, ils sont
    // notifiés et arrêtés ; « scan volé » nous ramène à l'arrêt si l'inverse
    // se produit (au lieu de rester bloqué sur « scan en cours »).
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
    // Déconnexion volontaire : on désarme la reconnexion auto et on annule une
    // tentative en attente AVANT cancelConnection (onDisconnected suit aussitôt).
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
        // déjà déconnecté
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
      // Garde anti-double-connexion : une tentative déjà en vol, ou une connexion
      // au même appareil, ne doit pas en lancer une seconde (deux moniteurs
      // mêleraient leurs valeurs). Une connexion à un AUTRE appareil déconnecte
      // d'abord le précédent. `connectingRef` est armé AVANT tout `await` (la
      // déconnexion du précédent en contient un) pour fermer la fenêtre de course.
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
          // déjà déconnecté
        }
      }
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
      let dev: Device | null = null;
      try {
        const ok = await requestBlePermissions();
        if (!ok) throw new Error('Permissions Bluetooth refusées.');

        const manager = getManager();
        // Timeout : un capteur endormi ne doit pas bloquer la carte ~30 s.
        dev = await manager.connectToDevice(deviceId, {
          autoConnect: false,
          timeout: CONNECT_TIMEOUT_MS,
        });
        dev = await dev.discoverAllServicesAndCharacteristics();
        connectedRef.current = dev;

        // Ancien abonnement retiré avant d'en réenregistrer un (anti-accumulation).
        disconnectSubRef.current?.remove();
        disconnectSubRef.current = dev.onDisconnected(() => {
          disconnectSubRef.current?.remove();
          disconnectSubRef.current = null;
          monitorRef.current?.remove();
          monitorRef.current = null;
          connectedRef.current = null;
          setBpm(null);
          setDevice(null);
          scheduleReconnect(deviceId); // coupure involontaire : back-off borné
        });

        // Ancien moniteur retiré avant d'en réattacher un.
        monitorRef.current?.remove();
        monitorRef.current = dev.monitorCharacteristicForService(
          HEART_RATE_SERVICE,
          HEART_RATE_MEASUREMENT,
          (err, characteristic) => {
            if (err) return;
            const value = parseHeartRate(characteristic?.value ?? null);
            if (value == null) return; // trame invalide ou FC 0 (contact perdu)
            setBpm(value);
            // Notifie chaque trame brute (même valeur identique) pour éviter
            // les trous d'échantillonnage pendant un palier cardiaque.
            const sample: HrSample = { ts: nowMs(), hr: value };
            for (const cb of listenersRef.current) cb(sample);
          },
        );

        const info: ScannedDevice = { id: dev.id, name: dev.name ?? 'Ceinture cardiaque' };
        setDevice(info);
        setScanned([]); // liste de scan devenue obsolète, ne plus la laisser tapable
        setStatus('connected');
        reconnectAttemptsRef.current = 0; // connexion établie : compteur remis à zéro
        await setSetting(LAST_DEVICE_KEY, JSON.stringify(info));
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Échec de connexion.');
        connectedRef.current = null;
        // Découverte/monitoring échoué après connexion (GATT 133 fréquent) :
        // l'appareil reste semi-connecté et indétectable au scan → on force la
        // fermeture GATT.
        if (dev) {
          try {
            await dev.cancelConnection();
          } catch {
            // déjà fermé
          }
        }
        // Replanifie tant que le budget de tentatives n'est pas épuisé (la chaîne
        // de reconnexion ne doit pas mourir au premier échec).
        connectingRef.current = false;
        scheduleReconnect(deviceId);
        return;
      }
      connectingRef.current = false;
    },
    [scheduleReconnect, stopScan],
  );

  // Réf vers le dernier `connect` (appelé par la reconnexion différée, sans
  // recréer le timer à chaque changement d'identité de connect).
  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  // Nettoyage : annule une reconnexion / un scan en attente au démontage.
  useEffect(() => {
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      if (scanTimerRef.current) clearTimeout(scanTimerRef.current);
      releaseScan(scanOwner);
    };
  }, [scanOwner]);

  // Écoute l'état de l'adaptateur Bluetooth. `emitCurrentState` déclenche aussi
  // la tentative de reconnexion initiale au lancement (on appelle toujours
  // connect() — même si l'appareil est déjà connecté au niveau BLE, il faut
  // (ré)attacher le moniteur, surtout après un reload de dev). Et un cycle
  // BT off→on re-déclenche la reconnexion au lieu de rester en « Erreur ».
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
          // réglage illisible : on attend l'action utilisateur
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
