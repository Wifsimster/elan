// Connexion Bluetooth Low Energy à une ceinture cardiaque.
// Utilise le profil GATT standard « Heart Rate » (service 0x180D).
import { PermissionsAndroid, Platform } from 'react-native';
import { BleManager, type Device } from 'react-native-ble-plx';

/** Service & caractéristique standard « Heart Rate Measurement ». */
export const HEART_RATE_SERVICE = '0000180d-0000-1000-8000-00805f9b34fb';
export const HEART_RATE_MEASUREMENT = '00002a37-0000-1000-8000-00805f9b34fb';

/**
 * Service & caractéristique standard « Cycling Speed and Cadence » (CSC).
 * Couvre les capteurs iGPSPORT CAD70 (cadence) et SPD70 (vitesse), ainsi que
 * tout capteur vélo BLE conforme au profil GATT CSC.
 */
export const CSC_SERVICE = '00001816-0000-1000-8000-00805f9b34fb';
export const CSC_MEASUREMENT = '00002a5b-0000-1000-8000-00805f9b34fb';

let manager: BleManager | null = null;

/** BleManager partagé (créé paresseusement pour ne pas bloquer le démarrage web). */
export function getManager(): BleManager {
  if (!manager) manager = new BleManager();
  return manager;
}

/** Délai max d'une tentative de connexion / reconnexion (ms) : au-delà, on
 *  abandonne au lieu de bloquer la carte ~30 s (capteur endormi). */
export const CONNECT_TIMEOUT_MS = 10_000;

/** Durée max d'un scan avant arrêt automatique (ms) : la radio ne doit pas
 *  balayer indéfiniment après qu'on a quitté les Réglages. */
export const SCAN_TIMEOUT_MS = 20_000;

// ---------------------------------------------------------------------------
// Coordinateur de scan partagé
// ---------------------------------------------------------------------------
// La ceinture cardiaque et les capteurs vélo partagent le MÊME BleManager, donc
// un seul scan matériel à la fois. Sans coordination, lancer le scan de l'un
// pendant le scan de l'autre appelle `stopDeviceScan()` global et laisse la
// première carte bloquée sur « scan en cours ». Ici, un seul propriétaire à la
// fois : quand un nouveau scan démarre, l'ancien propriétaire est notifié
// (« scan volé ») pour remettre son état à l'arrêt.

let scanOwner: symbol | null = null;
let scanStolenCb: (() => void) | null = null;

/**
 * Prend possession du scan partagé. Si un autre propriétaire scannait, il est
 * notifié via son callback « volé » et le scan matériel est stoppé au préalable.
 * L'appelant enchaîne ensuite sur `getManager().startDeviceScan(...)`.
 */
export function acquireScan(owner: symbol, onStolen: () => void): void {
  if (scanOwner && scanOwner !== owner && scanStolenCb) scanStolenCb();
  getManager().stopDeviceScan();
  scanOwner = owner;
  scanStolenCb = onStolen;
}

/** Relâche le scan partagé et stoppe la radio — seulement si `owner` le détient
 *  encore (ne coupe pas le scan d'un propriétaire qui l'aurait « volé » depuis). */
export function releaseScan(owner: symbol): void {
  if (scanOwner === owner) {
    scanOwner = null;
    scanStolenCb = null;
    getManager().stopDeviceScan();
  }
}

/** Demande les permissions Bluetooth nécessaires (Android). */
export async function requestBlePermissions(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;

  const sdk = Platform.Version as number;
  const perms: string[] = [];
  if (sdk >= 31) {
    perms.push(
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
    );
  } else {
    perms.push(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION);
  }

  const result = await PermissionsAndroid.requestMultiple(perms as any);
  return Object.values(result).every((v) => v === PermissionsAndroid.RESULTS.GRANTED);
}

/** Décode une chaîne base64 en octets (sans dépendance Buffer). */
function base64ToBytes(b64: string): Uint8Array {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const clean = b64.replace(/=+$/, '');
  const bytes: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (const ch of clean) {
    const idx = chars.indexOf(ch);
    if (idx === -1) continue;
    buffer = (buffer << 6) | idx;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((buffer >> bits) & 0xff);
    }
  }
  return Uint8Array.from(bytes);
}

/**
 * Extrait la fréquence cardiaque (bpm) d'une valeur de caractéristique base64.
 * Format GATT : octet 0 = drapeaux ; bit 0 indique une valeur 8 bits (0) ou 16 bits (1).
 */
export function parseHeartRate(base64Value: string | null): number | null {
  if (!base64Value) return null;
  const data = base64ToBytes(base64Value);
  if (data.length < 2) return null;
  const flags = data[0];
  const is16bit = (flags & 0x01) === 0x01;
  // Une trame 16 bits annoncée mais tronquée à 2 octets ferait lire `data[2]`
  // = undefined (→ silencieusement la valeur basse seule). On rejette plutôt
  // que de remonter une FC fausse à partir d'une trame capteur malformée.
  const bpm = is16bit ? (data.length < 3 ? null : data[1] | (data[2] << 8)) : data[1];
  // FC = 0 : contact perdu (bit « Sensor Contact » à 0 ou trame de repli du
  // capteur). Ce n'est pas une valeur physiologique — on la traite comme
  // « absente » plutôt que de l'afficher et de la mêler aux agrégats (moyenne
  // tirée vers le bas, point GPS avec FC 0).
  if (bpm == null || bpm <= 0) return null;
  return bpm;
}

/**
 * Compteurs bruts d'une mesure CSC (caractéristique 0x2A5B). Un capteur peut
 * fournir la roue (vitesse), le pédalier (cadence) ou les deux ; les champs
 * absents valent `null`. Les temps sont en 1/1024 s et bouclent à 65536.
 */
export type CscRaw = {
  /** Révolutions de roue cumulées (uint32). */
  wheelRevs: number | null;
  /** Horodatage du dernier passage de roue, en 1/1024 s (uint16). */
  wheelTime: number | null;
  /** Révolutions de pédalier cumulées (uint16). */
  crankRevs: number | null;
  /** Horodatage du dernier tour de pédalier, en 1/1024 s (uint16). */
  crankTime: number | null;
};

/** Lit un entier little-endian non signé de `len` octets à partir de `off`. */
function readLE(d: Uint8Array, off: number, len: number): number {
  let v = 0;
  for (let i = 0; i < len; i++) v += d[off + i] * 2 ** (8 * i);
  return v;
}

/**
 * Décode une mesure CSC base64. Octet 0 = drapeaux :
 * bit 0 → données roue présentes, bit 1 → données pédalier présentes.
 */
export function parseCsc(base64Value: string | null): CscRaw | null {
  if (!base64Value) return null;
  const d = base64ToBytes(base64Value);
  if (d.length < 1) return null;

  const flags = d[0];
  const wheelPresent = (flags & 0x01) === 0x01;
  const crankPresent = (flags & 0x02) === 0x02;

  let i = 1;
  const out: CscRaw = { wheelRevs: null, wheelTime: null, crankRevs: null, crankTime: null };

  if (wheelPresent) {
    if (d.length < i + 6) return null;
    out.wheelRevs = readLE(d, i, 4);
    out.wheelTime = readLE(d, i + 4, 2);
    i += 6;
  }
  if (crankPresent) {
    if (d.length < i + 4) return null;
    out.crankRevs = readLE(d, i, 2);
    out.crankTime = readLE(d, i + 2, 2);
  }
  return out;
}

export type { Device };
