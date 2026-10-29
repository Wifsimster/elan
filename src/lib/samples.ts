import type { HrSample } from '@/lib/types';

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

export function summarizeHr(samples: HrSample[]): { avgHr: number | null; maxHr: number | null } {
  const { avg, max } = meanMax(samples.map((s) => s.hr));
  return { avgHr: avg, maxHr: max };
}

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

export function nearestSample<T extends { ts: number }>(
  samples: T[],
  ts: number,
  pick: (s: T) => number,
  toleranceMs = 10_000,
): number | null {
  if (samples.length === 0) return null;
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
