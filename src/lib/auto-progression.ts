// Progression automatique du programme de musculation, semaine après semaine,
// d'après le ressenti noté (facile / moyen / dur). C'est LA logique qui « relève
// la difficulté » du plan tout seul : on monte la charge des exercices notés
// faciles, on allège ceux notés durs, d'un cran par semaine au maximum.
//
// Principes de conception (issus de la revue croisée data / UX / sécurité) :
//  - 100 % local, aucune dépendance réseau (comme tout `lib/`).
//  - La cible est TOUJOURS calculée à partir de la dernière valeur ENREGISTRÉE
//    (charge/durée réellement loguée), jamais d'un conseil précédent : pas de
//    double-comptage, la boucle s'auto-corrige (si l'utilisateur ignore la montée
//    et re-logue la même charge, on reproposera la même montée — pas de dérive).
//  - Cadencé à un pas par semaine ISO : impossible de s'emballer.
//  - Sécurité : seuls les deux full-body chargés progressent (`autoProgress`
//    posé sur `program.ts`) ; les exercices de rééducation / mobilité (dos,
//    cervicales) ne sont JAMAIS touchés. Un ressenti « dur » déclenche un
//    allègement (deload), un plancher empêche de descendre sous la charge de
//    départ, un plafond empêche de monter à l'infini.
//
// Le module expose des fonctions PURES (testées dans __tests__/lib/) et une
// fine couche d'orchestration qui lit l'historique et persiste l'état.

import {
  exerciseHistory,
  getSetting,
  setSetting,
  type ExercisePoint,
} from '@/lib/db';
import { notifyProgression } from '@/lib/notifications';
import {
  defaultReps,
  getEffectiveWeekPlan,
  templateById,
  type TemplateExercise,
} from '@/lib/program';
import { suggestProgression } from '@/lib/progression-advice';
import type { Difficulty } from '@/lib/types';

/** Nature de la progression : charge (kg) ou gainage chronométré (secondes). */
export type ProgressKind = 'load' | 'time';

/** Pas de montée hebdomadaire : 2,5 kg (grille du stepper de charge de muscu.tsx). */
export const LOAD_STEP_KG = 2.5;
/** Pas d'allongement du gainage : +5 s par semaine. */
export const TIME_STEP_SEC = 5;
/** Plafond de charge : `startWeightKg × 3` (au-delà, montée manuelle uniquement). */
export const LOAD_CEILING_FACTOR = 3;
/** Plafond de durée du gainage : `repsMax × 1,5` (planche 40→60 s, latéral 30→45 s). */
export const TIME_CEILING_FACTOR = 1.5;

const CONFIG_KEY = 'auto_progression';
const STATE_KEY = 'auto_progression_state';

// ---------------------------------------------------------------------------
// Cœur pur : calcul de la prochaine cible
// ---------------------------------------------------------------------------

export type TargetResult = {
  /** Nouvelle valeur cible : charge en kg (`load`) ou secondes (`time`). */
  value: number;
  /** Valeur de base (dernière enregistrée) avant ajustement. */
  base: number;
  /** Vrai si `value` diffère de `base` (montée ou allègement effectif). */
  changed: boolean;
  direction: 'up' | 'down' | 'none';
};

/**
 * Prochaine cible d'un exercice à partir de ses ressentis récents et de sa
 * dernière valeur enregistrée. Fonction pure : le classement du ressenti est
 * délégué à `suggestProgression` (déjà testé), on ne fait ici qu'appliquer un
 * pas borné.
 *
 *  - `augmente` -> +1 pas (plafonné à `ceil`)
 *  - `reduis`   -> −1 pas (planchonné à `floor`) — allègement sur « dur »
 *  - `maintiens`-> inchangé
 */
export function nextTarget(opts: {
  kind: ProgressKind;
  /** Ressentis récents, du plus ancien au plus récent (`null` = non noté). */
  recent: (Difficulty | null | undefined)[];
  /** Dernière valeur enregistrée : charge max (`load`) ou durée (`time`). */
  base: number;
  /** Plancher : jamais en-dessous (charge de départ / repsMin secondes). */
  floor: number;
  /** Plafond : jamais au-dessus (au-delà, on maintient). */
  ceil: number;
  step?: number;
}): TargetResult {
  const step = opts.step ?? (opts.kind === 'load' ? LOAD_STEP_KG : TIME_STEP_SEC);
  const none = (): TargetResult => ({
    value: opts.base,
    base: opts.base,
    changed: false,
    direction: 'none',
  });

  const advice = suggestProgression(opts.recent);

  if (advice === 'augmente') {
    if (opts.base >= opts.ceil) return none(); // plafond atteint : on maintient
    const value = Math.min(opts.ceil, opts.base + step);
    return value > opts.base
      ? { value, base: opts.base, changed: true, direction: 'up' }
      : none();
  }
  if (advice === 'reduis') {
    const value = Math.max(opts.floor, opts.base - step);
    return value < opts.base
      ? { value, base: opts.base, changed: true, direction: 'down' }
      : none();
  }
  return none();
}

