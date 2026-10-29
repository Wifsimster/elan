import { getSetting, setSetting, statsBetween, tonnageBetween } from '@/lib/db';
import { nowMs } from '@/lib/time';
import { startOfWeekMs } from '@/lib/week';

export type GoalMetric = 'sessions' | 'distance' | 'tonnage';
export type GoalPeriod = 'week' | 'month';
export type GoalActivity = 'all' | 'velo' | 'course' | 'marche' | 'muscu';

export type Goal = {
  id: string;
  metric: GoalMetric;
  period: GoalPeriod;
  target: number;
  activity: GoalActivity;
};

const SETTING_KEY = 'goals';
const WEEK_MS = 7 * 86_400_000;

const METRICS: GoalMetric[] = ['sessions', 'distance', 'tonnage'];
const PERIODS: GoalPeriod[] = ['week', 'month'];
const ACTIVITIES: GoalActivity[] = ['all', 'velo', 'course', 'marche', 'muscu'];

export function periodRange(period: GoalPeriod, now: number): { fromMs: number; toMs: number } {
  if (period === 'week') {
    const fromMs = startOfWeekMs(now);
    const toMs = startOfWeekMs(fromMs + WEEK_MS + 43_200_000);
    return { fromMs, toMs };
  }
  const d = new Date(now);
  const fromMs = new Date(d.getFullYear(), d.getMonth(), 1).getTime();
  const toMs = new Date(d.getFullYear(), d.getMonth() + 1, 1).getTime();
  return { fromMs, toMs };
}

export type GoalProgress = {
  goal: Goal;
  value: number;
  target: number;
  ratio: number;
  done: boolean;
};

export function computeProgress(goal: Goal, value: number): GoalProgress {
  const target = goal.target;
  const ratio = target > 0 ? Math.min(1, Math.max(0, value / target)) : 0;
  return { goal, value, target, ratio, done: target > 0 && value >= target };
}

const PERIOD_LABEL: Record<GoalPeriod, string> = { week: 'semaine', month: 'mois' };
const ACTIVITY_NOUN: Record<GoalActivity, string> = {
  all: 'séances',
  velo: 'sorties vélo',
  course: 'sorties course à pied',
  marche: 'sorties marche',
  muscu: 'séances muscu',
};

export function describeGoal(goal: Goal): string {
  const per = PERIOD_LABEL[goal.period];
  if (goal.metric === 'distance') return `${goal.target} km / ${per}`;
  if (goal.metric === 'tonnage') return `${goal.target} kg soulevés / ${per}`;
  return `${goal.target} ${ACTIVITY_NOUN[goal.activity]} / ${per}`;
}

export function formatGoalValue(goal: Goal, value: number): string {
  if (goal.metric === 'distance') return `${value} km`;
  if (goal.metric === 'tonnage') return `${Math.round(value)} kg`;
  return String(value);
}

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

export function makeGoal(input: Omit<Goal, 'id'>): Goal {
  return { id: makeId(), ...input };
}

function makeId(): string {
  return `g${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

export async function loadGoals(): Promise<Goal[]> {
  return parseGoals(await getSetting(SETTING_KEY));
}

export async function saveGoals(goals: Goal[]): Promise<void> {
  await setSetting(SETTING_KEY, serializeGoals(goals));
}

export async function measureGoal(goal: Goal, now: number = nowMs()): Promise<number> {
  const { fromMs, toMs } = periodRange(goal.period, now);
  if (goal.metric === 'sessions') {
    const type = goal.activity === 'all' ? undefined : goal.activity;
    const s = await statsBetween(fromMs, toMs, type);
    return s.sessionCount;
  }
  if (goal.metric === 'distance') {
    const s = await statsBetween(fromMs, toMs, goal.activity === 'all' ? undefined : goal.activity);
    return Math.round((s.totalDistanceM / 1000) * 10) / 10;
  }
  return tonnageBetween(fromMs, toMs);
}

export async function goalProgressList(now: number = nowMs()): Promise<GoalProgress[]> {
  const goals = await loadGoals();
  return Promise.all(goals.map(async (g) => computeProgress(g, await measureGoal(g, now))));
}
