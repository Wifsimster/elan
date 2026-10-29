import { haversineMeters } from '@/lib/geo';

export type TimedPoint = {
  ts: number;
  lat: number;
  lon: number;
  speedKmh?: number | null;
};

const MOVING_THRESHOLD_MS = 0.7;
const MIN_SEGMENT_M = 4;
const MAX_SEGMENT_SEC = 30;

export function movingTimeSec(points: TimedPoint[]): number {
  if (points.length < 2) return 0;
  let sec = 0;
  for (let i = 1; i < points.length; i++) {
    const prev = points[i - 1];
    const cur = points[i];
    const dt = (cur.ts - prev.ts) / 1000;
    if (dt <= 0) continue;

    const dopplerMs = cur.speedKmh != null ? cur.speedKmh / 3.6 : null;
    let moving: boolean;
    if (dopplerMs != null) {
      moving = dopplerMs >= MOVING_THRESHOLD_MS;
    } else {
      const d = haversineMeters(prev, cur);
      moving = d >= MIN_SEGMENT_M && d / dt >= MOVING_THRESHOLD_MS;
    }
    if (moving) sec += Math.min(dt, MAX_SEGMENT_SEC);
  }
  return Math.round(sec);
}
