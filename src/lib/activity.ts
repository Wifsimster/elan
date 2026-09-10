// Table de vérité des activités : ce que chacune est, comment on la nomme, ce
// qu'elle mesure. Volontairement sans dépendance au thème (les écrans lisent
// `theme[meta.colorKey]`) pour rester une brique pure et testable.
import type { MaterialCommunityIcons } from '@expo/vector-icons';

import type { ActivityType } from '@/lib/types';

type Meta = {
  label: string;
  /** Libellé court, pour les puces de filtre et les boutons. */
  shortLabel: string;
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  colorKey: 'velo' | 'muscu' | 'course' | 'marche';
  /** L'activité produit un tracé GPS : distance, vitesse, dénivelé, carte. */
  gps: boolean;
  /**
   * L'allure (min/km) parle mieux que la vitesse (km/h) — vrai à pied, faux à
   * vélo. Ne change que l'affichage : la base stocke toujours des km/h.
   */
  pace: boolean;
};

export const ACTIVITY_META: Record<ActivityType, Meta> = {
  velo: { label: 'Vélo', shortLabel: 'Vélo', icon: 'bike', colorKey: 'velo', gps: true, pace: false },
  course: {
    label: 'Course à pied',
    shortLabel: 'Course',
    icon: 'run',
    colorKey: 'course',
    gps: true,
    pace: true,
  },
  marche: { label: 'Marche', shortLabel: 'Marche', icon: 'walk', colorKey: 'marche', gps: true, pace: true },
  muscu: {
    label: 'Musculation',
    shortLabel: 'Muscu',
    icon: 'dumbbell',
    colorKey: 'muscu',
    gps: false,
    pace: false,
  },
};

/** Types d'activité dans l'ordre d'affichage (accueil, filtres, objectifs). */
export const ACTIVITY_TYPES: ActivityType[] = ['velo', 'course', 'marche', 'muscu'];

/**
 * L'activité est-elle tracée au GPS ? Sert de garde partout où le code lisait
 * `type === 'velo'` pour dire « il y a un tracé » (carte, profils, distance,
 * temps en mouvement, récupération de séance orpheline).
 */
export function isGpsActivity(type: ActivityType): boolean {
  return ACTIVITY_META[type].gps;
}

/** Faut-il présenter l'effort en allure (min/km) plutôt qu'en vitesse (km/h) ? */
export function usesPace(type: ActivityType): boolean {
  return ACTIVITY_META[type].pace;
}

/**
 * Convertit une valeur inconnue (paramètre de route, réglage restauré) en type
 * d'activité. On teste l'appartenance à `ACTIVITY_TYPES` et non `in ACTIVITY_META` :
 * `in` suit la chaîne de prototypes, si bien que `?type=toString` aurait produit
 * une activité fantôme dont la lecture des métadonnées plantait.
 */
export function toActivityType(value: unknown, fallback: ActivityType = 'velo'): ActivityType {
  return ACTIVITY_TYPES.includes(value as ActivityType) ? (value as ActivityType) : fallback;
}
