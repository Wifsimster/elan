// Décodeur FIT (Flexible and Interoperable Data Transfer) en JavaScript pur —
// aucun module natif. C'est le format binaire produit par les capteurs
// Garmin/Wahoo/Coros et renvoyé par l'« export original » et l'export en masse
// de Strava (souvent compressé en .gz, décompressé en amont par `decode.ts`).
//
// On ne décode que ce dont l'app a besoin (messages `record` + résumé `session`)
// et on renvoie la même forme `ParsedActivity` que le parseur GPX/TCX, pour que
// la normalisation aval (`import.ts`) soit identique quel que soit le format.
//
// Référence : FIT Protocol — en-tête (12/14 o) + enregistrements (définition /
// données) + CRC final. Positions en semicercles, horodatage en secondes depuis
// l'époque FIT (1989-12-31), altitude/vitesse à l'échelle.

import type { ParsedActivity, ParsedPoint } from './parse';

/** Décalage entre l'époque FIT (1989-12-31T00:00:00Z) et l'époque Unix, en s. */
const FIT_EPOCH_OFFSET_S = 631065600;
/** Conversion semicercles → degrés : 180 / 2^31. */
const SEMICIRCLE_TO_DEG = 180 / 2147483648;

/** Numéros de message globaux exploités. */
const MSG_RECORD = 20;
const MSG_SESSION = 18;
const MSG_SPORT = 12;

type FieldDef = { num: number; size: number; baseType: number };
type MsgDef = { globalNum: number; littleEndian: boolean; fields: FieldDef[]; devSize: number };

/** Lit un champ scalaire selon son type de base FIT ; null si valeur « invalide ». */
function readNumber(view: DataView, offset: number, baseType: number, le: boolean): number | null {
  switch (baseType) {
    case 0x00: // enum
    case 0x02: // uint8
    case 0x0a: // uint8z
    case 0x0d: {
      // byte
      const v = view.getUint8(offset);
      return v === 0xff ? null : v;
    }
    case 0x01: {
      // sint8
      const v = view.getInt8(offset);
      return v === 0x7f ? null : v;
    }
    case 0x84: // uint16
    case 0x8b: {
      // uint16z
      const v = view.getUint16(offset, le);
      return v === 0xffff ? null : v;
    }
    case 0x83: {
      // sint16
      const v = view.getInt16(offset, le);
      return v === 0x7fff ? null : v;
    }
    case 0x86: // uint32
    case 0x8c: {
      // uint32z
      const v = view.getUint32(offset, le);
      return v === 0xffffffff ? null : v;
    }
    case 0x85: {
      // sint32
      const v = view.getInt32(offset, le);
      return v === 0x7fffffff ? null : v;
    }
    case 0x88: {
      // float32
      const v = view.getFloat32(offset, le);
      return Number.isNaN(v) ? null : v;
    }
    case 0x89: {
      // float64
      const v = view.getFloat64(offset, le);
      return Number.isNaN(v) ? null : v;
    }
    default:
      return null; // types non gérés (string, int64…) — ignorés
  }
}

/** Convertit un horodatage FIT (s depuis l'époque FIT) en ms epoch Unix. */
function fitTimeToMs(fitSeconds: number): number {
  return (fitSeconds + FIT_EPOCH_OFFSET_S) * 1000;
}

/** Mappe l'enum `sport` FIT vers la catégorie du domaine. */
function mapSport(sport: number | null): ParsedActivity['sport'] {
  if (sport == null) return 'unknown';
  if (sport === 2) return 'cycling'; // 2 = cycling
  if (sport === 0) return 'unknown'; // 0 = generic
  return 'other';
}

/**
 * Décode un fichier FIT (déjà décompressé) et renvoie une activité unique.
 * Lève si la signature `.FIT` est absente ou l'en-tête invalide.
 */
