// Conseil de progression en musculation à partir du ressenti noté séance après
// séance (facile / moyen / dur). Fonction pure, 100 % local, sans dépendance :
// c'est le seul endroit où vit la logique « dois-je augmenter les reps/la charge ? ».

import type { Difficulty } from './types';

export type ProgressionAdvice = 'augmente' | 'maintiens' | 'reduis';

/** Score ordonnable d'un ressenti (`null`/non noté = ignoré dans les calculs). */
export function difficultyScore(d: Difficulty | null | undefined): number | null {
  if (d === 'facile') return 1;
  if (d === 'moyen') return 2;
  if (d === 'dur') return 3;
  return null;
}

/** Libellé FR d'un ressenti, pour l'affichage. */
export function difficultyLabel(d: Difficulty): string {
  if (d === 'facile') return 'Facile';
  if (d === 'moyen') return 'Moyen';
  return 'Dur';
}

/**
 * Conseille d'augmenter, maintenir ou réduire la charge/les reps d'un exercice à
 * partir des ressentis récents, donnés du plus ancien au plus récent.
 *
 * Heuristique (sur les `window` dernières séances notées, défaut 3) :
 *  - aucune note exploitable                              -> 'maintiens'
 *  - dernière notée = 'dur'                               -> 'reduis'   (priorité : on réagit
 *                                                            vite à une séance subitement trop dure)
 *  - dernière notée = 'facile' sans 'dur' dans la fenêtre -> 'augmente'
 *  - moyenne des scores <= 1.5 (proche de facile)         -> 'augmente'
 *  - moyenne des scores >= 2.5 (proche de dur)            -> 'reduis'
 *  - sinon (moyen dominant)                               -> 'maintiens'
 */
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

  if (last === 3) return 'reduis'; // dernière séance « dur » : on lève le pied
  if (last === 1 && !lastN.includes(3)) return 'augmente'; // facile, sans « dur » récent
  if (avg <= 1.5) return 'augmente';
  if (avg >= 2.5) return 'reduis';
  return 'maintiens';
}

/** Phrase d'action FR (tutoiement) à afficher pour un conseil donné. */
export function adviceLabel(a: ProgressionAdvice): string {
  if (a === 'augmente') return 'Augmente les reps ou la charge';
  if (a === 'reduis') return 'Réduis la charge, soigne la technique';
  return 'Maintiens, charge bien calibrée';
}
