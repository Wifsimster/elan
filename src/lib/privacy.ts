import { getSetting, setSetting } from '@/lib/db';
import { haversineMeters } from '@/lib/geo';

const KEY = 'privacy_zone_m';

export const PRIVACY_ZONE_OPTIONS = [0, 100, 200, 500] as const;

export async function getPrivacyZoneM(): Promise<number> {
  const raw = await getSetting(KEY);
  if (raw == null) return 0;
  const v = Number(raw);
  return Number.isFinite(v) && v > 0 ? v : 0;
}

export async function setPrivacyZoneM(meters: number): Promise<void> {
  await setSetting(KEY, String(Math.max(0, Math.round(meters))));
}

export function trimPrivacyZone<T extends { lat: number; lon: number }>(
  points: T[],
  radiusM: number,
): T[] {
  if (radiusM <= 0 || points.length < 2) return points;
  const first = points[0];
  const last = points[points.length - 1];

  let start = 0;
  while (start < points.length && haversineMeters(first, points[start]) < radiusM) start++;
  let end = points.length - 1;
  while (end > start && haversineMeters(last, points[end]) < radiusM) end--;

  if (end - start < 1) return points;
  return points.slice(start, end + 1);
}
