// Catalogue d'exercices (« magasin ») + moteur de recommandation.
//
// Objectif : rendre la musculation libre et adaptée à tous. Plutôt qu'un
// programme figé, l'utilisateur pioche dans une bibliothèque d'exercices, en
// voit le détail (muscles, matériel, exécution) et reçoit un nombre de
// répétitions et une charge conseillés, calculés à partir de son profil (poids,
// taille, sexe) et de ce qu'il cherche à faire (objectif). Tout est ensuite
// personnalisable à la main dans la séance.
//
// 100 % local, sans dépendance UI : ce module ne contient que des données et de
// l'arithmétique, comme `program.ts`. Les charges conseillées sont un POINT DE
// DÉPART pour un pratiquant intermédiaire ; le moteur de progression (dernière
// charge enregistrée) prend ensuite le relais.

import type { Profile, TrainingGoal } from '@/lib/types';

/** Grand groupe musculaire — sert de rayon dans le magasin (filtre/section). */
export type ExerciseCategory = 'Jambes' | 'Pectoraux' | 'Dos' | 'Épaules' | 'Bras' | 'Gainage';

/** Matériel nécessaire — sert de second filtre (« je n'ai que des haltères »). */
export type Equipment =
  | 'poids du corps'
  | 'haltères'
  | 'barre'
  | 'kettlebell'
  | 'élastique'
  | 'machine';

/** Une fiche d'exercice du catalogue. */
export type CatalogExercise = {
  id: string;
  name: string;
  category: ExerciseCategory;
  /** Muscles sollicités (affichés en pastilles), libellés alignés sur `program.ts`. */
  muscles: string[];
  equipment: Equipment[];
  /** Glyphe MaterialCommunityIcons illustrant le mouvement. */
  icon: string;
  /** Clé d'illustration photo (paire départ → fin), résolue dans `exercise-images.ts`. */
  imageKey?: string;
  /** « bras » / « jambe » / « côté » — travail unilatéral. */
  perSideLabel?: 'bras' | 'jambe' | 'côté';
  /** Exercice chronométré (gainage) : les « reps » sont des secondes. */
  timed?: boolean;
  /** Polyarticulaire (gros mouvement) vs isolation. */
  compound: boolean;
  /**
   * Base de la charge conseillée : charge de travail d'un pratiquant
   * intermédiaire en hypertrophie, exprimée en fraction du poids de corps.
   * 0 = au poids du corps / non chargeable.
   */
  loadFactor: number;
  /** Vrai si `loadFactor` s'entend PAR haltère (mouvements aux haltères). */
  loadPerSide?: boolean;
};

/** Ordre d'affichage des rayons. */
export const CATEGORIES: ExerciseCategory[] = ['Jambes', 'Pectoraux', 'Dos', 'Épaules', 'Bras', 'Gainage'];

/** Matériels filtrables (ordre d'affichage). */
export const EQUIPMENTS: Equipment[] = [
  'poids du corps',
  'haltères',
  'barre',
  'kettlebell',
  'élastique',
  'machine',
];

