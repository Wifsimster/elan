// Estimations de force pour la musculation. Calculs purs, 100 % locaux.

/**
 * Estimation du 1RM (charge maximale théorique sur une seule répétition) par la
 * formule d'Epley : `1RM ≈ poids × (1 + reps / 30)`. Elle normalise une série
 * (charge × répétitions) sur une même échelle de force, ce qui permet de
 * comparer des séances où la charge et le nombre de reps varient — un meilleur
 * indicateur de progression que la seule charge max.
 *
 * Renvoie 0 pour une série sans charge (gainage chronométré) ou invalide.
 */
export function epley1RM(weightKg: number, reps: number): number {
  if (weightKg <= 0 || reps <= 0) return 0;
  if (reps === 1) return weightKg;
  return weightKg * (1 + reps / 30);
}
