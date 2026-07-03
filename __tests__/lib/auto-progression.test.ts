// Tests de la progression automatique (lib/auto-progression.ts) : cœur pur
// (nextTarget, targetForExercise, isoWeekKey, descriptions). L'orchestration
// impure (lecture historique / persistance / notif) n'est pas couverte ici.
import {
  changeSummaryLine,
  describeChange,
  isoWeekKey,
  nextTarget,
  notificationContent,
  targetForExercise,
  type ProgressionChange,
} from '@/lib/auto-progression';
import type { ExercisePoint } from '@/lib/db';
import type { TemplateExercise } from '@/lib/program';

// --- Fabriques de test -----------------------------------------------------

const point = (
  overrides: Partial<ExercisePoint> & { difficulty: ExercisePoint['difficulty'] },
): ExercisePoint => ({
  sessionId: 1,
  startedAt: 0,
  maxWeightKg: 20,
  topReps: 10,
  volume: 0,
  sets: 3,
  ...overrides,
});

const loadEx: TemplateExercise = {
  name: 'Goblet squat',
  sets: 3,
  repsMin: 8,
  repsMax: 12,
  startWeightKg: 20,
  autoProgress: 'load',
  icon: 'x',
  muscles: [],
  howTo: '',
};

const timedEx: TemplateExercise = {
  name: 'Gainage planche',
  sets: 3,
  repsMin: 20,
  repsMax: 40,
  startWeightKg: 0,
  timed: true,
  autoProgress: 'time',
  icon: 'x',
  muscles: [],
  howTo: '',
};

const rehabEx: TemplateExercise = {
  name: 'Bird-dog',
  sets: 3,
  repsMin: 8,
  repsMax: 10,
  startWeightKg: 0,
  icon: 'x',
  muscles: [],
  howTo: '',
}; // pas d'autoProgress -> jamais progressé

// --- nextTarget ------------------------------------------------------------

describe('nextTarget', () => {
  it('monte d’un pas (2,5 kg) quand le ressenti conseille d’augmenter', () => {
    const r = nextTarget({ kind: 'load', recent: ['facile', 'facile'], base: 20, floor: 20, ceil: 60 });
    expect(r).toEqual({ value: 22.5, base: 20, changed: true, direction: 'up' });
  });

  it('allège d’un pas quand la dernière séance est « dur »', () => {
    const r = nextTarget({ kind: 'load', recent: ['facile', 'dur'], base: 25, floor: 20, ceil: 60 });
    expect(r).toEqual({ value: 22.5, base: 25, changed: true, direction: 'down' });
  });

  it('maintient sur ressenti « moyen » dominant', () => {
    const r = nextTarget({ kind: 'load', recent: ['moyen', 'moyen'], base: 20, floor: 20, ceil: 60 });
    expect(r.changed).toBe(false);
    expect(r.value).toBe(20);
  });

  it('maintient quand aucun ressenti n’est noté', () => {
    const r = nextTarget({ kind: 'load', recent: [null, undefined], base: 20, floor: 20, ceil: 60 });
    expect(r.changed).toBe(false);
  });

  it('ne dépasse jamais le plafond', () => {
    const r = nextTarget({ kind: 'load', recent: ['facile'], base: 60, floor: 20, ceil: 60 });
    expect(r.changed).toBe(false);
    expect(r.value).toBe(60);
  });

  it('ne descend jamais sous le plancher', () => {
    const r = nextTarget({ kind: 'load', recent: ['dur', 'dur'], base: 20, floor: 20, ceil: 60 });
    expect(r.changed).toBe(false);
    expect(r.value).toBe(20);
  });

  it('progresse le gainage de +5 s', () => {
    const r = nextTarget({ kind: 'time', recent: ['facile'], base: 30, floor: 20, ceil: 60 });
    expect(r).toEqual({ value: 35, base: 30, changed: true, direction: 'up' });
  });
});

// --- targetForExercise -----------------------------------------------------