export const CATALOG: CatalogExercise[] = [
  // ---------- JAMBES ----------
  {
    id: 'goblet-squat',
    name: 'Goblet squat',
    category: 'Jambes',
    muscles: ['Quadriceps', 'Fessiers', 'Adducteurs', 'Gainage'],
    equipment: ['haltères', 'kettlebell'],
    icon: 'weight-lifter',
    imageKey: 'goblet-squat',
    compound: true,
    loadFactor: 0.3,
  },
  {
    id: 'back-squat',
    name: 'Squat barre',
    category: 'Jambes',
    muscles: ['Quadriceps', 'Fessiers', 'Adducteurs', 'Lombaires', 'Gainage'],
    equipment: ['barre'],
    icon: 'weight-lifter',
    compound: true,
    loadFactor: 1.0,
  },
  {
    id: 'romanian-deadlift',
    name: 'Soulevé de terre roumain haltères',
    category: 'Jambes',
    muscles: ['Ischio-jambiers', 'Fessiers', 'Lombaires'],
    equipment: ['haltères'],
    icon: 'weight-lifter',
    imageKey: 'dumbbell-romanian-deadlift',
    compound: true,
    loadFactor: 0.25,
    loadPerSide: true,
  },
  {
    id: 'barbell-rdl',
    name: 'Soulevé de terre roumain barre',
    category: 'Jambes',
    muscles: ['Ischio-jambiers', 'Fessiers', 'Lombaires', 'Grand dorsal'],
    equipment: ['barre'],
    icon: 'weight-lifter',
    compound: true,
    loadFactor: 1.0,
  },
  {
    id: 'dumbbell-lunges',
    name: 'Fentes avant haltères',
    category: 'Jambes',
    muscles: ['Quadriceps', 'Fessiers', 'Ischio-jambiers'],
    equipment: ['haltères'],
    icon: 'run',
    imageKey: 'dumbbell-lunges',
    perSideLabel: 'jambe',
    compound: true,
    loadFactor: 0.15,
    loadPerSide: true,
  },
  {
    id: 'bulgarian-split-squat',
    name: 'Fentes bulgares haltères',
    category: 'Jambes',
    muscles: ['Quadriceps', 'Fessiers', 'Ischio-jambiers'],
    equipment: ['haltères'],
    icon: 'run',
    imageKey: 'bulgarian-split-squat',
    perSideLabel: 'jambe',
    compound: true,
    loadFactor: 0.15,
    loadPerSide: true,
  },
  {
    id: 'hip-thrust',
    name: 'Hip thrust barre',
    category: 'Jambes',
    muscles: ['Fessiers', 'Ischio-jambiers', 'Quadriceps'],
    equipment: ['barre'],
    icon: 'bridge',
    compound: true,
    loadFactor: 1.3,
  },
  {
    id: 'glute-bridge',
    name: 'Pont fessier',
    category: 'Jambes',
    muscles: ['Fessiers', 'Lombaires', 'Ischio-jambiers'],
    equipment: ['poids du corps', 'haltères', 'élastique'],
    icon: 'bridge',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'leg-press',
    name: 'Presse à cuisses',
    category: 'Jambes',
    muscles: ['Quadriceps', 'Fessiers', 'Adducteurs'],
    equipment: ['machine'],
    icon: 'weight-lifter',
    compound: true,
    loadFactor: 1.8,
  },
  {
    id: 'kettlebell-swing',
    name: 'Kettlebell swing',
    category: 'Jambes',
    muscles: ['Fessiers', 'Ischio-jambiers', 'Lombaires', 'Gainage'],
    equipment: ['kettlebell'],
    icon: 'weight-lifter',
    compound: true,
    loadFactor: 0.22,
  },
  {
    id: 'bodyweight-squat',
    name: 'Squat au poids du corps',
    category: 'Jambes',
    muscles: ['Quadriceps', 'Fessiers', 'Adducteurs'],
    equipment: ['poids du corps'],
    icon: 'human',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'standing-calf-raise',
    name: 'Mollets debout',
    category: 'Jambes',
    muscles: ['Mollets'],
    equipment: ['poids du corps', 'haltères', 'machine'],
    icon: 'human-handsdown',
    compound: false,
    loadFactor: 0.15,
    loadPerSide: true,
  },

  // ---------- PECTORAUX ----------
  {
    id: 'dumbbell-bench-press',
    name: 'Développé couché haltères',
    category: 'Pectoraux',
    muscles: ['Pectoraux', 'Triceps', 'Deltoïdes antérieurs'],
    equipment: ['haltères'],
    icon: 'dumbbell',
    imageKey: 'dumbbell-bench-press',
    compound: true,
    loadFactor: 0.22,
    loadPerSide: true,
  },
  {
    id: 'barbell-bench-press',
    name: 'Développé couché barre',
    category: 'Pectoraux',
    muscles: ['Pectoraux', 'Triceps', 'Deltoïdes antérieurs'],
    equipment: ['barre'],
    icon: 'dumbbell',
    compound: true,
    loadFactor: 0.65,
  },
  {
    id: 'incline-dumbbell-press',
    name: 'Développé incliné haltères',
    category: 'Pectoraux',
    muscles: ['Pectoraux', 'Deltoïdes antérieurs', 'Triceps'],
    equipment: ['haltères'],
    icon: 'dumbbell',
    compound: true,
    loadFactor: 0.18,
    loadPerSide: true,
  },
  {
    id: 'push-up',
    name: 'Pompes',
    category: 'Pectoraux',
    muscles: ['Pectoraux', 'Triceps', 'Deltoïdes antérieurs', 'Gainage'],
    equipment: ['poids du corps'],
    icon: 'human',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'dumbbell-fly',
    name: 'Écarté couché haltères',
    category: 'Pectoraux',
    muscles: ['Pectoraux', 'Deltoïdes antérieurs'],
    equipment: ['haltères'],
    icon: 'arrow-collapse-horizontal',
    compound: false,
    loadFactor: 0.1,
    loadPerSide: true,
  },
  {
    id: 'band-chest-press',
    name: 'Développé poitrine élastique',
    category: 'Pectoraux',
    muscles: ['Pectoraux', 'Triceps', 'Deltoïdes antérieurs'],
    equipment: ['élastique'],
    icon: 'arrow-collapse-horizontal',
    compound: true,
    loadFactor: 0,
  },

  // ---------- DOS ----------
  {
    id: 'one-arm-dumbbell-row',
    name: 'Rowing haltère un bras',
    category: 'Dos',
    muscles: ['Grand dorsal', 'Trapèzes', 'Rhomboïdes', 'Biceps'],
    equipment: ['haltères'],
    icon: 'arm-flex',
    imageKey: 'one-arm-dumbbell-row',
    perSideLabel: 'bras',
    compound: true,
    loadFactor: 0.28,
    loadPerSide: true,
  },
  {
    id: 'bent-over-two-dumbbell-row',
    name: 'Rowing penché 2 bras haltères',
    category: 'Dos',
    muscles: ['Grand dorsal', 'Trapèzes', 'Rhomboïdes', 'Biceps'],
    equipment: ['haltères'],
    icon: 'arm-flex',
    imageKey: 'bent-over-two-dumbbell-row',
    compound: true,
    loadFactor: 0.22,
    loadPerSide: true,
  },
  {
    id: 'barbell-row',
    name: 'Rowing barre',
    category: 'Dos',
    muscles: ['Grand dorsal', 'Trapèzes', 'Rhomboïdes', 'Biceps', 'Lombaires'],
    equipment: ['barre'],
    icon: 'arm-flex',
    compound: true,
    loadFactor: 0.6,
  },
  {
    id: 'deadlift',
    name: 'Soulevé de terre barre',
    category: 'Dos',
    muscles: ['Lombaires', 'Fessiers', 'Ischio-jambiers', 'Trapèzes', 'Grand dorsal'],
    equipment: ['barre'],
    icon: 'weight-lifter',
    compound: true,
    loadFactor: 1.4,
  },
  {
    id: 'pull-up',
    name: 'Tractions',
    category: 'Dos',
    muscles: ['Grand dorsal', 'Biceps', 'Rhomboïdes', 'Trapèzes'],
    equipment: ['poids du corps'],
    icon: 'arm-flex',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'lat-pulldown',
    name: 'Tirage vertical poulie',
    category: 'Dos',
    muscles: ['Grand dorsal', 'Biceps', 'Rhomboïdes'],
    equipment: ['machine'],
    icon: 'arm-flex',
    compound: true,
    loadFactor: 0.6,
  },
  {
    id: 'band-row',
    name: 'Tirage horizontal élastique',
    category: 'Dos',
    muscles: ['Grand dorsal', 'Rhomboïdes', 'Trapèzes', 'Biceps'],
    equipment: ['élastique'],
    icon: 'arrow-collapse-horizontal',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'scapular-retraction',
    name: 'Rétractions scapulaires',
    category: 'Dos',
    muscles: ['Rhomboïdes', 'Trapèzes', 'Deltoïdes'],
    equipment: ['poids du corps', 'élastique'],
    icon: 'arrow-collapse-horizontal',
    compound: false,
    loadFactor: 0,
  },

  // ---------- ÉPAULES ----------
  {
    id: 'standing-dumbbell-press',
    name: 'Développé épaules debout haltères',
    category: 'Épaules',
    muscles: ['Deltoïdes', 'Triceps', 'Gainage'],
    equipment: ['haltères'],
    icon: 'dumbbell',
    imageKey: 'standing-dumbbell-press',
    compound: true,
    loadFactor: 0.14,
    loadPerSide: true,
  },
  {
    id: 'overhead-press-barbell',
    name: 'Développé militaire barre',
    category: 'Épaules',
    muscles: ['Deltoïdes', 'Triceps', 'Trapèzes', 'Gainage'],
    equipment: ['barre'],
    icon: 'dumbbell',
    compound: true,
    loadFactor: 0.45,
  },
  {
    id: 'lateral-raise',
    name: 'Élévations latérales',
    category: 'Épaules',
    muscles: ['Deltoïdes'],
    equipment: ['haltères', 'élastique'],
    icon: 'arrow-up-bold',
    compound: false,
    loadFactor: 0.06,
    loadPerSide: true,
  },
  {
    id: 'rear-delt-fly',
    name: 'Oiseau (deltoïdes postérieurs)',
    category: 'Épaules',
    muscles: ['Deltoïdes', 'Rhomboïdes', 'Trapèzes'],
    equipment: ['haltères', 'élastique'],
    icon: 'arrow-collapse-horizontal',
    compound: false,
    loadFactor: 0.05,
    loadPerSide: true,
  },
  {
    id: 'band-face-pull',
    name: 'Face pull élastique',
    category: 'Épaules',
    muscles: ['Deltoïdes', 'Trapèzes', 'Rhomboïdes'],
    equipment: ['élastique'],
    icon: 'arrow-collapse-horizontal',
    compound: false,
    loadFactor: 0,
  },

  // ---------- BRAS ----------
  {
    id: 'dumbbell-biceps-curl',
    name: 'Curl biceps haltères',
    category: 'Bras',
    muscles: ['Biceps'],
    equipment: ['haltères'],
    icon: 'arm-flex',
    compound: false,
    loadFactor: 0.13,
    loadPerSide: true,
  },
  {
    id: 'hammer-curl',
    name: 'Curl marteau haltères',
    category: 'Bras',
    muscles: ['Biceps'],
    equipment: ['haltères'],
    icon: 'arm-flex',
    compound: false,
    loadFactor: 0.14,
    loadPerSide: true,
  },
  {
    id: 'triceps-overhead-extension',
    name: 'Extension triceps nuque haltère',
    category: 'Bras',
    muscles: ['Triceps'],
    equipment: ['haltères'],
    icon: 'dumbbell',
    compound: false,
    loadFactor: 0.18,
  },
  {
    id: 'triceps-dips-bench',
    name: 'Dips entre deux appuis',
    category: 'Bras',
    muscles: ['Triceps', 'Pectoraux', 'Deltoïdes antérieurs'],
    equipment: ['poids du corps'],
    icon: 'human',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'band-biceps-curl',
    name: 'Curl biceps élastique',
    category: 'Bras',
    muscles: ['Biceps'],
    equipment: ['élastique'],
    icon: 'arm-flex',
    compound: false,
    loadFactor: 0,
  },

  // ---------- GAINAGE ----------
  {
    id: 'plank',
    name: 'Gainage planche',
    category: 'Gainage',
    muscles: ['Abdominaux', 'Transverse', 'Lombaires'],
    equipment: ['poids du corps'],
    icon: 'yoga',
    imageKey: 'plank',
    timed: true,
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'side-plank',
    name: 'Gainage latéral',
    category: 'Gainage',
    muscles: ['Obliques', 'Transverse', 'Moyen fessier'],
    equipment: ['poids du corps'],
    icon: 'yoga',
    imageKey: 'side-plank',
    perSideLabel: 'côté',
    timed: true,
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'dead-bug',
    name: 'Dead bug',
    category: 'Gainage',
    muscles: ['Transverse', 'Abdominaux', 'Gainage'],
    equipment: ['poids du corps'],
    icon: 'bug',
    perSideLabel: 'côté',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'bird-dog',
    name: 'Bird-dog',
    category: 'Gainage',
    muscles: ['Gainage', 'Transverse', 'Lombaires', 'Fessiers'],
    equipment: ['poids du corps'],
    icon: 'dog',
    perSideLabel: 'côté',
    compound: true,
    loadFactor: 0,
  },
  {
    id: 'mcgill-curl-up',
    name: 'McGill curl-up',
    category: 'Gainage',
    muscles: ['Abdominaux', 'Transverse', 'Gainage'],
    equipment: ['poids du corps'],
    icon: 'human',
    compound: false,
    loadFactor: 0,
  },
  {
    id: 'superman',
    name: 'Superman (extension lombaire)',
    category: 'Gainage',
    muscles: ['Lombaires', 'Fessiers', 'Dos'],
    equipment: ['poids du corps'],
    icon: 'arrow-up-bold',
    compound: false,
    loadFactor: 0,
  },
];

