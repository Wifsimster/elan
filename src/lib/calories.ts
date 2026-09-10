// Estimation des calories : modèle MET interpolé pour le vélo et la musculation,
// avec mélange optionnel d'une estimation cardio (style Keytel, ajustée sans âge).
// kcal_MET = MET * poids(kg) * durée(h). Suffisant pour un suivi personnel.
import type { ActivityType } from '@/lib/types';

/** FCmax par défaut quand le profil ne la fournit pas (cohérent avec le profil par défaut). */
const DEFAULT_MAX_HR = 190;

/** Points de référence du Compendium (km/h → MET) pour le vélo, loisir à vigoureux. */
const VELO_MET_TABLE: readonly (readonly [number, number])[] = [
  [10, 3.5],    // très tranquille / promenade
  [16, 4.0],    // loisir
  [19, 6.8],    // allure soutenue
  [22.5, 8.0],  // entraînement
  [25.5, 10.0], // vigoureux
  [30.5, 12.0], // course / rapide
  [33, 15.8],   // > 32 km/h, effort maximal
];

/** Points de référence du Compendium (km/h → MET) pour la course à pied. */
const COURSE_MET_TABLE: readonly (readonly [number, number])[] = [
  [6.4, 6.0],   // footing très lent
  [8.0, 8.3],
  [9.7, 9.8],
  [11.3, 11.0],
  [12.9, 11.8],
  [14.5, 12.8],
  [16.1, 14.5],
  [17.7, 16.0], // allure de compétition
];

/** Points de référence du Compendium (km/h → MET) pour la marche, plat à soutenu. */
const MARCHE_MET_TABLE: readonly (readonly [number, number])[] = [
  [3.2, 2.8],  // flânerie
  [4.0, 3.0],
  [4.8, 3.5],  // allure courante
  [5.6, 4.3],
  [6.4, 5.0],  // marche rapide
  [7.2, 7.0],
  [8.0, 8.3],  // marche athlétique
];

/** Interpolation linéaire entre les points de référence (extrapolation plate aux bords). */
function metFromTable(
  table: readonly (readonly [number, number])[],
  avgSpeedKmh: number,
): number {
  const first = table[0];
  const last = table[table.length - 1];
  if (avgSpeedKmh <= first[0]) return first[1];
  if (avgSpeedKmh >= last[0]) return last[1];
  for (let i = 1; i < table.length; i++) {
    const [x1, y1] = table[i];
    if (avgSpeedKmh <= x1) {
      const [x0, y0] = table[i - 1];
      const t = (avgSpeedKmh - x0) / (x1 - x0);
      return y0 + t * (y1 - y0);
    }
  }
  return last[1];
}

/** MET musculation : valeur Compendium « moderate resistance training » (5 est trop pour du circuit). */
const MUSCU_MET = 4.5;

/**
 * Table MET et vitesse de repli par activité tracée au GPS. La vitesse de repli
 * ne sert que si l'appelant n'en fournit pas (séance sans tracé exploitable) :
 * elle vaut une allure ordinaire pour l'activité.
 */
const SPEED_MET: Record<'velo' | 'course' | 'marche', {
  table: readonly (readonly [number, number])[];
  fallbackKmh: number;
}> = {
  velo: { table: VELO_MET_TABLE, fallbackKmh: 18 },
  course: { table: COURSE_MET_TABLE, fallbackKmh: 10 },
  marche: { table: MARCHE_MET_TABLE, fallbackKmh: 5 },
};

/**
 * Énergie cardio estimée (kcal/min) inspirée de Keytel et al., variante sans âge :
 * on n'a que le poids et la FCmax — on utilise le %FCmax comme proxy d'intensité.
 * Donne ~5 à ~15 kcal/min pour 70 kg entre 70 % et 95 % de FCmax.
 */
function hrKcalPerMin(weightKg: number, avgHr: number, maxHr: number): number {
  const pct = Math.max(0.4, Math.min(1.05, avgHr / maxHr));
  return 0.082 * weightKg * Math.pow(pct, 1.7);
}

/**
 * Bonus d'énergie lié au dénivelé positif (~0,77 kcal/m pour 70 kg, indép. de la
 * vitesse). Le travail vertical (m·g·h à ~23 % de rendement) est du même ordre à
 * pied qu'à vélo : la même constante sert aux deux.
 */
function elevationBonusKcal(weightKg: number, elevationGainM: number): number {
  if (elevationGainM <= 0) return 0;
  return weightKg * 0.011 * elevationGainM;
}

export function estimateCalories(params: {
  type: ActivityType;
  weightKg: number;
  durationSec: number;
  avgSpeedKmh?: number | null;
  elevationGainM?: number | null;
  avgHr?: number | null;
  maxHr?: number | null;
}): number {
  const hours = params.durationSec / 3600;
  if (hours <= 0 || params.weightKg <= 0) return 0;

  const speedMet = params.type === 'muscu' ? null : SPEED_MET[params.type];
  const met = speedMet
    ? metFromTable(speedMet.table, params.avgSpeedKmh ?? speedMet.fallbackKmh)
    : MUSCU_MET;
  const metKcal = met * params.weightKg * hours;

  // Mélange cardio (Keytel sans âge) si FC moyenne disponible : 60 % cardio, 40 % MET.
  const avgHr = params.avgHr ?? null;
  const maxHr = params.maxHr ?? DEFAULT_MAX_HR;
  let baseKcal = metKcal;
  if (avgHr != null && avgHr > 0 && maxHr > 0) {
    const hrKcal = hrKcalPerMin(params.weightKg, avgHr, maxHr) * (params.durationSec / 60);
    baseKcal = 0.4 * metKcal + 0.6 * hrKcal;
  }

  // Bonus dénivelé : toute activité qui déplace le corps en montée (vélo, course,
  // marche). Ignoré en musculation, qui n'a pas de dénivelé.
  const elevBonus =
    params.type === 'muscu'
      ? 0
      : elevationBonusKcal(params.weightKg, params.elevationGainM ?? 0);

  return baseKcal + elevBonus;
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
