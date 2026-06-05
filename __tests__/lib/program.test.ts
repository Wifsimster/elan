// Tests pour les utilitaires du programme muscu (templates, planning, helpers).
import {
  TEMPLATES,
  WEEK_PLAN,
  defaultReps,
  planForDay,
  targetHint,
  templateById,
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
