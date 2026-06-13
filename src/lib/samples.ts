// Agrégation et appariement temporel d'échantillons de capteurs (FC, cadence).
// Pur : aucune dépendance React/Expo — partagé par les écrans de séance
// (velo.tsx / muscu.tsx) et testé dans __tests__/lib/samples.test.ts.
import type { HrSample } from '@/lib/types';

/**
 * Moyenne (arrondie) et maximum d'une série de valeurs. Renvoie `null` quand la
 * série est vide — pour ne pas inscrire un faux 0 lorsqu'aucun capteur n'était
 * connecté (ex. séance sans ceinture cardiaque).
 */
export function meanMax(values: number[]): { avg: number | null; max: number | null } {
  if (values.length === 0) return { avg: null, max: null };
  let sum = 0;
  let max = values[0];
  for (const v of values) {
    sum += v;
    if (v > max) max = v;
  }
  return { avg: Math.round(sum / values.length), max };
}

/** Moyenne (arrondie) et max d'un buffer d'échantillons cardiaques. */
export function summarizeHr(samples: HrSample[]): { avgHr: number | null; maxHr: number | null } {
  const { avg, max } = meanMax(samples.map((s) => s.hr));
  return { avgHr: avg, maxHr: max };
}

/**
 * Moyenne « en mouvement » et max de cadence (tr/min). La moyenne exclut les
 * phases de roue libre (cadence 0) pour ne pas la diluer, mais le max porte sur
 * toutes les valeurs. `null` si aucun échantillon ; la moyenne est `null` si la
 * roue n'a jamais tourné (que des 0).
 */
export function summarizeCadence(
  rpms: number[],
): { avgCadence: number | null; maxCadence: number | null } {
  if (rpms.length === 0) return { avgCadence: null, maxCadence: null };
  let max = rpms[0];
  let sum = 0;
  let moving = 0;
  for (const v of rpms) {
    if (v > max) max = v;
    if (v > 0) {
      sum += v;
      moving++;
    }
  }
  if (moving === 0) return { avgCadence: null, maxCadence: max || null };
  return { avgCadence: Math.round(sum / moving), maxCadence: max };
}

/**
 * Ajoute un échantillon à un buffer en down-samplant les paliers : ignore une
 * valeur identique à la dernière reçue il y a moins de `minGapMs`. Borne ainsi
 * les buffers sur les longues sorties (FC/cadence stables) sans altérer
 * moyenne, max ni l'appariement temporel avec les points GPS. Mute `buf`.
 */
export function pushDownsampled<T extends { ts: number }>(
  buf: T[],
  sample: T,
  value: (s: T) => number,
  minGapMs = 1000,
): void {
  const last = buf[buf.length - 1];
  if (last && value(last) === value(sample) && sample.ts - last.ts < minGapMs) return;
  buf.push(sample);
}

/**
 * Valeur de l'échantillon temporellement le plus proche de `ts` (au-delà de
 * `toleranceMs`, renvoie `null`). Les échantillons sont supposés triés par `ts`
 * croissant (ordre de réception BLE) : recherche dichotomique en O(log N) au
 * lieu d'un scan par point GPS — sur une longue sortie cela évite plusieurs
 * secondes de calcul au moment de l'enregistrement.
 */
export function nearestSample<T extends { ts: number }>(
  samples: T[],
  ts: number,
  pick: (s: T) => number,
  toleranceMs = 10_000,
): number | null {
  if (samples.length === 0) return null;
  // Borne basse via recherche dichotomique : premier index dont ts >= cible.
  let lo = 0;
  let hi = samples.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (samples[mid].ts < ts) lo = mid + 1;
    else hi = mid;
  }
  const candidates: T[] = [];
  if (lo < samples.length) candidates.push(samples[lo]);
  if (lo > 0) candidates.push(samples[lo - 1]);
  let best = candidates[0];
  let bestDiff = Math.abs(best.ts - ts);
  for (let i = 1; i < candidates.length; i++) {
    const diff = Math.abs(candidates[i].ts - ts);
    if (diff < bestDiff) {
      bestDiff = diff;
      best = candidates[i];
    }
  }
  return bestDiff <= toleranceMs ? pick(best) : null;
}
