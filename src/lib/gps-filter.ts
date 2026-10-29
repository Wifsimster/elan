import { haversineMeters } from '@/lib/geo';

export type GpsFix = {
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  accuracy: number | null;
  altitudeAccuracy: number | null;
  speed: number | null;
};

export type ConsolidatedPoint = {
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  speedKmh: number | null;
};

export type ConsolidationResult = {
  point: ConsolidatedPoint | null;
  deltaDistanceM: number;
  deltaElevationGainM: number;
};

const MAX_ACCURACY_M = 50;
const MAX_PLAUSIBLE_SPEED_MS = 30;
const STANDSTILL_SPEED_MS = 0.7;
const DISTANCE_ANCHOR_M = 5;
const KALMAN_MIN_PROCESS_NOISE_MS = 3;
const ALTITUDE_WINDOW = 7;
const ELEVATION_HYSTERESIS_M = 5;
const MAX_ALTITUDE_ACCURACY_M = 12;
const DEFAULT_ACCURACY_M = 15;
const ALT_RESET_GAP_S = 30;

function wrapLonDelta(deltaDeg: number): number {
  let d = deltaDeg;
  while (d > 180) d -= 360;
  while (d < -180) d += 360;
  return d;
}

function normalizeLon(lon: number): number {
  return ((((lon + 180) % 360) + 360) % 360) - 180;
}

export class GpsConsolidator {
  private estLat = 0;
  private estLon = 0;
  private variance = -1;

  private lastTs = 0;
  private lastAccepted: ConsolidatedPoint | null = null;
  private distanceAnchor: ConsolidatedPoint | null = null;

  private altWindow: number[] = [];
  private elevationAnchor: number | null = null;

  process(fix: GpsFix): ConsolidationResult {
    const rejected: ConsolidationResult = {
      point: null,
      deltaDistanceM: 0,
      deltaElevationGainM: 0,
    };

    const accuracy = Math.max(fix.accuracy ?? DEFAULT_ACCURACY_M, 1);
    if (accuracy > MAX_ACCURACY_M) return rejected;

    const prev = this.lastAccepted;
    if (prev) {
      const dt = (fix.ts - prev.ts) / 1000;
      if (dt <= 0) return rejected;
      if (haversineMeters(prev, fix) / dt > MAX_PLAUSIBLE_SPEED_MS) return rejected;
    }

    if (this.variance < 0) {
      this.estLat = fix.lat;
      this.estLon = fix.lon;
      this.variance = accuracy * accuracy;
    } else {
      const dt = Math.max((fix.ts - this.lastTs) / 1000, 0.001);
      const q = Math.max(KALMAN_MIN_PROCESS_NOISE_MS, fix.speed ?? 0);
      this.variance += dt * q * q;
      const gain = this.variance / (this.variance + accuracy * accuracy);
      this.estLat += gain * (fix.lat - this.estLat);
      this.estLon = normalizeLon(this.estLon + gain * wrapLonDelta(fix.lon - this.estLon));
      this.variance *= 1 - gain;
    }

    if (this.lastTs > 0 && (fix.ts - this.lastTs) / 1000 > ALT_RESET_GAP_S) {
      this.altWindow = [];
    }
    this.lastTs = fix.ts;

    let smoothedAlt: number | null = null;
    if (
      fix.altitude != null &&
      (fix.altitudeAccuracy == null || fix.altitudeAccuracy <= MAX_ALTITUDE_ACCURACY_M)
    ) {
      this.altWindow.push(fix.altitude);
      if (this.altWindow.length > ALTITUDE_WINDOW) this.altWindow.shift();
      smoothedAlt = median(this.altWindow);
    }

    const point: ConsolidatedPoint = {
      ts: fix.ts,
      lat: this.estLat,
      lon: this.estLon,
      altitude: smoothedAlt,
      speedKmh: fix.speed != null && fix.speed >= 0 ? fix.speed * 3.6 : null,
    };

    let deltaDistanceM = 0;
    if (this.distanceAnchor == null) {
      this.distanceAnchor = point;
    } else {
      const moving = fix.speed == null || fix.speed >= STANDSTILL_SPEED_MS;
      const d = haversineMeters(this.distanceAnchor, point);
      if (moving && d >= DISTANCE_ANCHOR_M) {
        deltaDistanceM = d;
        this.distanceAnchor = point;
      }
    }

    let deltaElevationGainM = 0;
    if (smoothedAlt != null) {
      if (this.elevationAnchor == null) {
        this.elevationAnchor = smoothedAlt;
      } else if (smoothedAlt > this.elevationAnchor + ELEVATION_HYSTERESIS_M) {
        deltaElevationGainM = smoothedAlt - this.elevationAnchor;
        this.elevationAnchor = smoothedAlt;
      } else if (smoothedAlt < this.elevationAnchor - ELEVATION_HYSTERESIS_M) {
        this.elevationAnchor = smoothedAlt;
      }
    }

    this.lastAccepted = point;
    return { point, deltaDistanceM, deltaElevationGainM };
  }
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}
