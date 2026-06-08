// Aiguillage de décodage des fichiers d'export Strava, à partir d'octets bruts.
// Gère trois cas, dans cet ordre :
//   1. gzip (.gpx.gz / .tcx.gz / .fit.gz de l'export en masse) → décompression ;
//   2. FIT binaire (signature « .FIT ») → parseur binaire `fit.ts` ;
//   3. XML texte (GPX / TCX, export par activité) → parseur `parse.ts`.
// 100 % local, aucun appel réseau.

import { gunzipSync, strFromU8 } from 'fflate';

import { parseFit } from './fit';
import { parseStravaFile, type ParseResult } from './parse';

/** Taille maximale après décompression (octets) — garde-fou mémoire / DoS. */
const MAX_DECOMPRESSED_BYTES = 60 * 1024 * 1024;

function isGzip(b: Uint8Array): boolean {
  return b.length >= 2 && b[0] === 0x1f && b[1] === 0x8b;
}

/** Taille décompressée annoncée par l'en-tête gzip (champ ISIZE : 4 octets de
 *  fin, little-endian, modulo 2^32). Permet de refuser une « bombe » AVANT de
 *  l'inflater en mémoire. */
function declaredGunzipSize(b: Uint8Array): number {
  if (b.length < 4) return 0;
  const n = b.length;
  return (b[n - 4] | (b[n - 3] << 8) | (b[n - 2] << 16) | (b[n - 1] << 24)) >>> 0;
}

function isFit(b: Uint8Array): boolean {
  // Signature « .FIT » aux octets 8–11 de l'en-tête.
  return (
    b.length >= 12 && b[8] === 0x2e && b[9] === 0x46 && b[10] === 0x49 && b[11] === 0x54
  );
}

/** Décode un fichier d'export Strava (octets) vers la forme commune `ParseResult`. */
export function decodeStravaBytes(bytes: Uint8Array): ParseResult {
  let data = bytes;
  if (isGzip(data)) {
    // Garde-fou anti « zip bomb » : on rejette sur la taille annoncée AVANT
    // d'allouer le buffer décompressé…
    if (declaredGunzipSize(data) > MAX_DECOMPRESSED_BYTES) {
      throw new Error('Fichier décompressé trop volumineux.');
    }
    data = gunzipSync(data);
    // …puis on revérifie la taille réelle (ISIZE n'est pas fiable à 100 %).
    if (data.length > MAX_DECOMPRESSED_BYTES) {
      throw new Error('Fichier décompressé trop volumineux.');
    }
  }

  if (isFit(data)) {
    return { format: 'fit', activities: parseFit(data) };
  }

  // Sinon, on suppose du XML (GPX/TCX) : décodage UTF-8 puis parseur texte.
  return parseStravaFile(strFromU8(data));
}