export function parseFit(bytes: Uint8Array): ParsedActivity[] {
  if (bytes.length < 14) throw new Error('FIT : fichier trop court.');
  const headerSize = bytes[0];
  if (
    bytes.length < headerSize ||
    bytes[8] !== 0x2e ||
    bytes[9] !== 0x46 ||
    bytes[10] !== 0x49 ||
    bytes[11] !== 0x54
  ) {
    throw new Error('FIT : signature « .FIT » absente.');
  }

  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const dataSize = view.getUint32(4, true);
  // Fin de la zone de données : exclut le CRC final (2 octets), borné au tampon.
  const dataEnd = Math.min(headerSize + dataSize, bytes.length - 2);

  const defs: Record<number, MsgDef> = {};
  const points: ParsedPoint[] = [];
  let sport: ParsedActivity['sport'] = 'unknown';
  let startedAt: number | null = null;
  let distanceM: number | null = null;
  let calories: number | null = null;
  /** Dernier horodatage absolu connu (s FIT), pour les en-têtes compressés. */
  let lastTimestamp: number | null = null;

  let pos = headerSize;
  while (pos < dataEnd) {
    const header = bytes[pos++];

    // En-tête définition (bit 6) : décrit la disposition d'un type local.
    if ((header & 0x80) === 0 && (header & 0x40) !== 0) {
      const localType = header & 0x0f;
      const hasDev = (header & 0x20) !== 0;
      pos++; // octet réservé
      const le = bytes[pos++] === 0; // architecture : 0 = little-endian
      const globalNum = view.getUint16(pos, le);
      pos += 2;
      const numFields = bytes[pos++];
      const fields: FieldDef[] = [];
      for (let i = 0; i < numFields; i++) {
        fields.push({ num: bytes[pos], size: bytes[pos + 1], baseType: bytes[pos + 2] });
        pos += 3;
      }
      let devSize = 0;
      if (hasDev) {
        const numDev = bytes[pos++];
        for (let i = 0; i < numDev; i++) {
          devSize += bytes[pos + 1];
          pos += 3;
        }
      }
      defs[localType] = { globalNum, littleEndian: le, fields, devSize };
      continue;
    }

    // En-tête données : normal (bit 7 = 0) ou horodatage compressé (bit 7 = 1).
    const compressed = (header & 0x80) !== 0;
    const localType = compressed ? (header >> 5) & 0x03 : header & 0x0f;
    const def = defs[localType];
    if (!def) break; // données sans définition préalable → flux illisible

    let compressedTs: number | null = null;
    if (compressed && lastTimestamp != null) {
      const offset = header & 0x1f;
      if (offset >= (lastTimestamp & 0x1f)) lastTimestamp = (lastTimestamp & ~0x1f) + offset;
      else lastTimestamp = (lastTimestamp & ~0x1f) + offset + 0x20;
      compressedTs = lastTimestamp;
    }

    // Lit chaque champ dans une table {numéro → valeur}.
    const values: Record<number, number | null> = {};
    for (const f of def.fields) {
      if (pos + f.size > bytes.length) {
        pos = bytes.length; // tronqué → on arrête proprement
        break;
      }
      values[f.num] = readNumber(view, pos, f.baseType, def.littleEndian);
      pos += f.size;
    }
    pos += def.devSize;

    if (def.globalNum === MSG_RECORD) {
      let ts: number | null = null;
      if (values[253] != null) {
        lastTimestamp = values[253];
        ts = fitTimeToMs(values[253]);
      } else if (compressedTs != null) {
        ts = fitTimeToMs(compressedTs);
      }
      const lat = values[0] != null ? values[0] * SEMICIRCLE_TO_DEG : null;
      const lon = values[1] != null ? values[1] * SEMICIRCLE_TO_DEG : null;
      // Altitude : préfère « enhanced » (78) sinon le champ standard (2).
      const altRaw = values[78] != null ? values[78] : values[2];
      const ele = altRaw != null ? altRaw / 5 - 500 : null;
      points.push({ ts, lat, lon, ele, hr: values[3] ?? null, cad: values[4] ?? null });
    } else if (def.globalNum === MSG_SESSION) {
      if (values[5] != null) sport = mapSport(values[5]); // 5 = sport
      if (values[2] != null) startedAt = fitTimeToMs(values[2]); // 2 = start_time
      if (values[9] != null) distanceM = values[9] / 100; // 9 = total_distance (cm→m)
      if (values[11] != null) calories = values[11]; // 11 = total_calories (kcal)
    } else if (def.globalNum === MSG_SPORT && sport === 'unknown') {
      if (values[0] != null) sport = mapSport(values[0]); // 0 = sport
    }
  }

  const firstTimed = points.find((p) => p.ts != null);
  return [
    {
      sport,
      startedAt: startedAt ?? firstTimed?.ts ?? null,
      points,
      distanceM,
      calories,
    },
  ];
}
