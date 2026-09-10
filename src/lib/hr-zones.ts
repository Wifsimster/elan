// Répartition du temps passé dans chaque zone cardiaque sur une séance.
//
// Pur : aucune dépendance React/Expo, testé dans __tests__/lib/hr-zones.test.ts.
// Les seuils de zones sont ceux de `lib/calories.ts` (heartRateZone) — on ne les
// redéfinit pas ici pour que l'effort de séance et les zones racontent la même
// histoire.
import { heartRateZone, ZONE_LABELS } from '@/lib/calories';

export type ZoneNumber = 1 | 2 | 3 | 4 | 5;

/** Bornes basses des zones, en fraction de FC max (cf. `heartRateZone`). */
const ZONE_FLOORS = [0, 0.6, 0.7, 0.8, 0.9] as const;

/** Une zone et le temps qu'on y a passé. */
export type ZoneSlice = {
  zone: ZoneNumber;
  label: string;
  /** Borne basse de la zone en bpm (0 pour la zone 1). */
  minBpm: number;
  /** Borne haute en bpm, `null` pour la zone 5 (pas de plafond). */
  maxBpm: number | null;
  seconds: number;
  /** Part du temps cardio total, entre 0 et 1. */
  ratio: number;
};

export type ZoneDistribution = {
  slices: ZoneSlice[];
  /** Temps total effectivement attribué à une zone (hors trous de mesure). */
  totalSec: number;
};

/**
 * Écart maximal entre deux mesures encore compté comme du temps d'effort. Au-delà,
 * on considère qu'il y a eu un trou (ceinture hors de portée, pause, arrêt) et on
 * ne l'attribue à aucune zone — sinon une coupure de dix minutes gonflerait la
 * zone où la ceinture s'est tue.
 */
const MAX_GAP_MS = 30_000;

/** Bornes en bpm des cinq zones pour une FC max donnée. */
export function zoneBounds(maxHr: number): { zone: ZoneNumber; minBpm: number; maxBpm: number | null }[] {
  return ZONE_FLOORS.map((floor, i) => {
    const next = ZONE_FLOORS[i + 1];
    return {
      zone: (i + 1) as ZoneNumber,
      minBpm: Math.round(floor * maxHr),
      // La borne haute affichée est exclusive côté calcul : on montre le dernier
      // bpm encore dans la zone pour ne pas afficher deux zones qui se chevauchent.
      maxBpm: next == null ? null : Math.round(next * maxHr) - 1,
    };
  });
}

/**
 * Temps passé dans chaque zone à partir d'échantillons cardiaques horodatés.
 *
 * Chaque intervalle entre deux mesures consécutives est attribué à la zone de la
 * mesure qui l'ouvre, et plafonné à `MAX_GAP_MS`. Renvoie `null` quand rien n'est
 * exploitable (pas de ceinture, FC max inconnue, une seule mesure) plutôt qu'une
 * répartition vide : l'appelant n'affiche alors simplement pas le bloc.
 */
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
      // Point sans FC (capteur absent sur ce tronçon) : il coupe l'intervalle au
      // lieu de le prolonger avec la dernière valeur connue.
      previous = null;
      continue;
    }
    if (previous != null) {
      const gapMs = sample.ts - previous.ts;
      // Un horodatage égal ou antérieur (doublon, série non triée) n'ouvre pas
      // d'intervalle : on ne compte jamais de temps négatif.
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

/**
 * Zone où l'on a passé le plus de temps — la ligne de verdict du bloc. En cas
 * d'égalité parfaite, la zone la plus basse gagne (l'effort le plus prudent).
 */
export function dominantZone(distribution: ZoneDistribution): ZoneSlice {
  return distribution.slices.reduce((best, s) => (s.seconds > best.seconds ? s : best));
}