// ---------------------------------------------------------------------------
// Cible de pré-remplissage d'un exercice de séance (pur : historique en entrée)
// ---------------------------------------------------------------------------

export type ExerciseTarget = {
  /** Charge pré-remplie (kg). 0 pour les exercices chronométrés / non chargés. */
  weightKg: number;
  /** Reps pré-remplies (ou secondes pour le gainage chronométré). */
  reps: number;
  /** Dernière charge enregistrée (kg) — pour l'indice « dernière fois ». */
  lastWeightKg?: number;
  /** Delta appliqué par la progression auto (kg ou s), 0 si aucun. */
  bump: number;
  /** Nature du delta quand `bump !== 0`. */
  bumpKind?: ProgressKind;
};

/**
 * Cible de pré-remplissage d'un exercice, en tenant compte (ou non) de la
 * progression auto. Fonction PURE : reçoit l'historique de l'exercice (du plus
 * ancien au plus récent, séances terminées) plutôt que d'y accéder.
 *
 * C'est l'unique endroit où le pré-remplissage est décidé : la charge portée par
 * la séance précédente (`lastWeightByExercise`) est remplacée par cette cible,
 * qui EST cette charge plus, au plus, un pas gagné — d'où l'absence de double
 * comptage.
 */
export function targetForExercise(
  ex: TemplateExercise,
  history: ExercisePoint[],
  enabled: boolean,
): ExerciseTarget {
  const last = history.length ? history[history.length - 1] : undefined;
  const recent = history.map((h) => h.difficulty);
  const timed = ex.timed === true;
  const lastWeightKg = timed ? undefined : last?.maxWeightKg;

  // Repli (progression auto désactivée, exercice non progressable, ou aucune
  // séance passée) : continuité simple — dernière charge enregistrée, sinon
  // charge de départ ; reps au milieu de la fourchette. C'est le comportement
  // historique de `loadTemplate`.
  const baseWeight = timed ? ex.startWeightKg : last?.maxWeightKg ?? ex.startWeightKg;
  if (!enabled || !ex.autoProgress || !last) {
    return { weightKg: baseWeight, reps: defaultReps(ex), lastWeightKg, bump: 0 };
  }

  // Sécurité : si la dernière séance n'a pas été notée, on ne progresse pas
  // (le silence n'autorise pas une montée de charge). On garde la continuité.
  if (last.difficulty == null) {
    return timed
      ? { weightKg: 0, reps: last.topReps, lastWeightKg: undefined, bump: 0 }
      : { weightKg: last.maxWeightKg, reps: defaultReps(ex), lastWeightKg: last.maxWeightKg, bump: 0 };
  }

  if (ex.autoProgress === 'load') {
    const res = nextTarget({
      kind: 'load',
      recent,
      base: last.maxWeightKg,
      floor: ex.startWeightKg,
      ceil: ex.startWeightKg * LOAD_CEILING_FACTOR,
    });
    // Double progression : après une montée de charge, on repart en bas de la
    // fourchette de reps ; sinon on garde le milieu habituel.
    const reps = res.direction === 'up' ? ex.repsMin : defaultReps(ex);
    return {
      weightKg: res.value,
      reps,
      lastWeightKg: last.maxWeightKg,
      bump: res.changed ? res.value - res.base : 0,
      bumpKind: res.changed ? 'load' : undefined,
    };
  }

  // autoProgress === 'time' : on progresse la durée (secondes), pas la charge.
  const res = nextTarget({
    kind: 'time',
    recent,
    base: last.topReps,
    floor: ex.repsMin,
    ceil: Math.round(ex.repsMax * TIME_CEILING_FACTOR),
  });
  return {
    weightKg: 0,
    reps: res.value,
    lastWeightKg: undefined,
    bump: res.changed ? res.value - res.base : 0,
    bumpKind: res.changed ? 'time' : undefined,
  };
}

/**
 * Cibles de pré-remplissage pour les exercices d'un programme, en tenant compte
 * de la config (progression auto activée ou non). Lit l'historique de chaque
 * exercice. Utilisé par l'écran muscu au chargement d'un programme : c'est ce
 * qui remplace l'ancien `lastWeightByExercise` et applique la montée au bon
 * endroit, une seule fois.
 */
export async function targetsForExercises(
  exercises: TemplateExercise[],
): Promise<Record<string, ExerciseTarget>> {
  const cfg = await getAutoProgressionConfig();
  const out: Record<string, ExerciseTarget> = {};
  for (const ex of exercises) {
    const history = await exerciseHistory(ex.name);
    out[ex.name] = targetForExercise(ex, history, cfg.enabled);
  }
  return out;
}