// ---------------------------------------------------------------------------
// Objectifs d'entraînement
// ---------------------------------------------------------------------------

/** Paramètres d'un objectif : schéma de séries/reps/repos + intensité (charge). */
export type GoalSpec = {
  id: TrainingGoal;
  label: string;
  /** Phrase courte décrivant l'objectif (affichée à la sélection). */
  blurb: string;
  sets: number;
  repsMin: number;
  repsMax: number;
  restSec: number;
  /** Multiplie `loadFactor` pour obtenir la charge conseillée. */
  intensityFactor: number;
  /** Fourchette en secondes pour les exercices chronométrés (gainage). */
  timedMinSec: number;
  timedMaxSec: number;
};

// Fourchettes standard (NSCA / ACSM) : force = lourd/peu de reps/repos long,
// hypertrophie = modéré, endurance = léger/beaucoup de reps/repos court.
export const GOALS: GoalSpec[] = [
  {
    id: 'force',
    label: 'Force',
    blurb: 'Soulever lourd, peu de répétitions, longs repos.',
    sets: 5,
    repsMin: 3,
    repsMax: 6,
    restSec: 180,
    intensityFactor: 1.15,
    timedMinSec: 30,
    timedMaxSec: 60,
  },
  {
    id: 'hypertrophie',
    label: 'Prise de muscle',
    blurb: 'Charges modérées, volume, repos moyens.',
    sets: 4,
    repsMin: 8,
    repsMax: 12,
    restSec: 90,
    intensityFactor: 1.0,
    timedMinSec: 30,
    timedMaxSec: 45,
  },
  {
    id: 'endurance',
    label: 'Endurance',
    blurb: 'Charges légères, beaucoup de répétitions, repos courts.',
    sets: 3,
    repsMin: 15,
    repsMax: 20,
    restSec: 45,
    intensityFactor: 0.65,
    timedMinSec: 45,
    timedMaxSec: 90,
  },
  {
    id: 'tonification',
    label: 'Remise en forme',
    blurb: 'Tonifier en douceur, charges modérées, polyvalent.',
    sets: 3,
    repsMin: 10,
    repsMax: 15,
    restSec: 60,
    intensityFactor: 0.8,
    timedMinSec: 25,
    timedMaxSec: 45,
  },
  {
    id: 'perte-poids',
    label: 'Perte de poids',
    blurb: 'Rythme soutenu, repos courts, dépense énergétique.',
    sets: 3,
    repsMin: 12,
    repsMax: 15,
    restSec: 45,
    intensityFactor: 0.75,
    timedMinSec: 30,
    timedMaxSec: 60,
  },
];

