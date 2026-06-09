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
  if (is16bit) return data.length < 3 ? null : data[1] | (data[2] << 8);
  return data[1];
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
