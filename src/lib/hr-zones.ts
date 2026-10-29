import { heartRateZone, ZONE_LABELS } from '@/lib/calories';

export type ZoneNumber = 1 | 2 | 3 | 4 | 5;

const ZONE_FLOORS = [0, 0.6, 0.7, 0.8, 0.9] as const;

export type ZoneSlice = {
  zone: ZoneNumber;
  label: string;
  minBpm: number;
  maxBpm: number | null;
  seconds: number;
  ratio: number;
};

export type ZoneDistribution = {
  slices: ZoneSlice[];
  totalSec: number;
};

const MAX_GAP_MS = 30_000;

export function zoneBounds(maxHr: number): { zone: ZoneNumber; minBpm: number; maxBpm: number | null }[] {
  return ZONE_FLOORS.map((floor, i) => {
    const next = ZONE_FLOORS[i + 1];
    return {
      zone: (i + 1) as ZoneNumber,
      minBpm: Math.round(floor * maxHr),
      maxBpm: next == null ? null : Math.round(next * maxHr) - 1,
    };
  });
}

export function zoneDistribution(
  samples: readonly { ts: number; hr: number | null }[],
  maxHr: number,
): ZoneDistribution | null {
  if (maxHr <= 0) return null;

  const seconds = [0, 0, 0, 0, 0];
  let totalSec = 0;

  let previous: { ts: number; hr: number } | null = null;
  for (const sample of samples) {
    const hr = sample.hr;
    if (hr == null || hr <= 0) {
      previous = null;
      continue;
    }
    if (previous != null) {
      const gapMs = sample.ts - previous.ts;
      if (gapMs > 0) {
        const countedSec = Math.min(gapMs, MAX_GAP_MS) / 1000;
        seconds[heartRateZone(previous.hr, maxHr) - 1] += countedSec;
        totalSec += countedSec;
      }
    }
    previous = { ts: sample.ts, hr };
  }

  if (totalSec <= 0) return null;

  const bounds = zoneBounds(maxHr);
  return {
    totalSec,
    slices: bounds.map((b, i) => ({
      zone: b.zone,
      label: ZONE_LABELS[b.zone],
      minBpm: b.minBpm,
      maxBpm: b.maxBpm,
      seconds: seconds[i],
      ratio: seconds[i] / totalSec,
    })),
  };
}

export function dominantZone(distribution: ZoneDistribution): ZoneSlice {
  return distribution.slices.reduce((best, s) => (s.seconds > best.seconds ? s : best));
}
