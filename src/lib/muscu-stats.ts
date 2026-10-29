export type StatsExercise = {
  sets: { reps: number; weightKg: number; done?: boolean }[];
};

export type MuscuStats = {
  exerciseCount: number;
  totalSets: number;
  doneSets: number;
  totalVolume: number;
};

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

export function muscuSummary(stats: MuscuStats): string {
  return `${stats.exerciseCount} exercices · ${stats.totalSets} séries · ${Math.round(
    stats.totalVolume,
  )} kg soulevés`;
}
