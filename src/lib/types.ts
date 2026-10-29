export type ActivityType = 'velo' | 'muscu' | 'course' | 'marche';

export type Session = {
  id: number;
  type: ActivityType;
  startedAt: number;
  endedAt: number | null;
  durationSec: number;
  movingTimeSec: number | null;
  notes: string | null;
  avgHr: number | null;
  maxHr: number | null;
  distanceM: number | null;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  elevationGainM: number | null;
  avgCadence: number | null;
  maxCadence: number | null;
  calories: number | null;
  source: string | null;
  externalId: string | null;
  setCount?: number;
  exerciseCount?: number;
};

export type TrackPoint = {
  id: number;
  sessionId: number;
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  speedKmh: number | null;
  hr: number | null;
  cadence: number | null;
};

export type HrSample = { ts: number; hr: number };

export type BodyMeasurement = {
  id: number;
  measuredAt: number;
  weightKg: number;
};

export type Difficulty = 'facile' | 'moyen' | 'dur';

export type MuscuSet = {
  id: number;
  sessionId: number;
  exercise: string;
  setIndex: number;
  reps: number;
  weightKg: number;
  difficulty: Difficulty | null;
};

export type TrainingGoal = 'force' | 'hypertrophie' | 'endurance' | 'tonification' | 'perte-poids';

export type Sex = 'h' | 'f' | null;

export type Profile = {
  weightKg: number;
  heightCm: number;
  maxHr: number;
  goal: TrainingGoal;
  sex: Sex;
};

export type PeriodStats = {
  sessionCount: number;
  totalDurationSec: number;
  totalDistanceM: number;
  totalCalories: number;
};
