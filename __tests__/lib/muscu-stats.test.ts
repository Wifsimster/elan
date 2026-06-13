// Tests des agrégats de séance muscu (lib/muscu-stats.ts) : comptage séries,
// séries effectuées, volume soulevé et résumé textuel. Pur.
import { muscuStats, muscuSummary, type StatsExercise } from '@/lib/muscu-stats';

describe('muscuStats', () => {
  it('séance vide : tout à zéro', () => {
    expect(muscuStats([])).toEqual({
      exerciseCount: 0,
      totalSets: 0,
      doneSets: 0,
      totalVolume: 0,
    });
  });

  it('compte exercices, séries, séries effectuées et volume', () => {
    const exercises: StatsExercise[] = [
      {
        sets: [
          { reps: 10, weightKg: 50, done: true },
          { reps: 8, weightKg: 50, done: true },
          { reps: 8, weightKg: 50 },
        ],
      },
      {
        sets: [{ reps: 12, weightKg: 20, done: false }],
      },
    ];
    // volume = 10*50 + 8*50 + 8*50 + 12*20 = 500+400+400+240 = 1540
    expect(muscuStats(exercises)).toEqual({
      exerciseCount: 2,
      totalSets: 4,
      doneSets: 2,
      totalVolume: 1540,
    });
  });

  it('gère les charges décimales', () => {
    const exercises: StatsExercise[] = [{ sets: [{ reps: 10, weightKg: 22.5 }] }];
    expect(muscuStats(exercises).totalVolume).toBeCloseTo(225);
  });
});

describe('muscuSummary', () => {
  it('formate un résumé lisible avec volume arrondi', () => {
    const stats = { exerciseCount: 5, totalSets: 18, doneSets: 18, totalVolume: 2340.6 };
    expect(muscuSummary(stats)).toBe('5 exercices · 18 séries · 2341 kg soulevés');
  });
});
