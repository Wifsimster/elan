import { haversineMeters } from '@/lib/geo';
import { movingTimeSec } from '@/lib/moving-time';

export type AggPoint = {
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  speedKmh: number | null;
  hr: number | null;
  cadence: number | null;
};

export type SessionAggregate = {
  durationSec: number;
  movingTimeSec: number | null;
  distanceM: number | null;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  elevationGainM: number | null;
  avgHr: number | null;
  maxHr: number | null;
  avgCadence: number | null;
  maxCadence: number | null;
  endedAt: number;
};

const STANDSTILL_MS = 0.8;
const MIN_SEGMENT_M = 4;
const MAX_PLAUSIBLE_KMH = 120;
const ELEVATION_NOISE_M = 1;

function avgMaxPositive(values: (number | null)[]): { avg: number | null; max: number | null } {
  let sum = 0;
  let count = 0;
  let max: number | null = null;
  for (const v of values) {
    if (v == null || v <= 0) continue;
    sum += v;
    count++;
    if (max == null || v > max) max = v;
  }
  return count === 0 ? { avg: null, max } : { avg: Math.round(sum / count), max };
}

export function aggregateFromPoints(points: AggPoint[]): SessionAggregate | null {
  if (points.length < 2) return null;

  const startedAt = points[0].ts;
  const endedAt = points[points.length - 1].ts;
  const durationSec = Math.max(0, Math.round((endedAt - startedAt) / 1000));

  let distanceM = 0;
  let elevationGainM = 0;
  let anyAltitude = false;
  let prevAlt: number | null = null;
  for (let i = 1; i < points.length; i++) {
    const prev = points[i - 1];
    const cur = points[i];
    const dt = (cur.ts - prev.ts) / 1000;
    const d = haversineMeters(prev, cur);
    if (dt > 0) {
      const implicitKmh = (d / dt) * 3.6;
      const dopplerMs = cur.speedKmh != null ? cur.speedKmh / 3.6 : null;
      const moving =
        dopplerMs != null ? dopplerMs >= STANDSTILL_MS : d >= MIN_SEGMENT_M && d / dt >= STANDSTILL_MS;
      if (moving && implicitKmh <= MAX_PLAUSIBLE_KMH) distanceM += d;
    }
    if (cur.altitude != null) {
      anyAltitude = true;
      if (prevAlt != null && cur.altitude - prevAlt > ELEVATION_NOISE_M) {
        elevationGainM += cur.altitude - prevAlt;
      }
      prevAlt = cur.altitude;
    }
  }

  const moving = movingTimeSec(points);
  const effectiveSec = moving > 0 ? moving : durationSec;
  const avgSpeedKmh =
    distanceM > 0 && effectiveSec > 0 ? distanceM / 1000 / (effectiveSec / 3600) : null;

  let maxSpeedKmh: number | null = null;
  for (const p of points) {
    if (p.speedKmh != null && p.speedKmh <= MAX_PLAUSIBLE_KMH) {
      if (maxSpeedKmh == null || p.speedKmh > maxSpeedKmh) maxSpeedKmh = p.speedKmh;
    }
  }

  const { avg: avgHr, max: maxHr } = avgMaxPositive(points.map((p) => p.hr));
  const { avg: avgCadence, max: maxCadence } = avgMaxPositive(points.map((p) => p.cadence));

  return {
    durationSec,
    movingTimeSec: moving > 0 ? moving : null,
    distanceM: distanceM > 0 ? distanceM : null,
    avgSpeedKmh,
    maxSpeedKmh,
    elevationGainM: anyAltitude ? Math.round(elevationGainM) : null,
    avgHr,
    maxHr,
    avgCadence,
    maxCadence,
    endedAt,
  };
}
