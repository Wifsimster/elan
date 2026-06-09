// Consolidation des points GPS d'une sortie (approche Strava/OpenTracks).
//
// Pipeline appliqué à chaque position reçue :
//   1. porte de précision — on écarte les fixes inutilisables (OpenTracks
//      rejette au-delà de 50 m ; les fixes médiocres restants sont de toute
//      façon pondérés à la baisse par le Kalman) ;
//   2. rejet des téléportations — vitesse implicite impossible à vélo ;
//   3. filtre de Kalman 1D sur lat/lon (le classique « KalmanLatLong ») :
//      gain K = variance / (variance + précision²), la précision rapportée
//      par la puce GNSS sert de bruit de mesure ;
//   4. distance par point d'ancrage (modèle OpenTracks) : on ne crédite la
//      distance que lorsqu'on s'est éloigné d'au moins quelques mètres de
//      l'ancre, ce qui élimine à la fois la dérive à l'arrêt et le
//      sous-comptage des micro-segments écartés par un seuil naïf ;
//   5. dénivelé : altitude lissée (médiane glissante) + hystérésis symétrique
//      (machine à états GoldenCheetah) — l'altitude GPS est bruitée de ±10 m,
//      un seuil naïf gonfle ou écrase le cumul selon le sens du bruit.
//
// Le module est sans dépendance React : il est alimenté fix par fix par
// use-gps-tracker et reste testable isolément.

import { haversineMeters } from '@/lib/geo';

/** Position brute telle que fournie par expo-location. */
export type GpsFix = {
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  /** Précision horizontale en mètres (null si inconnue). */
  accuracy: number | null;
  /** Précision verticale en mètres (null si inconnue). */
  altitudeAccuracy: number | null;
  /** Vitesse Doppler en m/s rapportée par la puce (null si inconnue). */
  speed: number | null;
};

/** Point consolidé prêt à être tracé/enregistré. */
export type ConsolidatedPoint = {
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  speedKmh: number | null;
};

export type ConsolidationResult = {
  /** Point lissé à conserver, ou null si le fix a été rejeté. */
  point: ConsolidatedPoint | null;
  /** Distance (m) à ajouter au cumul pour ce fix (0 si rejet ou arrêt). */
  deltaDistanceM: number;
  /** Dénivelé positif (m) confirmé par l'hystérésis pour ce fix. */
  deltaElevationGainM: number;
};

/** Fixes plus imprécis que ce rayon : inutilisables (défaut OpenTracks : 50 m). */
const MAX_ACCURACY_M = 50;
/** Vitesse implicite au-delà de laquelle un saut est une aberration (30 m/s = 108 km/h). */
const MAX_PLAUSIBLE_SPEED_MS = 30;
/** En-dessous de cette vitesse Doppler, on se considère à l'arrêt (~2,5 km/h). */
const STANDSTILL_SPEED_MS = 0.7;
/** Avance minimale de l'ancre de distance (OpenTracks : 5-10 m). */
const DISTANCE_ANCHOR_M = 5;
/**
 * Bruit de process minimal du Kalman en m/s. Le filtre s'adapte à la vitesse
 * rapportée (Q ≈ vitesse courante) ; ce plancher garde un lissage fort à
 * l'arrêt, là où la dérive est la plus visible.
 */
const KALMAN_MIN_PROCESS_NOISE_MS = 3;
/** Fenêtre de la médiane glissante appliquée à l'altitude (échantillons ~1 Hz). */
const ALTITUDE_WINDOW = 7;
/**
 * Hystérésis du dénivelé (machine à états GoldenCheetah). Strava exige ~10 m
 * de montée soutenue pour de l'altitude GPS pure ; la médiane glissante et la
 * porte de précision verticale en amont permettent de descendre à 5 m sans
 * compter le bruit.
 */
const ELEVATION_HYSTERESIS_M = 5;
/** Précision verticale au-delà de laquelle l'altitude du fix est ignorée. */
const MAX_ALTITUDE_ACCURACY_M = 12;
/** Précision horizontale supposée quand la puce n'en fournit pas. */
const DEFAULT_ACCURACY_M = 15;

export class GpsConsolidator {
  // État du filtre de Kalman (position estimée + variance en m²).
  private estLat = 0;
  private estLon = 0;
  private variance = -1; // < 0 tant qu'aucun fix accepté

  private lastTs = 0;
  private lastAccepted: ConsolidatedPoint | null = null;
  // Ancre de distance : dernier point depuis lequel la distance a été créditée.
  private distanceAnchor: ConsolidatedPoint | null = null;

