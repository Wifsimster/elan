import { sha256 } from 'js-sha256';

import { estimateCalories } from '@/lib/calories';
import { haversineMeters } from '@/lib/geo';
import { movingTimeSec } from '@/lib/moving-time';
import { decodeStravaBytes } from '@/lib/strava/decode';
import { type ParsedActivity, type ParsedPoint } from '@/lib/strava/parse';
import type { ActivityType, TrackPoint } from '@/lib/types';

export type ImportedPoint = Omit<TrackPoint, 'id' | 'sessionId'>;

export type ImportedSession = {
  type: ActivityType;
  startedAt: number;
  endedAt: number;
  durationSec: number;
  movingTimeSec: number | null;
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

const MAX_PLAUSIBLE_SPEED_KMH = 160;
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
    if (v == null || v <= 0) continue;
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

function normalize(act: ParsedActivity, weightKg: number): ImportedDraft | string {
  if (act.sport === 'other') return 'activité non supportée ignorée';
  const type: ActivityType =
    act.sport === 'running' ? 'course' : act.sport === 'walking' ? 'marche' : 'velo';

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
  if (durationSec <= 0) return 'séance trop courte ou incomplète';

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
      if (dtSec > 0) {
        const s = (dM / dtSec) * 3.6;
        if (s <= MAX_PLAUSIBLE_SPEED_KMH) {
          accDist += dM;
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

  const moving = movingTimeSec(outPoints);
  const movingTimeSec_ = moving > 0 ? moving : null;
  const effectiveSec = moving > 0 ? moving : durationSec;
  const avgSpeedKmh =
    distanceM != null && effectiveSec > 0 ? (distanceM / effectiveSec) * 3.6 : null;
  const elevationGainM = computeGain(timed);
  const { avg: avgHr, max: maxHr } = avgMax(timed.map((p) => p.hr));
  const { avg: avgCadence, max: maxCadence } = avgMax(timed.map((p) => p.cad));

  const calories =
    act.calories != null
      ? act.calories
      : effectiveSec > 0
        ? estimateCalories({ type, weightKg, durationSec: effectiveSec, avgSpeedKmh })
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
      type,
      startedAt,
      endedAt,
      durationSec,
      movingTimeSec: movingTimeSec_,
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

export function buildDrafts(bytes: Uint8Array, weightKg: number): BuildResult {
  const parsed = decodeStravaBytes(bytes);
  const drafts: ImportedDraft[] = [];
  const skipped: string[] = [];
  for (const act of parsed.activities) {
    const r = normalize(act, weightKg);
    if (typeof r === 'string') skipped.push(r);
    else drafts.push(r);
  }
  return { drafts, skipped };
}
