import type { Difficulty } from './types';

export type ProgressionAdvice = 'augmente' | 'maintiens' | 'reduis';

export function difficultyScore(d: Difficulty | null | undefined): number | null {
  if (d === 'facile') return 1;
  if (d === 'moyen') return 2;
  if (d === 'dur') return 3;
  return null;
}

export function difficultyLabel(d: Difficulty): string {
  if (d === 'facile') return 'Facile';
  if (d === 'moyen') return 'Moyen';
  return 'Dur';
}

export function suggestProgression(
  recent: (Difficulty | null | undefined)[],
  window = 3,
): ProgressionAdvice {
  const scored = recent
    .map(difficultyScore)
    .filter((s): s is number => s !== null);
  if (scored.length === 0) return 'maintiens';

  const lastN = scored.slice(-window);
  const last = lastN[lastN.length - 1];
  const avg = lastN.reduce((a, b) => a + b, 0) / lastN.length;

  if (last === 3) return 'reduis';
  if (last === 1 && !lastN.includes(3)) return 'augmente';
  if (avg <= 1.5) return 'augmente';
  if (avg >= 2.5) return 'reduis';
  return 'maintiens';
}

export function adviceLabel(a: ProgressionAdvice): string {
  if (a === 'augmente') return 'Augmente les reps ou la charge';
  if (a === 'reduis') return 'Réduis la charge, soigne la technique';
  return 'Maintiens, charge bien calibrée';
}
