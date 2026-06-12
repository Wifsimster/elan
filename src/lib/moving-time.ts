// Temps « en mouvement » d'une sortie vélo — façon Strava : on ne compte que les
// intervalles où le cycliste avance réellement, en écartant les arrêts (feu
// rouge, pause café… ou un chrono qu'on a oublié de stopper). Pur (sans React),
// alimenté par les points consolidés du tracker ou les points d'un import.
//
// Détection de l'arrêt, par segment entre deux points successifs :
//   - si le point porte une vitesse (Doppler GNSS, fiable à l'arrêt), on s'y fie ;
//   - sinon, on retombe sur la vitesse implicite du segment (distance / temps),
//     en exigeant une distance minimale pour ne pas confondre la dérive de la
//     position à l'arrêt (dans le rayon de précision) avec un vrai déplacement.
// Un intervalle anormalement long (perte de signal, app suspendue) est plafonné
// pour ne pas gonfler le temps sur un trou de tracé.

import { haversineMeters } from '@/lib/geo';

/** Point minimal exploitable pour le calcul (compatible TrackPoint / ConsolidatedPoint). */
export type TimedPoint = {
  ts: number;
  lat: number;
  lon: number;
  speedKmh?: number | null;
};

/** En-dessous de cette vitesse on se considère à l'arrêt (~2,9 km/h),
 *  cohérent avec le seuil de standstill du filtre GPS (lib/gps-filter). */
const MOVING_THRESHOLD_MS = 0.8;
/** Distance minimale d'un segment pour le juger « en mouvement » faute de
 *  vitesse Doppler : couvre la dérive de la position à l'arrêt (précision GPS). */
const MIN_SEGMENT_M = 4;
/** Au-delà de cet écart entre deux points, l'intervalle est ambigu (perte de
 *  signal) : on n'en crédite que ce plafond pour ne pas gonfler le temps. */
const MAX_SEGMENT_SEC = 30;

/**
 * Temps en mouvement (secondes, arrondi) déduit d'une suite de points
 * chronologiques. Renvoie 0 s'il y a moins de deux points : l'appelant retombe
 * alors sur la durée totale.
 */
export function movingTimeSec(points: TimedPoint[]): number {
  if (points.length < 2) return 0;
  let sec = 0;
  for (let i = 1; i < points.length; i++) {
    const prev = points[i - 1];
    const cur = points[i];
    const dt = (cur.ts - prev.ts) / 1000;
    if (dt <= 0) continue;

    const dopplerMs = cur.speedKmh != null ? cur.speedKmh / 3.6 : null;
    let moving: boolean;
    if (dopplerMs != null) {
      moving = dopplerMs >= MOVING_THRESHOLD_MS;
    } else {
      const d = haversineMeters(prev, cur);
      moving = d >= MIN_SEGMENT_M && d / dt >= MOVING_THRESHOLD_MS;
    }
    if (moving) sec += Math.min(dt, MAX_SEGMENT_SEC);
  }
  return Math.round(sec);
}
