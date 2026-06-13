// Tests de la logique pure des objectifs d'entraînement (lib/goals.ts) :
// bornes de période, calcul d'avancement, libellés, parsing robuste et
// opérations de liste. Pur (aucun accès base/React). Dates en heure locale
// pour rester indépendant du fuseau de la CI.
import {
  computeProgress,
  describeGoal,
  formatGoalValue,
  makeGoal,
  parseGoals,
  periodRange,
  removeGoal,
  serializeGoals,
  upsertGoal,
  type Goal,
} from '@/lib/goals';

const goal = (over: Partial<Goal> = {}): Goal => ({
  id: 'g1',
  metric: 'sessions',
  period: 'week',
  target: 3,
  activity: 'all',
  ...over,
});

describe('periodRange', () => {
  it('semaine : lundi 00:00 → lundi suivant (7 jours)', () => {
    const now = new Date(2025, 5, 11, 14, 30).getTime(); // mercredi 11 juin
    const { fromMs, toMs } = periodRange('week', now);
    const from = new Date(fromMs);
    expect(from.getDay()).toBe(1); // lundi
    expect(from.getDate()).toBe(9);
    expect(from.getHours()).toBe(0);
    expect(toMs - fromMs).toBe(7 * 86_400_000);
  });

  it('mois : 1er du mois → 1er du mois suivant', () => {
    const now = new Date(2025, 5, 11, 14, 30).getTime(); // juin
    const { fromMs, toMs } = periodRange('month', now);
    const from = new Date(fromMs);
    const to = new Date(toMs);
    expect(from.getDate()).toBe(1);
    expect(from.getMonth()).toBe(5); // juin
    expect(to.getDate()).toBe(1);
    expect(to.getMonth()).toBe(6); // juillet
  });

  it('mois : décembre déborde sur janvier de l’année suivante', () => {
    const now = new Date(2025, 11, 20).getTime(); // décembre 2025
    const { toMs } = periodRange('month', now);
    const to = new Date(toMs);
    expect(to.getFullYear()).toBe(2026);
    expect(to.getMonth()).toBe(0); // janvier
  });
});

describe('computeProgress', () => {
  it('avancement partiel : ratio borné, non atteint', () => {
    const p = computeProgress(goal({ target: 4 }), 1);
    expect(p.ratio).toBeCloseTo(0.25);
    expect(p.done).toBe(false);
    expect(p.value).toBe(1);
    expect(p.target).toBe(4);
  });

  it('atteint à exactement la cible', () => {
    const p = computeProgress(goal({ target: 3 }), 3);
    expect(p.done).toBe(true);
    expect(p.ratio).toBe(1);
  });

  it('dépassement : ratio plafonné à 1, done vrai', () => {
    const p = computeProgress(goal({ target: 3 }), 5);
    expect(p.ratio).toBe(1);
    expect(p.done).toBe(true);
  });

  it('cible nulle ou absente : ratio 0, jamais atteint (pas de division par zéro)', () => {
    const p = computeProgress(goal({ target: 0 }), 2);
    expect(p.ratio).toBe(0);
    expect(p.done).toBe(false);
  });
});

describe('describeGoal / formatGoalValue', () => {
  it('libellés selon métrique et période', () => {
    expect(describeGoal(goal({ metric: 'sessions', activity: 'velo', target: 3 }))).toBe(
      '3 sorties vélo / semaine',
    );
    expect(describeGoal(goal({ metric: 'sessions', activity: 'muscu', period: 'month', target: 8 }))).toBe(
      '8 séances muscu / mois',
    );
    expect(describeGoal(goal({ metric: 'distance', period: 'month', target: 100 }))).toBe(
      '100 km / mois',
    );
    expect(describeGoal(goal({ metric: 'tonnage', period: 'month', target: 5000 }))).toBe(
      '5000 kg soulevés / mois',
    );
  });

  it('valeur réalisée formatée avec unité', () => {
    expect(formatGoalValue(goal({ metric: 'sessions' }), 2)).toBe('2');
    expect(formatGoalValue(goal({ metric: 'distance' }), 42.5)).toBe('42.5 km');
    expect(formatGoalValue(goal({ metric: 'tonnage' }), 5123.4)).toBe('5123 kg');
  });
});

describe('parseGoals (robuste) / serializeGoals', () => {
  it('valeur absente ou JSON corrompu → liste vide', () => {
    expect(parseGoals(null)).toEqual([]);
    expect(parseGoals('not json')).toEqual([]);
    expect(parseGoals('{"not":"array"}')).toEqual([]);
  });

  it('ignore les entrées invalides et conserve les valides', () => {
    const raw = JSON.stringify([
      { id: 'a', metric: 'sessions', period: 'week', target: 3, activity: 'velo' }, // ok
      { id: 'b', metric: 'bogus', period: 'week', target: 3, activity: 'all' }, // métrique invalide
      { id: 'c', metric: 'distance', period: 'year', target: 50, activity: 'all' }, // période invalide
      { id: 'd', metric: 'tonnage', period: 'month', target: 0, activity: 'all' }, // cible <= 0
      { id: 'e', metric: 'distance', period: 'month', target: 100 }, // activity manquante → 'all'
    ]);
    const goals = parseGoals(raw);
    expect(goals.map((g) => g.id)).toEqual(['a', 'e']);
    expect(goals[1].activity).toBe('all');
  });

  it('round-trip serialize → parse', () => {
    const goals: Goal[] = [
      goal({ id: 'x', metric: 'distance', period: 'month', target: 120, activity: 'all' }),
    ];
    expect(parseGoals(serializeGoals(goals))).toEqual(goals);
  });
});

describe('upsertGoal / removeGoal / makeGoal', () => {
  it('ajoute un nouvel objectif', () => {
    const next = upsertGoal([], goal({ id: 'g1' }));
    expect(next).toHaveLength(1);
  });

  it('remplace l’objectif de même id', () => {
    const next = upsertGoal([goal({ id: 'g1', target: 3 })], goal({ id: 'g1', target: 5 }));
    expect(next).toHaveLength(1);
    expect(next[0].target).toBe(5);
  });

  it('supprime par id', () => {
    const next = removeGoal([goal({ id: 'g1' }), goal({ id: 'g2' })], 'g1');
    expect(next.map((g) => g.id)).toEqual(['g2']);
  });

  it('makeGoal attribue un id unique', () => {
    const a = makeGoal({ metric: 'sessions', period: 'week', target: 3, activity: 'all' });
    const b = makeGoal({ metric: 'sessions', period: 'week', target: 3, activity: 'all' });
    expect(a.id).not.toBe(b.id);
    expect(a.id).toBeTruthy();
  });
});
