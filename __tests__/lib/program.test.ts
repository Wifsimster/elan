// Tests pour les utilitaires du programme muscu (templates, planning, helpers).
import {
  DEFAULT_WEEK_PLAN,
  TEMPLATES,
  WEEK_PLAN,
  defaultReps,
  isValidWeekPlan,
  planForDay,
  targetHint,
  templateById,
  type PlannedSession,
  type TemplateExercise,
} from '@/lib/program';

describe('templateById', () => {
  it('retrouve un template existant', () => {
    const t = templateById('fullbody-a');
    expect(t).toBeDefined();
    expect(t?.id).toBe('fullbody-a');
    expect(t?.name).toBe('Full-body A');
  });

  it('retourne undefined pour un id inconnu ou non défini', () => {
    expect(templateById('inexistant')).toBeUndefined();
    expect(templateById(undefined)).toBeUndefined();
  });

  it('chaque template du tableau est retrouvable par son id', () => {
    for (const t of TEMPLATES) {
      expect(templateById(t.id)).toBe(t);
    }
  });
});

describe('planForDay', () => {
  // Convention : 0=dimanche (Date.getDay()), index interne 0=lundi.
  it('lundi (1) → vélo', () => {
    expect(planForDay(1).kind).toBe('velo');
  });

  it('mardi (2) → muscu Full-body A', () => {
    const p = planForDay(2);
    expect(p.kind).toBe('muscu');
    if (p.kind === 'muscu') expect(p.templateId).toBe('fullbody-a');
  });

  it('vendredi (5) → muscu Full-body B', () => {
    const p = planForDay(5);
    expect(p.kind).toBe('muscu');
    if (p.kind === 'muscu') expect(p.templateId).toBe('fullbody-b');
  });

  it('dimanche (0) → repos', () => {
    expect(planForDay(0).kind).toBe('repos');
  });

  it('samedi (6) → repos', () => {
    expect(planForDay(6).kind).toBe('repos');
  });

  it('plan hebdomadaire complet de 7 jours', () => {
    expect(WEEK_PLAN).toHaveLength(7);
  });
});

describe('targetHint', () => {
  const base: TemplateExercise = {
    name: 'X',
    sets: 3,
    repsMin: 8,
    repsMax: 12,
    startWeightKg: 10,
    howTo: '',
    muscles: [],
    icon: 'dumbbell',
  };

  it('plage de reps standard', () => {
    expect(targetHint(base)).toBe('3 × 8-12');
  });

  it('reps fixes (min = max) → valeur unique', () => {
    expect(targetHint({ ...base, repsMin: 10, repsMax: 10 })).toBe('3 × 10');
  });

  it('exercice chronométré ajoute " s"', () => {
    expect(targetHint({ ...base, timed: true, repsMin: 20, repsMax: 40 })).toBe(
      '3 × 20-40 s',
    );
  });

  it('travail unilatéral ajoute le suffixe / côté', () => {
    expect(targetHint({ ...base, perSideLabel: 'bras' })).toBe('3 × 8-12 / bras');
  });

  it('chronométré + unilatéral combinés', () => {
    expect(
      targetHint({
        ...base,
        timed: true,
        repsMin: 15,
        repsMax: 30,
        perSideLabel: 'côté',
      }),
    ).toBe('3 × 15-30 s / côté');
  });
});

describe('isValidWeekPlan', () => {
  it('accepte le planning par défaut', () => {
    expect(isValidWeekPlan(DEFAULT_WEEK_PLAN)).toBe(true);
  });

  it('rejette autre chose qu’un tableau de 7 entrées', () => {
    expect(isValidWeekPlan(null)).toBe(false);
    expect(isValidWeekPlan('repos')).toBe(false);
    expect(isValidWeekPlan([{ kind: 'repos' }])).toBe(false); // trop court
    expect(isValidWeekPlan(Array(8).fill({ kind: 'repos' }))).toBe(false); // trop long
  });

  it('accepte une muscu pour chacun des templates connus', () => {
    for (const t of TEMPLATES) {
      const plan: PlannedSession[] = [
        { kind: 'muscu', label: t.name, templateId: t.id },
        ...Array(6).fill({ kind: 'repos' as const }),
      ];
      expect(isValidWeekPlan(plan)).toBe(true);
    }
  });

  it('rejette une muscu dont le templateId est inconnu', () => {
    const plan = [
      { kind: 'muscu', label: 'X', templateId: 'inexistant' },
      ...Array(6).fill({ kind: 'repos' }),
    ];
    expect(isValidWeekPlan(plan)).toBe(false);
  });

  it('rejette une entrée de type inconnu ou un vélo sans label', () => {
    const unknownKind = [{ kind: 'natation' }, ...Array(6).fill({ kind: 'repos' })];
    expect(isValidWeekPlan(unknownKind)).toBe(false);

    const veloNoLabel = [{ kind: 'velo' }, ...Array(6).fill({ kind: 'repos' })];
    expect(isValidWeekPlan(veloNoLabel)).toBe(false);
  });
});

describe('defaultReps', () => {
  const base: TemplateExercise = {
    name: 'X',
    sets: 3,
    repsMin: 8,
    repsMax: 12,
    startWeightKg: 10,
    howTo: '',
    muscles: [],
    icon: 'dumbbell',
  };

  it('milieu de fourchette, arrondi', () => {
    expect(defaultReps(base)).toBe(10);
  });

  it('plage symétrique impaire arrondie au plus proche', () => {
    expect(defaultReps({ ...base, repsMin: 5, repsMax: 8 })).toBe(7); // 6.5 → 7
  });

  it('min = max → renvoie cette valeur', () => {
    expect(defaultReps({ ...base, repsMin: 10, repsMax: 10 })).toBe(10);
  });
});
