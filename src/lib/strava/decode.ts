import { gunzipSync, strFromU8 } from 'fflate';

import { parseFit } from './fit';
import { parseStravaFile, type ParseResult } from './parse';

const MAX_DECOMPRESSED_BYTES = 60 * 1024 * 1024;

function isGzip(b: Uint8Array): boolean {
  return b.length >= 2 && b[0] === 0x1f && b[1] === 0x8b;
}

function declaredGunzipSize(b: Uint8Array): number {
  if (b.length < 4) return 0;
  const n = b.length;
  return (b[n - 4] | (b[n - 3] << 8) | (b[n - 2] << 16) | (b[n - 1] << 24)) >>> 0;
}

function isFit(b: Uint8Array): boolean {
  return (
    b.length >= 12 && b[8] === 0x2e && b[9] === 0x46 && b[10] === 0x49 && b[11] === 0x54
  );
}

export function decodeStravaBytes(bytes: Uint8Array): ParseResult {
  let data = bytes;
  if (isGzip(data)) {
    if (declaredGunzipSize(data) > MAX_DECOMPRESSED_BYTES) {
      throw new Error('Fichier décompressé trop volumineux.');
    }
    data = gunzipSync(data);
    if (data.length > MAX_DECOMPRESSED_BYTES) {
      throw new Error('Fichier décompressé trop volumineux.');
    }
  }

  if (isFit(data)) {
    return { format: 'fit', activities: parseFit(data) };
  }

  return parseStravaFile(strFromU8(data));
}
