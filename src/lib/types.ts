// Types du domaine — suivi d'activité physique personnel et local.

export type ActivityType = 'velo' | 'muscu';

/** Une séance d'entraînement enregistrée. */
export type Session = {
  id: number;
  type: ActivityType;
  /** Début en ms epoch. */
  startedAt: number;
  /** Fin en ms epoch (null si en cours). */
  endedAt: number | null;
  /** Durée active en secondes (hors pauses). */
  durationSec: number;
  notes: string | null;
  // Cardio (commun aux deux types, si ceinture connectée)
  avgHr: number | null;
  maxHr: number | null;
  // Spécifique vélo
  distanceM: number | null;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  elevationGainM: number | null;
  // Cadence (vélo, si capteur de cadence connecté), en tours/min
  avgCadence: number | null;
  maxCadence: number | null;
  calories: number | null;
};

/** Un point GPS d'un tracé vélo. */
export type TrackPoint = {
  id: number;
  sessionId: number;
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  speedKmh: number | null;
  hr: number | null;
  /** Cadence en tours/min au point le plus proche (si capteur connecté). */
  cadence: number | null;
};

/** Une série de musculation (un exercice peut avoir plusieurs séries). */
export type MuscuSet = {
  id: number;
  sessionId: number;
  exercise: string;
  setIndex: number;
  reps: number;
  weightKg: number;
};

/** Profil utilisateur, pour l'estimation des calories. */
export type Profile = {
  weightKg: number;
  /** FC max théorique, pour les zones cardio. */
  maxHr: number;
};

/** Statistiques agrégées sur une période. */
export type PeriodStats = {
  sessionCount: number;
  totalDurationSec: number;
  totalDistanceM: number;
  totalCalories: number;
};
