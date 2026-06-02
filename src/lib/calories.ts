// Estimation des calories par la méthode des MET (équivalents métaboliques).
// kcal = MET * poids(kg) * durée(h). Estimation grossière, suffisante pour le suivi.
import type { ActivityType } from '@/lib/types';

/** MET vélo selon la vitesse moyenne (km/h). */
function veloMet(avgSpeedKmh: number): number {
  if (avgSpeedKmh < 16) return 4;
  if (avgSpeedKmh < 19) return 6;
  if (avgSpeedKmh < 22) return 8;
  if (avgSpeedKmh < 25) return 10;
  if (avgSpeedKmh < 30) return 12;
  return 15.8;
}

const MUSCU_MET = 5; // musculation vigoureuse

export function estimateCalories(params: {
  type: ActivityType;
  weightKg: number;
  durationSec: number;
  avgSpeedKmh?: number | null;
}): number {
  const hours = params.durationSec / 3600;
  const met =
    params.type === 'velo' ? veloMet(params.avgSpeedKmh ?? 18) : MUSCU_MET;
  return met * params.weightKg * hours;
}

/** Zone cardio (1 à 5) à partir de la FC et de la FC max. */
export function heartRateZone(hr: number, maxHr: number): number {
  const pct = hr / maxHr;
  if (pct < 0.6) return 1;
  if (pct < 0.7) return 2;
  if (pct < 0.8) return 3;
  if (pct < 0.9) return 4;
  return 5;
}

export const ZONE_LABELS: Record<number, string> = {
  1: 'Récupération',
  2: 'Endurance',
  3: 'Aérobie',
  4: 'Seuil',
  5: 'Maximal',
};
