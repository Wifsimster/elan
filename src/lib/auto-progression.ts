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

export type ProgressKind = 'load' | 'time';

export const LOAD_STEP_KG = 2.5;
export const TIME_STEP_SEC = 5;
export const LOAD_CEILING_FACTOR = 3;
export const TIME_CEILING_FACTOR = 1.5;

const CONFIG_KEY = 'auto_progression';
const STATE_KEY = 'auto_progression_state';

export type TargetResult = {
  value: number;
  base: number;
  changed: boolean;
  direction: 'up' | 'down' | 'none';
};

export function nextTarget(opts: {
  kind: ProgressKind;
  recent: (Difficulty | null | undefined)[];
  base: number;
  floor: number;
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
    if (opts.base >= opts.ceil) return none();
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

export type ExerciseTarget = {
  weightKg: number;
  reps: number;
  lastWeightKg?: number;
  bump: number;
  bumpKind?: ProgressKind;
};

export function targetForExercise(
  ex: TemplateExercise,
  history: ExercisePoint[],
  enabled: boolean,
): ExerciseTarget {
  const last = history.length ? history[history.length - 1] : undefined;
  const recent = history.map((h) => h.difficulty);
  const timed = ex.timed === true;
  const lastWeightKg = timed ? undefined : last?.maxWeightKg;

  const baseWeight = timed ? ex.startWeightKg : last?.maxWeightKg ?? ex.startWeightKg;
  if (!enabled || !ex.autoProgress || !last) {
    return { weightKg: baseWeight, reps: defaultReps(ex), lastWeightKg, bump: 0 };
  }

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
    const reps = res.direction === 'up' ? ex.repsMin : defaultReps(ex);
    return {
      weightKg: res.value,
      reps,
      lastWeightKg: last.maxWeightKg,
      bump: res.changed ? res.value - res.base : 0,
      bumpKind: res.changed ? 'load' : undefined,
    };
  }

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

export type ProgressionChange = {
  exercise: string;
  kind: ProgressKind;
  from: number;
  to: number;
  direction: 'up' | 'down';
};

const fmtKg = (v: number): string =>
  Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ',');

export function describeChange(c: ProgressionChange): string {
  if (c.kind === 'load') return `${c.exercise} ${fmtKg(c.from)} → ${fmtKg(c.to)} kg`;
  return `${c.exercise} ${c.from} → ${c.to} s`;
}

export function changeSummaryLine(changes: ProgressionChange[]): string {
  const ups = changes.filter((c) => c.direction === 'up').length;
  const downs = changes.filter((c) => c.direction === 'down').length;
  const parts: string[] = [];
  if (ups > 0) parts.push(`${ups} exercice${ups > 1 ? 's' : ''} renforcé${ups > 1 ? 's' : ''}`);
  if (downs > 0) parts.push(`${downs} allégé${downs > 1 ? 's' : ''}`);
  return parts.join(' · ');
}

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

export function isoWeekKey(d: Date): string {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((t.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
  return `${t.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

export type AutoProgressionConfig = { enabled: boolean };

export const DEFAULT_AUTO_PROGRESSION_CONFIG: AutoProgressionConfig = { enabled: true };

export async function getAutoProgressionConfig(): Promise<AutoProgressionConfig> {
  const raw = await getSetting(CONFIG_KEY);
  if (!raw) return DEFAULT_AUTO_PROGRESSION_CONFIG;
  try {
    const p = JSON.parse(raw);
    if (p && typeof p === 'object' && typeof p.enabled === 'boolean') return { enabled: p.enabled };
  } catch {
  }
  return DEFAULT_AUTO_PROGRESSION_CONFIG;
}

export async function setAutoProgressionConfig(cfg: AutoProgressionConfig): Promise<void> {
  await setSetting(CONFIG_KEY, JSON.stringify(cfg));
}

export async function saveAutoProgressionEnabled(enabled: boolean): Promise<void> {
  await setAutoProgressionConfig({ enabled });
}

export type AutoProgressionState = {
  week: string;
  changes: ProgressionChange[];
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
  }
  return EMPTY_STATE;
}

export async function setAutoProgressionState(state: AutoProgressionState): Promise<void> {
  await setSetting(STATE_KEY, JSON.stringify(state));
}

export async function dismissProgressionBanner(): Promise<void> {
  const state = await getAutoProgressionState();
  await setAutoProgressionState({ ...state, dismissed: true });
}

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

export async function runWeeklyProgressionIfDue(now: number): Promise<ProgressionChange[]> {
  const cfg = await getAutoProgressionConfig();
  if (!cfg.enabled) return [];

  const week = isoWeekKey(new Date(now));
  const state = await getAutoProgressionState();
  if (state.week === week) return state.changes;

  if (state.week === '') {
    await setAutoProgressionState({ week, changes: [], dismissed: true });
    return [];
  }

  const changes = await computeWeeklyChanges();
  await setAutoProgressionState({ week, changes, dismissed: changes.length === 0 });
  if (changes.length > 0) {
    const { title, body } = notificationContent(changes);
    await notifyProgression(title, body);
  }
  return changes;
}
