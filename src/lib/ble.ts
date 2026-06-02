// Connexion Bluetooth Low Energy à une ceinture cardiaque.
// Utilise le profil GATT standard « Heart Rate » (service 0x180D).
import { PermissionsAndroid, Platform } from 'react-native';
import { BleManager, type Device } from 'react-native-ble-plx';

/** Service & caractéristique standard « Heart Rate Measurement ». */
export const HEART_RATE_SERVICE = '0000180d-0000-1000-8000-00805f9b34fb';
export const HEART_RATE_MEASUREMENT = '00002a37-0000-1000-8000-00805f9b34fb';

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
  return is16bit ? data[1] | (data[2] << 8) : data[1];
}

export type { Device };
