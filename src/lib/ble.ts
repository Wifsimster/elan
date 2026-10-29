import { PermissionsAndroid, Platform } from 'react-native';
import { BleManager, type Device } from 'react-native-ble-plx';

export const HEART_RATE_SERVICE = '0000180d-0000-1000-8000-00805f9b34fb';
export const HEART_RATE_MEASUREMENT = '00002a37-0000-1000-8000-00805f9b34fb';

export const CSC_SERVICE = '00001816-0000-1000-8000-00805f9b34fb';
export const CSC_MEASUREMENT = '00002a5b-0000-1000-8000-00805f9b34fb';

let manager: BleManager | null = null;

export function getManager(): BleManager {
  if (!manager) manager = new BleManager();
  return manager;
}

export const CONNECT_TIMEOUT_MS = 10_000;

export const SCAN_TIMEOUT_MS = 20_000;

let scanOwner: symbol | null = null;
let scanStolenCb: (() => void) | null = null;

export function acquireScan(owner: symbol, onStolen: () => void): void {
  if (scanOwner && scanOwner !== owner && scanStolenCb) scanStolenCb();
  getManager().stopDeviceScan();
  scanOwner = owner;
  scanStolenCb = onStolen;
}

export function releaseScan(owner: symbol): void {
  if (scanOwner === owner) {
    scanOwner = null;
    scanStolenCb = null;
    getManager().stopDeviceScan();
  }
}

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

export function parseHeartRate(base64Value: string | null): number | null {
  if (!base64Value) return null;
  const data = base64ToBytes(base64Value);
  if (data.length < 2) return null;
  const flags = data[0];
  const is16bit = (flags & 0x01) === 0x01;
  const bpm = is16bit ? (data.length < 3 ? null : data[1] | (data[2] << 8)) : data[1];
  if (bpm == null || bpm <= 0) return null;
  return bpm;
}

export type CscRaw = {
  wheelRevs: number | null;
  wheelTime: number | null;
  crankRevs: number | null;
  crankTime: number | null;
};

function readLE(d: Uint8Array, off: number, len: number): number {
  let v = 0;
  for (let i = 0; i < len; i++) v += d[off + i] * 2 ** (8 * i);
  return v;
}

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
