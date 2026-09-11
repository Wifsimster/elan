// Changement de type d'une séance déjà enregistrée (vélo ↔ course ↔ marche).
//
// Le type n'est pas qu'une étiquette : les calories ont été estimées avec la
// table MET de l'activité d'origine, et la cadence vient d'un capteur vélo. Ce
// module calcule — en pur, sans toucher la base — ce qui doit changer avec le
// type. L'écriture est dans `db.changeSessionType()`.
import { isGpsActivity } from '@/lib/activity';
import { estimateCalories } from '@/lib/calories';
import type { ActivityType, Session } from '@/lib/types';

/**
 * Le changement est-il permis ? Uniquement entre activités tracées au GPS :
 * passer de ou vers la musculation n'a pas de sens (une séance muscu n'a pas de
 * tracé, une sortie n'a pas de séries), et re-choisir le type courant est un
 * non-événement.
 */
export function canRetype(from: ActivityType, to: ActivityType): boolean {
  return from !== to && isGpsActivity(from) && isGpsActivity(to);
}

/** Ce qu'un changement de type modifie sur la ligne de séance. */
export type RetypeChanges = {
  type: ActivityType;
  /** Calories ré-estimées avec la table MET de la nouvelle activité. */
  calories: number;
  /** Cadence : conservée à vélo, effacée dès qu'on passe à pied. */
  avgCadence: number | null;
  maxCadence: number | null;
};

/**
 * Calcule les nouvelles valeurs de la séance pour le type `to`, ou `null` si le
 * changement n'est pas permis.
 *
 * Les calories repartent des agrégats déjà stockés, avec la même formule qu'à
 * l'enregistrement : temps EN MOUVEMENT quand il est connu (un arrêt prolongé ne
 * doit pas gonfler l'estimation), vitesse moyenne, dénivelé, FC moyenne de la
 * séance — et la FC max du PROFIL, pas celle de la séance, qui sert de référence
 * d'intensité.
 */
export function retypeChanges(
  session: Session,
  to: ActivityType,
  profile: { weightKg: number; maxHr: number },
): RetypeChanges | null {
  if (!canRetype(session.type, to)) return null;

  const effectiveSec = session.movingTimeSec ?? session.durationSec;
  const calories = estimateCalories({
    type: to,
    weightKg: profile.weightKg,
    durationSec: effectiveSec,
    avgSpeedKmh: session.avgSpeedKmh,
    elevationGainM: session.elevationGainM,
    avgHr: session.avgHr,
    maxHr: profile.maxHr,
  });

  // La cadence mesurée est celle d'un pédalier : elle ne veut plus rien dire à
  // pied, et la laisser fausserait l'export coach comme la carte de partage.
  const keepsCadence = to === 'velo';

  return {
    type: to,
    calories,
    avgCadence: keepsCadence ? session.avgCadence : null,
    maxCadence: keepsCadence ? session.maxCadence : null,
  };
}