/** Spécification d'un objectif (repli sur l'hypertrophie si inconnu). */
export function goalSpec(goal: TrainingGoal): GoalSpec {
  return GOALS.find((g) => g.id === goal) ?? GOALS[1];
}

/** Libellé court d'un objectif, pour l'affichage. */
export function goalLabel(goal: TrainingGoal): string {
  return goalSpec(goal).label;
}

// ---------------------------------------------------------------------------
// Moteur de recommandation
// ---------------------------------------------------------------------------

// Femmes : charges absolues plus basses, écart plus marqué sur le haut du corps.
const SEX_FACTOR: Record<'h' | 'f' | 'null', { upper: number; lower: number }> = {
  f: { upper: 0.65, lower: 0.8 },
  h: { upper: 1.0, lower: 1.0 },
  null: { upper: 1.0, lower: 1.0 },
};

/** Catégories considérées « haut du corps » pour le facteur sexe. */
const UPPER_BODY = new Set<ExerciseCategory>(['Pectoraux', 'Dos', 'Épaules', 'Bras']);

/**
 * Arrondit une charge à un pas réaliste : barre / charges lourdes au pas de
 * 2,5 kg, travail léger à l'haltère au kg, très léger au demi-kilo.
 */
function roundWeight(kg: number, ex: CatalogExercise): number {
  if (kg <= 0) return 0;
  const step = !ex.loadPerSide && ex.loadFactor >= 0.5 ? 2.5 : kg < 6 ? 0.5 : 1;
  return Math.max(0, Math.round(kg / step) * step);
}

