// Normalisation des activités Strava parsées vers le modèle du domaine.
// Pur (aucun accès base/IO) : parse → normalise → dédup-clé. L'insertion est
// faite par `insertImportedSession` (db.ts) et l'orchestration IO par le hook.
import { sha256 } from 'js-sha256';

import { estimateCalories } from '@/lib/calories';
import { haversineMeters } from '@/lib/geo';
import { parseStravaFile, type ParsedActivity, type ParsedPoint } from '@/lib/strava/parse';
import type { TrackPoint } from '@/lib/types';

export type ImportedPoint = Omit<TrackPoint, 'id' | 'sessionId'>;

export type ImportedSession = {
  type: 'velo';
  startedAt: number;
  endedAt: number;
  durationSec: number;
  notes: string | null;
  avgHr: number | null;
  maxHr: number | null;
  distanceM: number | null;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  elevationGainM: number | null;
  avgCadence: number | null;
  maxCadence: number | null;
  calories: number | null;
  source: 'strava';
  externalId: string;
};

export type ImportedDraft = { session: ImportedSession; points: ImportedPoint[] };

export type BuildResult = { drafts: ImportedDraft[]; skipped: string[] };

/** Garde-fou contre les pics GPS (un saut de point produit une vitesse absurde). */
const MAX_PLAUSIBLE_SPEED_KMH = 160;
/** Seuil anti-bruit pour le dénivelé positif (m). */
const ELEVATION_NOISE_M = 0.5;

function isValidLatLon(lat: number | null, lon: number | null): boolean {
  return (
    lat != null &&
    lon != null &&
    lat >= -90 &&
    lat <= 90 &&
    lon >= -180 &&
    lon <= 180 &&
    !(lat === 0 && lon === 0)
  );
}

function avgMax(values: (number | null)[]): { avg: number | null; max: number | null } {
  let sum = 0;
  let count = 0;
  let max: number | null = null;
  for (const v of values) {
    if (v == null) continue;
    sum += v;
    count++;
    if (max == null || v > max) max = v;
  }
  return count === 0 ? { avg: null, max: null } : { avg: Math.round(sum / count), max };
}

function computeGain(points: ParsedPoint[]): number | null {
  let gain = 0;
  let prevEle: number | null = null;
  let any = false;
  for (const p of points) {
    if (p.ele == null) continue;
    any = true;
    if (prevEle != null) {
      const d = p.ele - prevEle;
      if (d > ELEVATION_NOISE_M) gain += d;
    }
    prevEle = p.ele;
  }
  return any ? Math.round(gain) : null;
}

/** Normalise une activité ; renvoie un motif (string) si elle doit être ignorée. */
function normalize(act: ParsedActivity, weightKg: number): ImportedDraft | string {
  if (act.sport === 'other') return 'activité non vélo ignorée';

  const timed = act.points.filter((p) => p.ts != null);
  if (timed.length === 0) return 'aucun horodatage exploitable';

  let startedAt = timed[0].ts as number;
  let endedAt = startedAt;
  for (const p of timed) {
    const t = p.ts as number;
    if (t < startedAt) startedAt = t;
    if (t > endedAt) endedAt = t;
  }
  const durationSec = Math.max(0, Math.round((endedAt - startedAt) / 1000));

  // Points GPS valides → seuls ceux-là sont insérés (lat/lon NOT NULL en base).
  const gps = timed.filter((p) => isValidLatLon(p.lat, p.lon));

  let distanceM = act.distanceM;
  let maxSpeedKmh: number | null = null;
  const outPoints: ImportedPoint[] = [];
  let prev: ParsedPoint | null = null;
  let accDist = 0;

  for (const p of gps) {
    let speedKmh: number | null = null;
    if (prev && prev.ts != null && p.ts != null) {
      const dtSec = (p.ts - prev.ts) / 1000;
      const dM = haversineMeters(
        { lat: prev.lat as number, lon: prev.lon as number },
        { lat: p.lat as number, lon: p.lon as number },
      );
      accDist += dM;
      if (dtSec > 0) {
        const s = (dM / dtSec) * 3.6;
        if (s <= MAX_PLAUSIBLE_SPEED_KMH) {
          speedKmh = s;
          if (maxSpeedKmh == null || s > maxSpeedKmh) maxSpeedKmh = s;
        }
      }
    }
    outPoints.push({
      ts: p.ts as number,
      lat: p.lat as number,
      lon: p.lon as number,
      altitude: p.ele,
      speedKmh,
      hr: p.hr,
      cadence: p.cad,
    });
    prev = p;
  }
  if (distanceM == null && gps.length >= 2) distanceM = accDist;

  const avgSpeedKmh =
    distanceM != null && durationSec > 0 ? (distanceM / durationSec) * 3.6 : null;
  const elevationGainM = computeGain(gps);
  const { avg: avgHr, max: maxHr } = avgMax(timed.map((p) => p.hr));
  const { avg: avgCadence, max: maxCadence } = avgMax(timed.map((p) => p.cad));

  const calories =
    act.calories != null
      ? act.calories
      : durationSec > 0
        ? estimateCalories({ type: 'velo', weightKg, durationSec, avgSpeedKmh })
        : null;

  const first = gps[0];
  const externalId =
    'strava-' +
    sha256(
      [startedAt, durationSec, Math.round(distanceM ?? 0), first?.lat ?? 'na', first?.lon ?? 'na'].join(
        '|',
      ),
    ).slice(0, 24);

  return {
    session: {
      type: 'velo',
      startedAt,
      endedAt,
      durationSec,
      notes: 'Importé depuis Strava',
      avgHr,
      maxHr,
      distanceM,
      avgSpeedKmh,
      maxSpeedKmh,
      elevationGainM,
      avgCadence,
      maxCadence,
      calories,
      source: 'strava',
      externalId,
    },
    points: outPoints,
  };
}

/** Parse le contenu d'un fichier et construit les séances importables. */
export function buildDrafts(content: string, weightKg: number): BuildResult {
  const parsed = parseStravaFile(content);
  const drafts: ImportedDraft[] = [];
  const skipped: string[] = [];
  for (const act of parsed.activities) {
    const r = normalize(act, weightKg);
    if (typeof r === 'string') skipped.push(r);
    else drafts.push(r);
  }
  return { drafts, skipped };
}