// ---------------------------------------------------------------------------
// Description des changements (bannière, notification, revue) — pur
// ---------------------------------------------------------------------------

/** Un ajustement de charge/durée appliqué à un exercice pour la nouvelle semaine. */
export type ProgressionChange = {
  exercise: string;
  kind: ProgressKind;
  /** Valeur avant (dernière enregistrée). */
  from: number;
  /** Valeur après (nouvelle cible). */
  to: number;
  direction: 'up' | 'down';
};

const fmtKg = (v: number): string =>
  Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ',');

/** « Goblet squat 20 → 22,5 kg » / « Gainage planche 30 → 35 s ». */
export function describeChange(c: ProgressionChange): string {
  if (c.kind === 'load') return `${c.exercise} ${fmtKg(c.from)} → ${fmtKg(c.to)} kg`;
  return `${c.exercise} ${c.from} → ${c.to} s`;
}

/** Résumé court : « 3 exercices renforcés · 1 allégé ». '' si aucun changement. */
export function changeSummaryLine(changes: ProgressionChange[]): string {
  const ups = changes.filter((c) => c.direction === 'up').length;
  const downs = changes.filter((c) => c.direction === 'down').length;
  const parts: string[] = [];
  if (ups > 0) parts.push(`${ups} exercice${ups > 1 ? 's' : ''} renforcé${ups > 1 ? 's' : ''}`);
  if (downs > 0) parts.push(`${downs} allégé${downs > 1 ? 's' : ''}`);
  return parts.join(' · ');
}

/** Titre + corps de la notification « programme régénéré ». Pur. */
export function notificationContent(changes: ProgressionChange[]): { title: string; body: string } {
  const hasUp = changes.some((c) => c.direction === 'up');
  const hasDown = changes.some((c) => c.direction === 'down');
  const title =
    hasUp && !hasDown
      ? 'Ton programme monte d’un cran'
      : !hasUp && hasDown
        ? 'On lève le pied cette semaine'
        : 'Ton programme évolue cette semaine';
  const list = changes.slice(0, 3).map(describeChange).join(', ');
  const more = changes.length > 3 ? `, +${changes.length - 3}` : '';
  return { title, body: `${list}${more}.` };
}

// ---------------------------------------------------------------------------
// Semaine ISO (idempotence de l'évaluation hebdomadaire) — pur
// ---------------------------------------------------------------------------