/** Recommandation calculée pour un exercice donné et un profil donné. */
export type Recommendation = {
  sets: number;
  repsMin: number;
  repsMax: number;
  restSec: number;
  /** Charge conseillée en kg. Si `perDumbbell`, c'est la charge PAR haltère. */
  weightKg: number;
  /** Vrai ⇒ `weightKg` s'entend par haltère (afficher « / main »). */
  perDumbbell: boolean;
  /** Vrai ⇒ `repsMin`/`repsMax` sont des SECONDES (gainage chronométré). */
  timed: boolean;
};

/** Sous-ensemble de profil dont dépend la recommandation. */
export type RecoProfile = Pick<Profile, 'weightKg' | 'heightCm' | 'sex' | 'goal'>;

/**
 * Calcule reps, séries, repos et charge conseillés pour un exercice à partir du
 * profil (poids, taille, sexe) et de l'objectif. La taille n'entre pas dans le
 * calcul de charge : le poids de corps capture déjà l'essentiel du gabarit, et
 * des bras de levier plus longs auraient un effet inverse non fiable — on évite
 * de compter deux fois la morphologie.
 */
export function recommend(profile: RecoProfile, ex: CatalogExercise): Recommendation {
  const goal = goalSpec(profile.goal);

  // 1) Exercices chronométrés (gainage) → pas de charge, reps = secondes.
  if (ex.timed) {
    return {
      sets: goal.sets,
      repsMin: goal.timedMinSec,
      repsMax: goal.timedMaxSec,
      restSec: goal.restSec,
      weightKg: 0,
      perDumbbell: false,
      timed: true,
    };
  }

  // 2) Mouvements au poids du corps / non chargeables.
  if (ex.loadFactor === 0) {
    return {
      sets: goal.sets,
      repsMin: goal.repsMin,
      repsMax: goal.repsMax,
      restSec: goal.restSec,
      weightKg: 0,
      perDumbbell: false,
      timed: false,
    };
  }

  // 3) Charge de base = loadFactor × poids de corps × intensité de l'objectif.
  let w = ex.loadFactor * profile.weightKg * goal.intensityFactor;

  // 4) Facteur sexe (haut vs bas du corps).
  const sexKey: 'h' | 'f' | 'null' = profile.sex === 'h' || profile.sex === 'f' ? profile.sex : 'null';
  const region = UPPER_BODY.has(ex.category) ? 'upper' : 'lower';
  w *= SEX_FACTOR[sexKey][region];

  return {
    sets: goal.sets,
    repsMin: goal.repsMin,
    repsMax: goal.repsMax,
    restSec: goal.restSec,
    weightKg: roundWeight(w, ex),
    perDumbbell: ex.loadPerSide === true,
    timed: false,
  };
}

