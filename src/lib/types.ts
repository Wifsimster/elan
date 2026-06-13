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
  /** Durée active en secondes (hors pauses) — temps total écran allumé/chrono. */
  durationSec: number;
  /**
   * Temps « en mouvement » en secondes (vélo) : durée hors arrêts, déduite des
   * points GPS façon Strava. `null` quand il n'a pas pu être calculé (pas de
   * tracé, musculation) — l'affichage retombe alors sur `durationSec`.
   */
  movingTimeSec: number | null;
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
  /** Provenance : null = enregistré dans l'app, 'strava' = importé d'un fichier. */
  source: string | null;
  /** Clé de déduplication des séances importées (null pour les séances natives). */
  externalId: string | null;
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

/**
 * Un échantillon de fréquence cardiaque horodaté (ms epoch), accumulé pendant
 * une séance puis agrégé (cf. `lib/samples.ts`) et apparié aux points GPS.
 */
export type HrSample = { ts: number; hr: number };

/** Une pesée du journal de poids corporel. */
export type BodyMeasurement = {
  id: number;
  /** Date de la mesure en ms epoch. */
  measuredAt: number;
  weightKg: number;
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

/**
 * Objectif d'entraînement musculation — pilote les fourchettes de répétitions,
 * le repos et l'intensité (charge) conseillés par le moteur de recommandation.
 */
export type TrainingGoal = 'force' | 'hypertrophie' | 'endurance' | 'tonification' | 'perte-poids';

/** Sexe biologique — affine la recommandation de charge. `null` = non précisé. */
export type Sex = 'h' | 'f' | null;

/** Profil utilisateur : calories, zones cardio et charges/reps conseillées. */
export type Profile = {
  weightKg: number;
  /** Taille en cm — entre dans la personnalisation des charges conseillées. */
  heightCm: number;
  /** FC max théorique, pour les zones cardio. */
  maxHr: number;
  /** Ce que l'utilisateur cherche à faire : oriente reps et charge conseillées. */
  goal: TrainingGoal;
  /** Sexe (optionnel) : ajuste la charge conseillée (haut du corps surtout). */
  sex: Sex;
};

/** Statistiques agrégées sur une période. */
export type PeriodStats = {
  sessionCount: number;
  totalDurationSec: number;
  totalDistanceM: number;
  totalCalories: number;
};
