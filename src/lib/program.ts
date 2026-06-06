// Programme muscu perso « Maison (haltères) » — prise de muscle.
// Deux séances full-body espacées dans la semaine (mardi A, vendredi B).
// Sert à pré-remplir une séance depuis l'écran muscu : exercices, nombre de
// séries et fourchette de reps en indice. Les charges restent à ajuster à la main
// (c'est le moteur de la progression). Charges de départ volontairement modestes
// pour la montée en charge douce des premières semaines.

import { deleteSetting, getSetting, setSetting } from '@/lib/db';

export type TemplateExercise = {
  name: string;
  sets: number;
  repsMin: number;
  repsMax: number;
  startWeightKg: number;
  /** Explication simple : comment exécuter le mouvement. */
  howTo: string;
  /** Groupes musculaires principalement sollicités (affichés en pastilles). */
  muscles: string[];
  /**
   * Glyphe MaterialCommunityIcons illustrant le mouvement, posé sur le héros de
   * la fiche d'exercice. Simple chaîne pour garder ce module sans dépendance UI.
   */
  icon: string;
  /**
   * Clé d'illustration photo (paire départ → fin), résolue dans
   * `components/exercise-images.ts`. Images domaine public bundlées localement.
   */
  imageKey?: string;
  /** « bras » / « jambe » / « côté » — affiché dans la cible et le travail unilatéral. */
  perSideLabel?: string;
  /** Exercice chronométré (gainage) : les « reps » sont des secondes. */
  timed?: boolean;
};

export type WorkoutTemplate = {
  id: 'fullbody-a' | 'fullbody-b' | 'dos-lombaire' | 'cervicales';
  name: string;
  day: string;
  exercises: TemplateExercise[];
};

