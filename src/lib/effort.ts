// Indicateur d'effort d'une séance, façon Strava (« Facile » → « Maximal »).
// S'appuie sur la zone cardio moyenne si une FC est disponible, sinon retombe
// sur la durée. Estimation indicative, pas une mesure physiologique.
import { heartRateZone } from '@/lib/calories';
import type { Session } from '@/lib/types';

export type EffortLevel = 1 | 2 | 3 | 4 | 5;

export type Effort = {
  level: EffortLevel;
  label: string;
  /** Clé de couleur du thème (data viz : du vert au rose cardio). */
  colorKey: 'success' | 'warning' | 'heart';
};

const LABELS: Record<EffortLevel, string> = {
  1: 'Facile',
  2: 'Modéré',
  3: 'Soutenu',
  4: 'Difficile',
  5: 'Maximal',
};

// Vert pour le facile, ambre pour le soutenu, rose cardio pour le maximal.
const COLORS: Record<EffortLevel, Effort['colorKey']> = {
  1: 'success',
  2: 'success',
  3: 'warning',
  4: 'warning',
  5: 'heart',
};

export function sessionEffort(session: Session, maxHr: number): Effort {
  let level: EffortLevel;
  if (session.avgHr != null && maxHr > 0) {
    level = heartRateZone(session.avgHr, maxHr) as EffortLevel;
  } else {
    // Hors FC, l'effort suit la durée : on prend le temps en mouvement quand il
    // est connu (vélo) pour qu'un long arrêt ne le surévalue pas.
    const minutes = (session.movingTimeSec ?? session.durationSec) / 60;
    level = minutes < 30 ? 1 : minutes < 60 ? 2 : minutes < 90 ? 3 : minutes < 150 ? 4 : 5;
  }
  return { level, label: LABELS[level], colorKey: COLORS[level] };
}