// ---------------------------------------------------------------------------
// Recherche / formatage
// ---------------------------------------------------------------------------

/** Retrouve une fiche du catalogue par son id. */
export function catalogById(id: string | undefined): CatalogExercise | undefined {
  if (!id) return undefined;
  return CATALOG.find((e) => e.id === id);
}

/** Retrouve une fiche par son nom exact (enrichit un exercice enregistré). */
export function catalogByName(name: string | undefined): CatalogExercise | undefined {
  if (!name) return undefined;
  return CATALOG.find((e) => e.name === name);
}

/**
 * Indice de cible lisible à partir d'une recommandation, ex.
 * « 4 × 8-12 » ou « 3 × 30-45 s / côté ».
 */
export function recoHint(ex: CatalogExercise, rec: Recommendation): string {
  const reps = rec.repsMin === rec.repsMax ? `${rec.repsMin}` : `${rec.repsMin}-${rec.repsMax}`;
  const unit = rec.timed ? ' s' : '';
  const side = ex.perSideLabel ? ` / ${ex.perSideLabel}` : '';
  return `${rec.sets} × ${reps}${unit}${side}`;
}

const fmtKg = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

/** Libellé de charge conseillée, ex. « 16 kg / main » ou « au poids du corps ». */
export function recoWeightLabel(rec: Recommendation): string {
  if (rec.timed) return 'gainage chronométré';
  if (rec.weightKg <= 0) return 'au poids du corps';
  return `${fmtKg(rec.weightKg)} kg${rec.perDumbbell ? ' / main' : ''}`;
}

// ---------------------------------------------------------------------------
// Exécution (« comment faire ») par exercice
// ---------------------------------------------------------------------------