export const TEMPLATES: WorkoutTemplate[] = [
  {
    id: 'fullbody-a',
    name: 'Full-body A',
    day: 'Mardi',
    exercises: [
      {
        name: 'Goblet squat',
        sets: 3, repsMin: 8, repsMax: 12, startWeightKg: 20,
        icon: 'weight-lifter',
        imageKey: 'goblet-squat',
        muscles: ['Quadriceps', 'Fessiers', 'Adducteurs', 'Gainage'],
        howTo:
          'Tiens un haltère verticalement contre la poitrine, à deux mains. Pieds largeur d’épaules. Descends en pliant les genoux, dos droit et talons au sol, jusqu’à ce que les cuisses soient parallèles au sol, puis remonte.',
      },
      {
        name: 'Développé couché haltères',
        sets: 3, repsMin: 8, repsMax: 12, startWeightKg: 16,
        icon: 'dumbbell',
        imageKey: 'dumbbell-bench-press',
        muscles: ['Pectoraux', 'Triceps', 'Deltoïdes antérieurs'],
        howTo:
          'Allongé sur le dos (banc ou sol), un haltère dans chaque main au niveau de la poitrine, coudes ouverts. Pousse les haltères vers le plafond bras tendus, puis redescends lentement.',
      },
      {
        name: 'Rowing haltère un bras',
        sets: 3, repsMin: 8, repsMax: 12, startWeightKg: 20, perSideLabel: 'bras',
        icon: 'arm-flex',
        imageKey: 'one-arm-dumbbell-row',
        muscles: ['Grand dorsal', 'Trapèzes', 'Rhomboïdes', 'Biceps'],
        howTo:
          'Un genou et une main en appui sur une table ou un banc, dos plat et horizontal. Bras tendu vers le sol, tire l’haltère le long du flanc vers la hanche, coude près du corps, puis redescends. Fais l’autre bras.',
      },
      {
        name: 'Fentes avant haltères',
        sets: 3, repsMin: 10, repsMax: 10, startWeightKg: 12, perSideLabel: 'jambe',
        icon: 'run',
        imageKey: 'dumbbell-lunges',
        muscles: ['Quadriceps', 'Fessiers', 'Ischio-jambiers'],
        howTo:
          'Un haltère dans chaque main, bras le long du corps. Fais un grand pas en avant et plie les deux genoux jusqu’à ~90°, buste droit, sans que le genou arrière touche le sol. Pousse sur la jambe avant pour revenir. Alterne les jambes.',
      },
      {
        name: 'Gainage planche',
        sets: 3, repsMin: 20, repsMax: 40, startWeightKg: 0, timed: true,
        icon: 'yoga',
        imageKey: 'plank',
        muscles: ['Abdominaux', 'Transverse', 'Lombaires'],
        howTo:
          'En appui sur les avant-bras et la pointe des pieds, corps gainé et bien aligné des épaules aux talons (pas de creux dans le bas du dos, fesses ni trop hautes ni trop basses). Tiens la position en respirant.',
      },
    ],
  },
  {
    id: 'fullbody-b',
    name: 'Full-body B',
    day: 'Vendredi',
    exercises: [
      {
        name: 'Soulevé de terre roumain haltères',
        sets: 3, repsMin: 8, repsMax: 12, startWeightKg: 20,
        icon: 'weight-lifter',
        imageKey: 'dumbbell-romanian-deadlift',
        muscles: ['Ischio-jambiers', 'Fessiers', 'Lombaires'],
        howTo:
          'Debout, haltères devant les cuisses, jambes quasi tendues (genoux légèrement fléchis). Penche le buste en envoyant les fesses vers l’arrière, dos bien droit, les haltères descendent le long des jambes jusqu’à sentir l’étirement des ischios, puis reviens en serrant les fessiers.',
      },
      {
        name: 'Développé épaules debout haltères',
        sets: 3, repsMin: 8, repsMax: 12, startWeightKg: 12,
        icon: 'dumbbell',
        imageKey: 'standing-dumbbell-press',
        muscles: ['Deltoïdes', 'Triceps', 'Gainage'],
        howTo:
          'Debout, gainé, un haltère de chaque côté à hauteur d’épaules, paumes vers l’avant. Pousse les haltères au-dessus de la tête bras tendus sans cambrer le dos, puis redescends à hauteur d’épaules.',
      },
      {
        name: 'Rowing penché 2 bras haltères',
        sets: 3, repsMin: 8, repsMax: 12, startWeightKg: 16,
        icon: 'arm-flex',
        imageKey: 'bent-over-two-dumbbell-row',
        muscles: ['Grand dorsal', 'Trapèzes', 'Rhomboïdes', 'Biceps'],
        howTo:
          'Buste penché en avant (~45°), dos plat, genoux légèrement fléchis, haltères sous les épaules bras tendus. Tire les deux haltères vers le bas-ventre en serrant les omoplates, coudes près du corps, puis redescends en contrôlant.',
      },
      {
        name: 'Fentes bulgares haltères',
        sets: 3, repsMin: 8, repsMax: 10, startWeightKg: 12, perSideLabel: 'jambe',
        icon: 'run',
        imageKey: 'bulgarian-split-squat',
        muscles: ['Quadriceps', 'Fessiers', 'Ischio-jambiers'],
        howTo:
          'Pied arrière posé sur une chaise/un canapé derrière toi, un haltère dans chaque main. Descends sur la jambe avant jusqu’à ce que la cuisse soit parallèle au sol, buste droit, puis remonte en poussant sur le talon avant. Fais l’autre jambe.',
      },
      {
        name: 'Gainage latéral',
        sets: 3, repsMin: 15, repsMax: 30, startWeightKg: 0, timed: true, perSideLabel: 'côté',
        icon: 'yoga',
        imageKey: 'side-plank',
        muscles: ['Obliques', 'Transverse', 'Moyen fessier'],
        howTo:
          'Allongé sur le côté, en appui sur un avant-bras (coude sous l’épaule), corps aligné de la tête aux pieds. Décolle les hanches et tiens la position sans laisser le bassin tomber. Puis change de côté.',
      },
    ],
  },
  {
    id: 'dos-lombaire',
    name: 'Dos / lombaire',
    day: 'Renfort doux',
    exercises: [
      {
        name: 'Bird-dog',
        sets: 3, repsMin: 8, repsMax: 10, startWeightKg: 0, perSideLabel: 'côté',
        icon: 'dog',
        muscles: ['Gainage', 'Transverse', 'Lombaires', 'Fessiers'],
        howTo:
          'À quatre pattes (quadrupédie), mains sous les épaules et genoux sous les hanches, dos plat. Tends en même temps le bras droit devant et la jambe gauche derrière, alignés avec le tronc, sans cambrer ni tourner le bassin. Reviens et alterne avec l’autre côté. Gainage anti-rotation, très sûr pour la colonne.',
      },
      {
        name: 'Dead bug',
        sets: 3, repsMin: 8, repsMax: 10, startWeightKg: 0, perSideLabel: 'côté',
        icon: 'bug',
        muscles: ['Transverse', 'Abdominaux', 'Gainage'],
        howTo:
          'Allongé sur le dos, bras tendus vers le plafond et genoux fléchis au-dessus des hanches (90°). Garde le bas du dos plaqué au sol et descends lentement le bras droit derrière la tête et la jambe gauche vers le sol, sans creuser les lombaires. Reviens et alterne. Renforce le transverse sans charger les disques.',
      },
      {
        name: 'Pont fessier',
        sets: 3, repsMin: 12, repsMax: 15, startWeightKg: 0,
        icon: 'bridge',
        muscles: ['Fessiers', 'Lombaires', 'Ischio-jambiers'],
        howTo:
          'Allongé sur le dos, genoux fléchis, pieds à plat largeur de bassin, bras le long du corps. Décolle le bassin en serrant les fessiers jusqu’à aligner épaules-hanches-genoux, sans cambrer. Tiens une seconde en haut puis redescends en contrôlant. Renforce fessiers et lombaires, soulage le bas du dos.',
      },
      {
        name: 'McGill curl-up',
        sets: 3, repsMin: 6, repsMax: 8, startWeightKg: 0,
        icon: 'human',
        muscles: ['Abdominaux', 'Transverse', 'Gainage'],
        howTo:
          'Allongé sur le dos, une jambe pliée pied au sol et l’autre tendue. Place tes mains sous le bas du dos pour garder sa courbure naturelle. Décolle juste la tête et les épaules de quelques centimètres sans plier la colonne, menton rentré, puis redescends doucement. Gainage abdo qui protège les disques.',
      },
      {
        name: 'Superman (extension lombaire)',
        sets: 2, repsMin: 8, repsMax: 10, startWeightKg: 0,
        icon: 'arrow-up-bold',
        muscles: ['Lombaires', 'Fessiers', 'Dos'],
        howTo:
          'Allongé sur le ventre, bras tendus devant. Décolle légèrement bras, poitrine et jambes du sol en gardant le regard vers le bas et la nuque longue, sur une amplitude réduite. Tiens brièvement puis relâche. À n’introduire que si tu n’as aucune douleur.',
      },
    ],
  },
  {
    id: 'cervicales',
    name: 'Cervicales / nuque',
    day: 'Posture assise',
    exercises: [
      {
        name: 'Rétractions cervicales (chin tucks)',
        sets: 3, repsMin: 10, repsMax: 10, startWeightKg: 0,
        icon: 'head-outline',
        muscles: ['Fléchisseurs profonds du cou', 'Cervicales'],
        howTo:
          'Assis ou debout, regard à l’horizontale. Recule le menton en glissant la tête vers l’arrière (comme pour faire un double menton), sans baisser ni lever le regard. Tiens 2-3 s puis relâche. Corrige la tête en avant — à faire plusieurs fois par jour.',
      },
      {
        name: 'Étirement trapèze supérieur',
        sets: 2, repsMin: 30, repsMax: 30, startWeightKg: 0, timed: true, perSideLabel: 'côté',
        icon: 'human-handsdown',
        muscles: ['Trapèze supérieur', 'Cervicales'],
        howTo:
          'Assis, une main sous la fesse pour abaisser l’épaule. Incline doucement la tête vers le côté opposé (oreille vers l’épaule), en t’aidant légèrement de l’autre main. Étirement doux côté douloureux, sans à-coup. Change de côté.',
      },
      {
        name: 'Étirement scalènes / SCM',
        sets: 2, repsMin: 20, repsMax: 30, startWeightKg: 0, timed: true, perSideLabel: 'côté',
        icon: 'human-handsdown',
        muscles: ['Scalènes', 'Sterno-cléido-mastoïdien'],
        howTo:
          'Assis, épaule basse. Incline la tête sur le côté puis tourne légèrement le menton vers le haut et l’arrière jusqu’à sentir l’étirement sur l’avant-côté du cou. Reste doux et respire. Change de côté.',
      },
      {
        name: 'Rétractions scapulaires',
        sets: 3, repsMin: 12, repsMax: 15, startWeightKg: 0,
        icon: 'arrow-collapse-horizontal',
        muscles: ['Rhomboïdes', 'Trapèzes moyens', 'Deltoïdes postérieurs'],
        howTo:
          'Debout ou assis, bras le long du corps (ou tenant un élastique devant toi). Serre les omoplates l’une vers l’autre vers le bas, sans hausser les épaules ni cambrer. Tiens 2 s puis relâche. Ouvre les épaules, contre la position assise fermée.',
      },
    ],
  },
];