describe('targetForExercise', () => {
  it('sans historique : charge de départ, aucun bump', () => {
    const t = targetForExercise(loadEx, [], true);
    expect(t.weightKg).toBe(20);
    expect(t.bump).toBe(0);
    expect(t.lastWeightKg).toBeUndefined();
  });

  it('progression désactivée : continuité sur la dernière charge, aucun bump', () => {
    const hist = [point({ maxWeightKg: 24, difficulty: 'facile' })];
    const t = targetForExercise(loadEx, hist, false);
    expect(t.weightKg).toBe(24); // dernière charge, pas de montée
    expect(t.bump).toBe(0);
    expect(t.lastWeightKg).toBe(24);
  });

  it('exercice non progressable (rééducation) : jamais de bump même activé', () => {
    const hist = [point({ maxWeightKg: 0, topReps: 10, difficulty: 'facile' })];
    const t = targetForExercise(rehabEx, hist, true);
    expect(t.bump).toBe(0);
  });

  it('activé + ressenti facile : +2,5 kg et reps repartent en bas de fourchette', () => {
    const hist = [point({ maxWeightKg: 20, topReps: 12, difficulty: 'facile' })];
    const t = targetForExercise(loadEx, hist, true);
    expect(t.weightKg).toBe(22.5);
    expect(t.reps).toBe(loadEx.repsMin); // double progression : reset au bas de la fourchette
    expect(t.bump).toBe(2.5);
    expect(t.bumpKind).toBe('load');
    expect(t.lastWeightKg).toBe(20);
  });

  it('dernière séance non notée : aucune montée (silence ≠ feu vert)', () => {
    // Dernière séance sans ressenti, malgré un « facile » plus ancien.
    const hist = [
      point({ maxWeightKg: 20, topReps: 12, difficulty: 'facile' }),
      point({ maxWeightKg: 20, topReps: 12, difficulty: null }),
    ];
    const t = targetForExercise(loadEx, hist, true);
    expect(t.bump).toBe(0);
    expect(t.weightKg).toBe(20);
  });

  it('activé + dernière « dur » : allègement de 2,5 kg', () => {
    const hist = [point({ maxWeightKg: 25, difficulty: 'dur' })];
    const t = targetForExercise(loadEx, hist, true);
    expect(t.weightKg).toBe(22.5);
    expect(t.bump).toBe(-2.5);
    expect(t.bumpKind).toBe('load');
  });

  it('gainage chronométré : progresse les secondes, pas la charge', () => {
    const hist = [point({ maxWeightKg: 0, topReps: 30, difficulty: 'facile' })];
    const t = targetForExercise(timedEx, hist, true);
    expect(t.weightKg).toBe(0);
    expect(t.reps).toBe(35);
    expect(t.bump).toBe(5);
    expect(t.bumpKind).toBe('time');
  });

  it('gainage : plafonné à repsMax × 1,5', () => {
    const hist = [point({ maxWeightKg: 0, topReps: 60, difficulty: 'facile' })];
    const t = targetForExercise(timedEx, hist, true); // repsMax 40 -> plafond 60
    expect(t.reps).toBe(60);
    expect(t.bump).toBe(0);
  });
});

// --- isoWeekKey ------------------------------------------------------------

describe('isoWeekKey', () => {
  it('deux jours d’une même semaine ISO donnent la même clé', () => {
    // 2026-06-29 (lundi) et 2026-07-03 (vendredi) : même semaine ISO.
    expect(isoWeekKey(new Date(2026, 5, 29))).toBe(isoWeekKey(new Date(2026, 6, 3)));
  });

  it('deux semaines différentes donnent des clés différentes', () => {
    expect(isoWeekKey(new Date(2026, 6, 3))).not.toBe(isoWeekKey(new Date(2026, 6, 10)));
  });

  it('format 4 chiffres - W deux chiffres', () => {
    expect(isoWeekKey(new Date(2026, 0, 5))).toMatch(/^\d{4}-W\d{2}$/);
  });
});

// --- Descriptions ----------------------------------------------------------

describe('descriptions', () => {
  const up: ProgressionChange = { exercise: 'Goblet squat', kind: 'load', from: 20, to: 22.5, direction: 'up' };
  const down: ProgressionChange = { exercise: 'Rowing', kind: 'load', from: 20, to: 17.5, direction: 'down' };
  const timed: ProgressionChange = { exercise: 'Gainage planche', kind: 'time', from: 30, to: 35, direction: 'up' };

  it('describeChange formate charge (virgule décimale) et durée', () => {
    expect(describeChange(up)).toBe('Goblet squat 20 → 22,5 kg');
    expect(describeChange(timed)).toBe('Gainage planche 30 → 35 s');
  });

  it('changeSummaryLine compte montées et allègements', () => {
    expect(changeSummaryLine([up, timed, down])).toBe('2 exercices renforcés · 1 allégé');
    expect(changeSummaryLine([up])).toBe('1 exercice renforcé');
    expect(changeSummaryLine([])).toBe('');
  });

  it('notificationContent choisit le titre selon le sens et liste le détail', () => {
    expect(notificationContent([up]).title).toBe('Ton programme monte d’un cran');
    expect(notificationContent([down]).title).toBe('On lève le pied cette semaine');
    expect(notificationContent([up, down]).title).toBe('Ton programme évolue cette semaine');
    expect(notificationContent([up]).body).toBe('Goblet squat 20 → 22,5 kg.');
  });
});