/** Clé de semaine ISO-8601, ex. « 2026-W27 ». Deux dates de la même semaine -> même clé. */
export function isoWeekKey(d: Date): string {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7; // lundi = 1 … dimanche = 7
  t.setUTCDate(t.getUTCDate() + 4 - day); // jeudi de la semaine courante
  const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((t.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
  return `${t.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

// ---------------------------------------------------------------------------
// Config (préférence utilisateur, activée par défaut)
// ---------------------------------------------------------------------------

export type AutoProgressionConfig = { enabled: boolean };

/** Activée PAR DÉFAUT : le plan monte tout seul tant que l'utilisateur ne coupe pas. */
export const DEFAULT_AUTO_PROGRESSION_CONFIG: AutoProgressionConfig = { enabled: true };

export async function getAutoProgressionConfig(): Promise<AutoProgressionConfig> {
  const raw = await getSetting(CONFIG_KEY);
  if (!raw) return DEFAULT_AUTO_PROGRESSION_CONFIG;
  try {
    const p = JSON.parse(raw);
    if (p && typeof p === 'object' && typeof p.enabled === 'boolean') return { enabled: p.enabled };
  } catch {
    // valeur corrompue : on retombe sur le défaut (activé)
  }
  return DEFAULT_AUTO_PROGRESSION_CONFIG;
}

export async function setAutoProgressionConfig(cfg: AutoProgressionConfig): Promise<void> {
  await setSetting(CONFIG_KEY, JSON.stringify(cfg));
}

/** Active / désactive la progression auto (raccourci pour la carte Réglages). */
export async function saveAutoProgressionEnabled(enabled: boolean): Promise<void> {
  await setAutoProgressionConfig({ enabled });
}

// ---------------------------------------------------------------------------
// État d'exécution (semaine évaluée + changements + masquage bannière)
// ---------------------------------------------------------------------------

export type AutoProgressionState = {
  /** Semaine ISO de la dernière évaluation. '' = jamais évalué. */
  week: string;
  /** Changements calculés pour `week` (bannière d'accueil + revue). */
  changes: ProgressionChange[];
  /** Vrai si l'utilisateur a masqué la bannière de cette semaine. */
  dismissed: boolean;
};

const EMPTY_STATE: AutoProgressionState = { week: '', changes: [], dismissed: false };

function isChange(v: unknown): v is ProgressionChange {
  if (!v || typeof v !== 'object') return false;
  const c = v as Record<string, unknown>;
  return (
    typeof c.exercise === 'string' &&
    (c.kind === 'load' || c.kind === 'time') &&
    typeof c.from === 'number' &&
    typeof c.to === 'number' &&
    (c.direction === 'up' || c.direction === 'down')
  );
}

export async function getAutoProgressionState(): Promise<AutoProgressionState> {
  const raw = await getSetting(STATE_KEY);
  if (!raw) return EMPTY_STATE;
  try {
    const p = JSON.parse(raw);
    if (p && typeof p === 'object' && typeof p.week === 'string') {
      const changes = Array.isArray(p.changes) ? p.changes.filter(isChange) : [];
      return { week: p.week, changes, dismissed: p.dismissed === true };
    }
  } catch {
    // valeur corrompue : état vide
  }
  return EMPTY_STATE;
}

export async function setAutoProgressionState(state: AutoProgressionState): Promise<void> {
  await setSetting(STATE_KEY, JSON.stringify(state));
}

/** Masque la bannière de progression de la semaine courante (best-effort). */
export async function dismissProgressionBanner(): Promise<void> {
  const state = await getAutoProgressionState();
  await setAutoProgressionState({ ...state, dismissed: true });
}

// ---------------------------------------------------------------------------
// Orchestration (impur : lit l'historique, persiste, notifie)
// ---------------------------------------------------------------------------

/**
 * Exercices auto-progressables du planning courant, dédupliqués par nom (un même
 * mouvement présent dans plusieurs jours ne compte qu'une fois).
 */
async function planAutoExercises(): Promise<TemplateExercise[]> {
  const plan = await getEffectiveWeekPlan();
  const seen = new Set<string>();
  const out: TemplateExercise[] = [];
  for (const entry of plan) {
    if (entry.kind !== 'muscu') continue;
    const t = templateById(entry.templateId);
    if (!t) continue;
    for (const ex of t.exercises) {
      if (ex.autoProgress && !seen.has(ex.name)) {
        seen.add(ex.name);
        out.push(ex);
      }
    }
  }
  return out;
}

/**
 * Calcule les changements de la nouvelle semaine : pour chaque exercice
 * auto-progressable du planning, compare la dernière valeur enregistrée à la
 * cible. Renvoie uniquement les exercices qui bougent réellement.
 */
export async function computeWeeklyChanges(): Promise<ProgressionChange[]> {
  const exercises = await planAutoExercises();
  const changes: ProgressionChange[] = [];
  for (const ex of exercises) {
    const history = await exerciseHistory(ex.name);
    const target = targetForExercise(ex, history, true);
    if (target.bump === 0 || !target.bumpKind) continue;
    const last = history[history.length - 1];
    const isLoad = target.bumpKind === 'load';
    changes.push({
      exercise: ex.name,
      kind: target.bumpKind,
      from: isLoad ? last.maxWeightKg : last.topReps,
      to: isLoad ? target.weightKg : target.reps,
      direction: target.bump > 0 ? 'up' : 'down',
    });
  }
  return changes;
}

/**
 * Évalue la progression si une nouvelle semaine ISO a commencé (idempotent).
 * À appeler au lancement de l'app et au focus de l'accueil. Renvoie les
 * changements de la semaine (vide si rien, désactivé, ou déjà évalué).
 *
 * Sécurités :
 *  - persiste l'état AVANT de notifier (un échec de notif ne rejoue pas l'annonce) ;
 *  - premier passage silencieux (amorçage) : activer la fonctionnalité ne
 *    déclenche pas une fausse annonce « programme régénéré ».
 */
export async function runWeeklyProgressionIfDue(now: number): Promise<ProgressionChange[]> {
  const cfg = await getAutoProgressionConfig();
  if (!cfg.enabled) return [];

  const week = isoWeekKey(new Date(now));
  const state = await getAutoProgressionState();
  if (state.week === week) return state.changes; // déjà évalué cette semaine

  if (state.week === '') {
    // Amorçage silencieux : on mémorise la semaine sans annoncer.
    await setAutoProgressionState({ week, changes: [], dismissed: true });
    return [];
  }

  const changes = await computeWeeklyChanges();
  await setAutoProgressionState({ week, changes, dismissed: changes.length === 0 });
  if (changes.length > 0) {
    // Notification best-effort : n'affiche rien si la permission n'est pas déjà
    // accordée (on ne la réclame pas — la bannière d'accueil reste le canal sûr).
    const { title, body } = notificationContent(changes);
    await notifyProgression(title, body);
  }
  return changes;
}