// Planning hebdomadaire (télétravail lun/mar/ven). Mardi et vendredi pour la
// muscu (3-4 jours d'écart), vélo le lundi en récup active. Index 0 = lundi.
export type PlannedSession =
  | { kind: 'velo'; label: string }
  | { kind: 'muscu'; label: string; templateId: WorkoutTemplate['id'] }
  | { kind: 'repos' };

/** Planning par défaut, utilisé tant que l'utilisateur n'en a pas défini un. */
export const DEFAULT_WEEK_PLAN: PlannedSession[] = [
  { kind: 'velo', label: 'Vélo 1h' },
  { kind: 'muscu', label: 'Full-body A', templateId: 'fullbody-a' },
  { kind: 'repos' },
  { kind: 'repos' },
  { kind: 'muscu', label: 'Full-body B', templateId: 'fullbody-b' },
  { kind: 'repos' },
  { kind: 'repos' },
];

/** Conservé pour compat ascendante (export brut et anciens appels). */
export const WEEK_PLAN: PlannedSession[] = DEFAULT_WEEK_PLAN;

const WEEK_PLAN_KEY = 'week_plan';

/** Vrai si `id` correspond à un template muscu connu (les 4 programmes). */
function isKnownTemplateId(id: unknown): id is WorkoutTemplate['id'] {
  return typeof id === 'string' && TEMPLATES.some((t) => t.id === id);
}

