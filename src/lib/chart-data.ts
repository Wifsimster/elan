import { haversineMeters } from '@/lib/geo';
import type { TrackPoint } from '@/lib/types';

export type ChartPoint = { x: number; y: number };

function cumulativeKm(points: TrackPoint[]): number[] {
  const out = [0];
  for (let i = 1; i < points.length; i++) {
    out.push(out[i - 1] + haversineMeters(points[i - 1], points[i]) / 1000);
  }
  return out;
}

function resample(series: ChartPoint[], max = 140): ChartPoint[] {
  if (series.length <= max) return series;
  const step = series.length / max;
  const out: ChartPoint[] = [];
  for (let i = 0; i < max; i++) out.push(series[Math.floor(i * step)]);
  out.push(series[series.length - 1]);
  return out;
}

function profile(points: TrackPoint[], pick: (p: TrackPoint) => number | null): ChartPoint[] {
  const km = cumulativeKm(points);
  const series: ChartPoint[] = [];
  for (let i = 0; i < points.length; i++) {
    const y = pick(points[i]);
    if (y != null) series.push({ x: km[i], y });
  }
  return resample(series);
}

export function speedProfile(points: TrackPoint[]): ChartPoint[] {
  return profile(points, (p) => (p.speedKmh != null ? Math.max(0, p.speedKmh) : null));
}

export function elevationProfile(points: TrackPoint[]): ChartPoint[] {
  return profile(points, (p) => p.altitude);
}

export function hrProfile(points: TrackPoint[]): ChartPoint[] {
  return profile(points, (p) => p.hr);
}
