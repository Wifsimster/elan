// Construction des séries de graphes (profils vitesse / altitude / FC) à partir
// des points GPS d'une sortie. Tout est calculé localement.
import { haversineMeters } from '@/lib/geo';
import type { TrackPoint } from '@/lib/types';

/** Un point de graphe (abscisse → valeur). Compatible avec `<LineChart>`. */
export type ChartPoint = { x: number; y: number };

/** Distance cumulée (en km) à chaque point du tracé. */
function cumulativeKm(points: TrackPoint[]): number[] {
  const out = [0];
  for (let i = 1; i < points.length; i++) {
    out.push(out[i - 1] + haversineMeters(points[i - 1], points[i]) / 1000);
  }
  return out;
}

/** Sous-échantillonne une série pour rester fluide sur les longues sorties. */
function resample(series: ChartPoint[], max = 140): ChartPoint[] {
  if (series.length <= max) return series;
  const step = series.length / max;
  const out: ChartPoint[] = [];
  for (let i = 0; i < max; i++) out.push(series[Math.floor(i * step)]);
  out.push(series[series.length - 1]);
  return out;
}

/** Construit une série {distance(km) → valeur} en ignorant les points sans donnée. */
function profile(points: TrackPoint[], pick: (p: TrackPoint) => number | null): ChartPoint[] {
  const km = cumulativeKm(points);
  const series: ChartPoint[] = [];
  for (let i = 0; i < points.length; i++) {
    const y = pick(points[i]);
    if (y != null) series.push({ x: km[i], y });
  }
  return resample(series);
}

/** Profil de vitesse (km/h) sur la distance. */
export function speedProfile(points: TrackPoint[]): ChartPoint[] {
  return profile(points, (p) => (p.speedKmh != null ? Math.max(0, p.speedKmh) : null));
}

/** Profil d'altitude (m) sur la distance. */
export function elevationProfile(points: TrackPoint[]): ChartPoint[] {
  return profile(points, (p) => p.altitude);
}

/** Profil de fréquence cardiaque (bpm) sur la distance. */
export function hrProfile(points: TrackPoint[]): ChartPoint[] {
  return profile(points, (p) => p.hr);
}
