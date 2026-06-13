// Agrégats d'une séance de musculation en cours : nombre de séries, séries
// effectuées et volume soulevé. Pur (aucune dépendance React/Expo) — extrait de
// muscu.tsx pour être testable et réutilisable ; testé dans
// __tests__/lib/muscu-stats.test.ts.

/** Forme minimale d'un exercice consommée par les agrégats (une liste de séries). */
export type StatsExercise = {
  sets: { reps: number; weightKg: number; done?: boolean }[];
};

export type MuscuStats = {
  /** Nombre d'exercices de la séance. */
  exerciseCount: number;
  /** Nombre total de séries (toutes confondues). */
  totalSets: number;
  /** Séries cochées comme effectuées. */
  doneSets: number;
  /** Volume soulevé en kg : Σ reps × charge. */
  totalVolume: number;
};

/** Calcule les agrégats d'une séance muscu en un seul parcours. */
export function muscuStats(exercises: StatsExercise[]): MuscuStats {
  let totalSets = 0;
  let doneSets = 0;
  let totalVolume = 0;
  for (const e of exercises) {
    for (const s of e.sets) {
      totalSets++;
      if (s.done) doneSets++;
      totalVolume += s.reps * s.weightKg;
    }
  }
  return { exerciseCount: exercises.length, totalSets, doneSets, totalVolume };
}

/** Résumé textuel d'une séance, stocké dans les notes (« 5 exercices · 18 séries · 2 340 kg soulevés »). */
export function muscuSummary(stats: MuscuStats): string {
  return `${stats.exerciseCount} exercices · ${stats.totalSets} séries · ${Math.round(
    stats.totalVolume,
  )} kg soulevés`;
}