/**
 * Valide une valeur lue depuis les réglages : 7 entrées, chacune bien formée.
 * Pour la muscu, on accepte n'importe quel template connu (pas seulement
 * full-body A/B) afin qu'un plan restauré depuis une sauvegarde ne soit pas
 * silencieusement rejeté. Exportée pour les tests.
 */
export function isValidWeekPlan(value: unknown): value is PlannedSession[] {
  if (!Array.isArray(value) || value.length !== 7) return false;
  return value.every((entry) => {
    if (!entry || typeof entry !== 'object') return false;
    const e = entry as { kind?: unknown; label?: unknown; templateId?: unknown };
    if (e.kind === 'repos') return true;
    if (e.kind === 'velo') return typeof e.label === 'string';
    if (e.kind === 'muscu') {
      return typeof e.label === 'string' && isKnownTemplateId(e.templateId);
    }
    return false;
  });
}

/** Plan effectif : plan personnalisé si défini, sinon `DEFAULT_WEEK_PLAN`. */
export async function getEffectiveWeekPlan(): Promise<PlannedSession[]> {
  const raw = await getSetting(WEEK_PLAN_KEY);
  if (!raw) return DEFAULT_WEEK_PLAN;
  try {
    const parsed = JSON.parse(raw);
    if (isValidWeekPlan(parsed)) return parsed;
  } catch {
    // ignore : valeur corrompue, on retombe sur le défaut
  }
  return DEFAULT_WEEK_PLAN;
}

/** Persiste un plan personnalisé (7 entrées attendues). */
export async function saveCustomWeekPlan(plan: PlannedSession[]): Promise<void> {
  await setSetting(WEEK_PLAN_KEY, JSON.stringify(plan));
}

/** Efface le plan personnalisé : on retombe sur `DEFAULT_WEEK_PLAN`. */
export async function resetCustomWeekPlan(): Promise<void> {
  await deleteSetting(WEEK_PLAN_KEY);
}

/** Séance prévue pour un jour `Date.getDay()` (0 = dimanche). */
export function planForDay(
  jsDay: number,
  plan: PlannedSession[] = DEFAULT_WEEK_PLAN,
): PlannedSession {
  return plan[(jsDay + 6) % 7]; // bascule vers lundi = 0
}

/** Retrouve un template par son id (pour le pré-chargement depuis l'accueil). */
export function templateById(id: string | undefined): WorkoutTemplate | undefined {
  return TEMPLATES.find((t) => t.id === id);
}

/**
 * Retrouve la fiche d'un exercice du programme par son nom (illustration,
 * muscles, exécution). Utilisé par la page de progression pour enrichir le
 * détail d'un exercice issu d'une séance enregistrée.
 */
export function exerciseByName(name: string | undefined): TemplateExercise | undefined {
  if (!name) return undefined;
  for (const t of TEMPLATES) {
    const found = t.exercises.find((e) => e.name === name);
    if (found) return found;
  }
  return undefined;
}

/** Indice de cible affiché sous le nom de l'exercice, ex. « 3 × 8-12 / bras ». */
export function targetHint(ex: TemplateExercise): string {
  const reps = ex.repsMin === ex.repsMax ? `${ex.repsMin}` : `${ex.repsMin}-${ex.repsMax}`;
  const unit = ex.timed ? ' s' : '';
  const side = ex.perSideLabel ? ` / ${ex.perSideLabel}` : '';
  return `${ex.sets} × ${reps}${unit}${side}`;
}

/** Valeur de départ pré-remplie pour les reps (milieu de la fourchette). */
export function defaultReps(ex: TemplateExercise): number {
  return Math.round((ex.repsMin + ex.repsMax) / 2);
}