// Texte d'exécution simple et sûr pour chaque mouvement. Repris à l'identique de
// `program.ts` pour les exercices communs, rédigé dans le même esprit pour les
// autres. Séparé du tableau `CATALOG` pour garder ce dernier lisible.
const HOWTO: Record<string, string> = {
  'goblet-squat':
    'Tiens un haltère verticalement contre la poitrine, à deux mains. Pieds largeur d’épaules. Descends en pliant les genoux, dos droit et talons au sol, jusqu’à ce que les cuisses soient parallèles au sol, puis remonte.',
  'back-squat':
    'Barre posée sur le haut du dos (trapèzes), pieds largeur d’épaules. Descends en envoyant les hanches vers l’arrière, dos gainé et talons au sol, jusqu’aux cuisses parallèles, puis remonte en poussant dans le sol. Commence léger, la technique prime.',
  'romanian-deadlift':
    'Debout, haltères devant les cuisses, jambes quasi tendues (genoux légèrement fléchis). Penche le buste en envoyant les fesses vers l’arrière, dos bien droit, les haltères descendent le long des jambes jusqu’à sentir l’étirement des ischios, puis reviens en serrant les fessiers.',
  'barbell-rdl':
    'Barre devant les cuisses, prise largeur d’épaules, genoux légèrement fléchis. Envoie les hanches en arrière en gardant le dos plat, la barre glisse le long des jambes jusqu’à mi-tibia, puis reviens en serrant les fessiers. Ne arrondis jamais le bas du dos.',
  'dumbbell-lunges':
    'Un haltère dans chaque main, bras le long du corps. Fais un grand pas en avant et plie les deux genoux jusqu’à ~90°, buste droit, sans que le genou arrière touche le sol. Pousse sur la jambe avant pour revenir. Alterne les jambes.',
  'bulgarian-split-squat':
    'Pied arrière posé sur une chaise/un canapé derrière toi, un haltère dans chaque main. Descends sur la jambe avant jusqu’à ce que la cuisse soit parallèle au sol, buste droit, puis remonte en poussant sur le talon avant. Fais l’autre jambe.',
  'hip-thrust':
    'Haut du dos appuyé sur un banc, barre (rembourrée) sur le pli des hanches, pieds à plat. Décolle le bassin en serrant fort les fessiers jusqu’à aligner épaules-hanches-genoux, sans cambrer, puis redescends en contrôlant.',
  'glute-bridge':
    'Allongé sur le dos, genoux fléchis, pieds à plat largeur de bassin, bras le long du corps. Décolle le bassin en serrant les fessiers jusqu’à aligner épaules-hanches-genoux, sans cambrer. Tiens une seconde en haut puis redescends en contrôlant.',
  'leg-press':
    'Assis dans la machine, pieds à plat sur la plateforme largeur d’épaules. Déverrouille puis descends en contrôlant jusqu’à ~90° aux genoux sans décoller le bas du dos, puis pousse sans bloquer brutalement les genoux en fin de mouvement.',
  'kettlebell-swing':
    'Kettlebell à deux mains, pieds un peu plus larges que les épaules. Bascule les hanches vers l’arrière puis projette-les vers l’avant pour propulser le poids à hauteur de poitrine, bras relâchés. La force vient des hanches, pas des bras. Dos gainé tout du long.',
  'bodyweight-squat':
    'Pieds largeur d’épaules, bras devant pour l’équilibre. Descends en pliant les genoux et en envoyant les hanches vers l’arrière, dos droit et talons au sol, jusqu’aux cuisses parallèles, puis remonte. Idéal pour apprendre le mouvement.',
  'standing-calf-raise':
    'Debout, pointe des pieds sur une marche ou à plat, un haltère en main si tu charges. Monte le plus haut possible sur la pointe des pieds en contractant les mollets, marque un temps en haut, puis redescends lentement en étirant.',
  'dumbbell-bench-press':
    'Allongé sur le dos (banc ou sol), un haltère dans chaque main au niveau de la poitrine, coudes ouverts. Pousse les haltères vers le plafond bras tendus, puis redescends lentement.',
  'barbell-bench-press':
    'Allongé sur un banc, barre au-dessus de la poitrine, prise un peu plus large que les épaules. Descends la barre en contrôlant jusqu’au bas des pectoraux, coudes à ~45°, puis pousse jusqu’aux bras tendus. Garde les omoplates serrées et les pieds au sol.',
  'incline-dumbbell-press':
    'Sur un banc incliné (~30°), un haltère dans chaque main à hauteur du haut des pectoraux. Pousse vers le plafond bras tendus sans cogner les haltères, puis redescends lentement en ouvrant légèrement les coudes.',
  'push-up':
    'Mains au sol un peu plus larges que les épaules, corps gainé et aligné de la tête aux talons. Descends en pliant les coudes (~45° du corps) jusqu’à frôler le sol, puis pousse pour remonter. Trop dur ? Pose les genoux au sol.',
  'dumbbell-fly':
    'Allongé sur un banc, un haltère léger dans chaque main au-dessus de la poitrine, coudes légèrement fléchis et fixes. Ouvre les bras en arc de cercle jusqu’à sentir l’étirement des pectoraux, puis reviens en « refermant » sans tendre les coudes.',
  'band-chest-press':
    'Élastique passé dans le dos et tenu à deux mains à hauteur de poitrine. Pousse les mains vers l’avant jusqu’aux bras tendus en contractant les pectoraux, puis reviens en contrôlant la tension de l’élastique.',
  'one-arm-dumbbell-row':
    'Un genou et une main en appui sur une table ou un banc, dos plat et horizontal. Bras tendu vers le sol, tire l’haltère le long du flanc vers la hanche, coude près du corps, puis redescends. Fais l’autre bras.',
  'bent-over-two-dumbbell-row':
    'Buste penché en avant (~45°), dos plat, genoux légèrement fléchis, haltères sous les épaules bras tendus. Tire les deux haltères vers le bas-ventre en serrant les omoplates, coudes près du corps, puis redescends en contrôlant.',
  'barbell-row':
    'Buste penché (~45°), dos plat, barre bras tendus sous les épaules. Tire la barre vers le bas-ventre en serrant les omoplates, coudes près du corps, puis redescends en contrôlant. Garde le bas du dos gainé.',
  deadlift:
    'Barre au sol contre les tibias, pieds largeur de bassin. Attrape la barre, dos plat et poitrine haute, puis pousse dans le sol en tendant hanches et genoux ensemble, barre collée aux jambes. Verrouille debout sans tirer en arrière. Mouvement technique : reste léger au début.',
  'pull-up':
    'Suspendu à une barre, prise largeur d’épaules (paumes vers l’avant). Tire en amenant la poitrine vers la barre, coudes vers le bas, puis redescends en contrôlant bras tendus. Trop dur ? Aide-toi d’un élastique ou d’un appui des pieds.',
  'lat-pulldown':
    'Assis face à la poulie haute, cuisses bloquées, prise large. Tire la barre vers le haut de la poitrine en serrant les omoplates et en gardant le buste légèrement incliné en arrière, puis remonte en contrôlant.',
  'band-row':
    'Élastique fixé devant toi à hauteur de poitrine (ou autour des pieds, jambes tendues). Tire les poignées vers le bas des côtes en serrant les omoplates, coudes près du corps, puis reviens en contrôlant la tension.',
  'scapular-retraction':
    'Debout ou assis, bras le long du corps (ou tenant un élastique devant toi). Serre les omoplates l’une vers l’autre vers le bas, sans hausser les épaules ni cambrer. Tiens 2 s puis relâche. Ouvre les épaules, contre la position assise fermée.',
  'standing-dumbbell-press':
    'Debout, gainé, un haltère de chaque côté à hauteur d’épaules, paumes vers l’avant. Pousse les haltères au-dessus de la tête bras tendus sans cambrer le dos, puis redescends à hauteur d’épaules.',
  'overhead-press-barbell':
    'Debout, barre sur le haut des pectoraux, prise largeur d’épaules, gainage serré. Pousse la barre au-dessus de la tête bras tendus en rentrant légèrement la tête, sans cambrer le bas du dos, puis redescends en contrôlant.',
  'lateral-raise':
    'Debout, un haltère léger dans chaque main le long du corps, coudes à peine fléchis. Lève les bras sur les côtés jusqu’à hauteur d’épaules (pas plus haut), comme pour « verser de l’eau », puis redescends lentement. Mouvement contrôlé, sans élan.',
  'rear-delt-fly':
    'Buste penché en avant, dos plat, un haltère léger dans chaque main sous la poitrine. Ouvre les bras sur les côtés en serrant les omoplates, coudes à peine fléchis, jusqu’à hauteur d’épaules, puis reviens en contrôlant. Cible l’arrière de l’épaule.',
  'band-face-pull':
    'Élastique fixé à hauteur du visage. Tire les deux brins vers ton front en écartant les mains et en serrant les omoplates, coudes hauts, puis reviens en contrôlant. Excellent pour la posture et la santé des épaules.',
  'dumbbell-biceps-curl':
    'Debout, un haltère dans chaque main, bras le long du corps, paumes vers l’avant. Plie les coudes pour monter les haltères vers les épaules sans bouger les coudes ni balancer le buste, puis redescends lentement.',
  'hammer-curl':
    'Comme un curl, mais paumes face à face (prise marteau) tout du long. Monte les haltères vers les épaules sans bouger les coudes, puis redescends en contrôlant. Sollicite biceps et avant-bras.',
  'triceps-overhead-extension':
    'Debout ou assis, un haltère tenu à deux mains au-dessus de la tête, bras tendus. Descends l’haltère derrière la nuque en pliant les coudes (qui restent hauts et serrés), puis tends les bras pour remonter. Contrôle la descente.',
  'triceps-dips-bench':
    'Mains posées au bord d’une chaise/banc derrière toi, jambes devant. Descends le bassin en pliant les coudes vers l’arrière jusqu’à ~90°, puis pousse pour remonter en tendant les bras. Plie les genoux pour faciliter, tends-les pour durcir.',
  'band-biceps-curl':
    'Debout sur l’élastique, une poignée dans chaque main, bras le long du corps. Plie les coudes pour monter les mains vers les épaules contre la tension, sans bouger les coudes, puis redescends en contrôlant.',
  plank:
    'En appui sur les avant-bras et la pointe des pieds, corps gainé et bien aligné des épaules aux talons (pas de creux dans le bas du dos, fesses ni trop hautes ni trop basses). Tiens la position en respirant.',
  'side-plank':
    'Allongé sur le côté, en appui sur un avant-bras (coude sous l’épaule), corps aligné de la tête aux pieds. Décolle les hanches et tiens la position sans laisser le bassin tomber. Puis change de côté.',
  'dead-bug':
    'Allongé sur le dos, bras tendus vers le plafond et genoux fléchis au-dessus des hanches (90°). Garde le bas du dos plaqué au sol et descends lentement le bras droit derrière la tête et la jambe gauche vers le sol, sans creuser les lombaires. Reviens et alterne.',
  'bird-dog':
    'À quatre pattes, mains sous les épaules et genoux sous les hanches, dos plat. Tends en même temps le bras droit devant et la jambe gauche derrière, alignés avec le tronc, sans cambrer ni tourner le bassin. Reviens et alterne. Gainage anti-rotation très sûr.',
  'mcgill-curl-up':
    'Allongé sur le dos, une jambe pliée pied au sol et l’autre tendue. Place tes mains sous le bas du dos pour garder sa courbure naturelle. Décolle juste la tête et les épaules de quelques centimètres sans plier la colonne, menton rentré, puis redescends doucement.',
  superman:
    'Allongé sur le ventre, bras tendus devant. Décolle légèrement bras, poitrine et jambes du sol en gardant le regard vers le bas et la nuque longue, sur une amplitude réduite. Tiens brièvement puis relâche. À n’introduire que sans aucune douleur.',
};

/** Texte d'exécution (« comment faire ») d'un exercice, ou `undefined`. */
export function exerciseHowTo(id: string): string | undefined {
  return HOWTO[id];
}