  // Lissage altimétrique : fenêtre brute + ancre de l'hystérésis.
  private altWindow: number[] = [];
  private elevationAnchor: number | null = null;

  /**
   * Intègre un fix brut et retourne le point consolidé (ou null si rejeté)
   * avec les incréments de distance et de dénivelé à appliquer.
   */
  process(fix: GpsFix): ConsolidationResult {
    const rejected: ConsolidationResult = {
      point: null,
      deltaDistanceM: 0,
      deltaElevationGainM: 0,
    };

    // 1. Porte de précision.
    const accuracy = Math.max(fix.accuracy ?? DEFAULT_ACCURACY_M, 1);
    if (accuracy > MAX_ACCURACY_M) return rejected;

    // 2. Rejet des téléportations. La vitesse implicite décroît avec le temps
    //    écoulé : après une vraie coupure GPS, le point suivant redevient
    //    plausible de lui-même (pas de blocage).
    const prev = this.lastAccepted;
    if (prev) {
      const dt = (fix.ts - prev.ts) / 1000;
      if (dt <= 0) return rejected;
      if (haversineMeters(prev, fix) / dt > MAX_PLAUSIBLE_SPEED_MS) return rejected;
    }

    // 3. Filtre de Kalman 1D (modèle position constante). La variance croît
    //    avec le temps écoulé, plus vite si la puce indique qu'on roule vite :
    //    le filtre suit le mouvement réel mais écrase la dérive à l'arrêt.
    if (this.variance < 0) {
      this.estLat = fix.lat;
      this.estLon = fix.lon;
      this.variance = accuracy * accuracy;
    } else {
      const dt = Math.max((fix.ts - this.lastTs) / 1000, 0.001);
      const q = Math.max(KALMAN_MIN_PROCESS_NOISE_MS, fix.speed ?? 0);
      this.variance += dt * q * q;
      const gain = this.variance / (this.variance + accuracy * accuracy);
      this.estLat += gain * (fix.lat - this.estLat);
      this.estLon += gain * (fix.lon - this.estLon);
      this.variance *= 1 - gain;
    }
    this.lastTs = fix.ts;

    // Altitude lissée par médiane glissante (robuste aux pics isolés), en
    // ignorant les fixes à la précision verticale médiocre.
    let smoothedAlt: number | null = null;
    if (
      fix.altitude != null &&
      (fix.altitudeAccuracy == null || fix.altitudeAccuracy <= MAX_ALTITUDE_ACCURACY_M)
    ) {
      this.altWindow.push(fix.altitude);
      if (this.altWindow.length > ALTITUDE_WINDOW) this.altWindow.shift();
      smoothedAlt = median(this.altWindow);
    }

    const point: ConsolidatedPoint = {
      ts: fix.ts,
      lat: this.estLat,
      lon: this.estLon,
      altitude: smoothedAlt,
      speedKmh: fix.speed != null && fix.speed >= 0 ? fix.speed * 3.6 : null,
    };

    // 4. Distance par ancre : créditée seulement après s'être éloigné d'au
    //    moins DISTANCE_ANCHOR_M, et jamais quand le Doppler dit qu'on est à
    //    l'arrêt (la position dérive dans le rayon de précision).
    let deltaDistanceM = 0;
    if (this.distanceAnchor == null) {
      this.distanceAnchor = point;
    } else {
      const moving = fix.speed == null || fix.speed >= STANDSTILL_SPEED_MS;
      const d = haversineMeters(this.distanceAnchor, point);
      if (moving && d >= DISTANCE_ANCHOR_M) {
        deltaDistanceM = d;
        this.distanceAnchor = point;
      }
    }

    // 5. Dénivelé : hystérésis symétrique (GoldenCheetah). Une montée n'est
    //    créditée — en totalité — qu'au-delà du seuil ; une descente ne
    //    déplace l'ancre qu'au-delà du même seuil, pour que l'oscillation du
    //    bruit vertical ne fabrique pas de faux dénivelé.
    let deltaElevationGainM = 0;
    if (smoothedAlt != null) {
      if (this.elevationAnchor == null) {
        this.elevationAnchor = smoothedAlt;
      } else if (smoothedAlt > this.elevationAnchor + ELEVATION_HYSTERESIS_M) {
        deltaElevationGainM = smoothedAlt - this.elevationAnchor;
        this.elevationAnchor = smoothedAlt;
      } else if (smoothedAlt < this.elevationAnchor - ELEVATION_HYSTERESIS_M) {
        this.elevationAnchor = smoothedAlt;
      }
    }

    this.lastAccepted = point;
    return { point, deltaDistanceM, deltaElevationGainM };
  }
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}
