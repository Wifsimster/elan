// Objectifs d'entraînement (100 % local) : « 3 sorties par semaine »,
// « 100 km par mois », « 5000 kg de tonnage par mois »… Les définitions sont
// stockées en JSON dans la table `settings` (clé `goals`) — donc incluses dans
// la sauvegarde S3 et l'export coach sans migration de schéma — et la
// progression est calculée À LA VOLÉE depuis les séances terminées (mêmes
// filtres que les statistiques existantes : `endedAt IS NOT NULL`).
//
// La logique pure (périodes, progression, libellés, parsing, opérations de
// liste) est testée dans __tests__/lib/goals.test.ts ; l'accès base est isolé
// en fin de module.
import { getSetting, setSetting, statsBetween, tonnageBetween } from '@/lib/db';
import { nowMs } from '@/lib/time';
import { startOfWeekMs } from '@/lib/week';

/** Métrique suivie : nombre de séances, distance vélo (km) ou tonnage muscu (kg). */
export type GoalMetric = 'sessions' | 'distance' | 'tonnage';
/** Fenêtre de suivi : semaine (lundi→dimanche) ou mois calendaire. */
export type GoalPeriod = 'week' | 'month';
/** Type d'activité compté (pertinent pour la métrique `sessions` uniquement). */
export type GoalActivity = 'all' | 'velo' | 'muscu';

export type Goal = {
  id: string;
  metric: GoalMetric;
  period: GoalPeriod;
  /** Cible dans l'unité naturelle : séances (n), km (distance), kg (tonnage). */
  target: number;
  /** Restreint le comptage `sessions` à un type ; ignoré pour distance/tonnage. */
  activity: GoalActivity;
};

const SETTING_KEY = 'goals';
const WEEK_MS = 7 * 86_400_000;

const METRICS: GoalMetric[] = ['sessions', 'distance', 'tonnage'];
const PERIODS: GoalPeriod[] = ['week', 'month'];
const ACTIVITIES: GoalActivity[] = ['all', 'velo', 'muscu'];

// ---------------------------------------------------------------------------
// Logique pure (périodes, progression, libellés, parsing, opérations de liste)
// ---------------------------------------------------------------------------

/** Bornes `[fromMs, toMs)` de la période courante contenant `now` (ms epoch). */
export function periodRange(period: GoalPeriod, now: number): { fromMs: number; toMs: number } {
  if (period === 'week') {
    const fromMs = startOfWeekMs(now);
    return { fromMs, toMs: fromMs + WEEK_MS };
  }
  // Mois calendaire local : du 1er du mois au 1er du mois suivant (le
  // dépassement d'index de mois est géré nativement par Date).
  const d = new Date(now);
  const fromMs = new Date(d.getFullYear(), d.getMonth(), 1).getTime();
  const toMs = new Date(d.getFullYear(), d.getMonth() + 1, 1).getTime();
  return { fromMs, toMs };
}

export type GoalProgress = {
  goal: Goal;
  /** Valeur réalisée sur la période, dans l'unité de la cible. */
  value: number;
  target: number;
  /** Avancement borné à [0, 1] (pour une barre/anneau). */
  ratio: number;
  /** Objectif atteint ou dépassé. */
  done: boolean;
};

/** Construit l'avancement d'un objectif à partir de la valeur réalisée. */
export function computeProgress(goal: Goal, value: number): GoalProgress {
  const target = goal.target;
  const ratio = target > 0 ? Math.min(1, Math.max(0, value / target)) : 0;
  return { goal, value, target, ratio, done: target > 0 && value >= target };
}

const PERIOD_LABEL: Record<GoalPeriod, string> = { week: 'semaine', month: 'mois' };
const ACTIVITY_NOUN: Record<GoalActivity, string> = {
  all: 'séances',
  velo: 'sorties vélo',
  muscu: 'séances muscu',
};

/** Libellé court d'un objectif : « 3 sorties vélo / semaine », « 100 km / mois ». */
export function describeGoal(goal: Goal): string {
  const per = PERIOD_LABEL[goal.period];
  if (goal.metric === 'distance') return `${goal.target} km / ${per}`;
  if (goal.metric === 'tonnage') return `${goal.target} kg soulevés / ${per}`;
  return `${goal.target} ${ACTIVITY_NOUN[goal.activity]} / ${per}`;
}

/** Valeur réalisée formatée avec son unité (« 2 », « 42,5 km », « 5000 kg »). */
export function formatGoalValue(goal: Goal, value: number): string {
  if (goal.metric === 'distance') return `${value} km`;
  if (goal.metric === 'tonnage') return `${Math.round(value)} kg`;
  return String(value);
}

/** Valide et normalise une entrée inconnue en `Goal` (ou `null` si invalide). */
function normalizeGoal(input: unknown): Goal | null {
  if (!input || typeof input !== 'object') return null;
  const o = input as Record<string, unknown>;
  if (!METRICS.includes(o.metric as GoalMetric)) return null;
  if (!PERIODS.includes(o.period as GoalPeriod)) return null;
  const target = Number(o.target);
  if (!Number.isFinite(target) || target <= 0) return null;
  const activity = ACTIVITIES.includes(o.activity as GoalActivity)
    ? (o.activity as GoalActivity)
    : 'all';
  const id = typeof o.id === 'string' && o.id.length > 0 ? o.id : makeId();
  return { id, metric: o.metric as GoalMetric, period: o.period as GoalPeriod, target, activity };
}

/** Lit une liste d'objectifs depuis la valeur de réglage (robuste : ignore le
 * JSON corrompu ou les entrées invalides plutôt que de planter). */
export function parseGoals(raw: string | null): Goal[] {
  if (!raw) return [];
  let arr: unknown;
  try {
    arr = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(arr)) return [];
  const out: Goal[] = [];
  for (const item of arr) {
    const goal = normalizeGoal(item);
    if (goal) out.push(goal);
  }
  return out;
}

export function serializeGoals(goals: Goal[]): string {
  return JSON.stringify(goals);
}

/** Ajoute un objectif, ou remplace celui de même `id`. */
export function upsertGoal(goals: Goal[], goal: Goal): Goal[] {
  const i = goals.findIndex((g) => g.id === goal.id);
  if (i === -1) return [...goals, goal];
  const next = goals.slice();
  next[i] = goal;
  return next;
}

export function removeGoal(goals: Goal[], id: string): Goal[] {
  return goals.filter((g) => g.id !== id);
}

/** Crée un objectif avec un identifiant frais. */
export function makeGoal(input: Omit<Goal, 'id'>): Goal {
  return { id: makeId(), ...input };
}

function makeId(): string {
  return `g${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

// ---------------------------------------------------------------------------
// Accès base : stockage des définitions + mesure de la progression
// ---------------------------------------------------------------------------

export async function loadGoals(): Promise<Goal[]> {
  return parseGoals(await getSetting(SETTING_KEY));
}

export async function saveGoals(goals: Goal[]): Promise<void> {
  await setSetting(SETTING_KEY, serializeGoals(goals));
}

/** Valeur réalisée d'un objectif sur sa période courante, dans l'unité cible. */
export async function measureGoal(goal: Goal, now: number = nowMs()): Promise<number> {
  const { fromMs, toMs } = periodRange(goal.period, now);
  if (goal.metric === 'sessions') {
    const type = goal.activity === 'all' ? undefined : goal.activity;
    const s = await statsBetween(fromMs, toMs, type);
    return s.sessionCount;
  }
  if (goal.metric === 'distance') {
    const s = await statsBetween(fromMs, toMs, 'velo');
    // km arrondis au dixième, cohérent avec l'affichage des distances.
    return Math.round((s.totalDistanceM / 1000) * 10) / 10;
  }
  return tonnageBetween(fromMs, toMs);
}

/** Avancement de tous les objectifs définis, pour la période courante. */
export async function goalProgressList(now: number = nowMs()): Promise<GoalProgress[]> {
  const goals = await loadGoals();
  return Promise.all(goals.map(async (g) => computeProgress(g, await measureGoal(g, now))));
}
