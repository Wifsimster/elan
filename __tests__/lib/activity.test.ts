// Tests des métadonnées d'activité (lib/activity.ts) : la table qui décide
// partout ailleurs si une séance a un tracé GPS et si l'effort se lit en allure.
import {
  ACTIVITY_META,
  ACTIVITY_TYPES,
  isGpsActivity,
  toActivityType,
  usesPace,
} from '@/lib/activity';
import type { ActivityType } from '@/lib/types';

describe('ACTIVITY_META', () => {
  it('couvre exactement les types listés, sans doublon', () => {
    expect([...ACTIVITY_TYPES].sort()).toEqual(['course', 'marche', 'muscu', 'velo']);
    expect(new Set(ACTIVITY_TYPES).size).toBe(ACTIVITY_TYPES.length);
    expect(Object.keys(ACTIVITY_META).sort()).toEqual([...ACTIVITY_TYPES].sort());
  });

  it('donne à chaque activité un libellé, un libellé court, une icône et une teinte', () => {
    for (const type of ACTIVITY_TYPES) {
      const meta = ACTIVITY_META[type];
      expect(meta.label.length).toBeGreaterThan(0);
      expect(meta.shortLabel.length).toBeGreaterThan(0);
      expect(meta.icon.length).toBeGreaterThan(0);
      expect(meta.colorKey.length).toBeGreaterThan(0);
    }
  });

  it('donne une teinte distincte à chaque activité', () => {
    const keys = ACTIVITY_TYPES.map((t) => ACTIVITY_META[t].colorKey);
    expect(new Set(keys).size).toBe(keys.length);
  });
});

describe('isGpsActivity', () => {
  it('vrai pour le vélo, la course et la marche ; faux en musculation', () => {
    expect(isGpsActivity('velo')).toBe(true);
    expect(isGpsActivity('course')).toBe(true);
    expect(isGpsActivity('marche')).toBe(true);
    expect(isGpsActivity('muscu')).toBe(false);
  });
});

describe('usesPace', () => {
  it('allure à pied, vitesse à vélo', () => {
    expect(usesPace('course')).toBe(true);
    expect(usesPace('marche')).toBe(true);
    expect(usesPace('velo')).toBe(false);
    expect(usesPace('muscu')).toBe(false);
  });

  it('ne concerne que des activités tracées', () => {
    for (const type of ACTIVITY_TYPES) {
      if (usesPace(type)) expect(isGpsActivity(type)).toBe(true);
    }
  });
});

describe('toActivityType', () => {
  it('accepte les types connus', () => {
    for (const type of ACTIVITY_TYPES) expect(toActivityType(type)).toBe(type);
  });

  it('retombe sur le vélo pour une valeur inconnue ou absente', () => {
    expect(toActivityType(undefined)).toBe('velo');
    expect(toActivityType('natation')).toBe('velo');
    expect(toActivityType(42)).toBe('velo');
    // Une clé héritée d'Object.prototype ne doit pas passer pour un type.
    expect(toActivityType('toString')).toBe('velo');
  });

  it('honore le repli explicite', () => {
    const fallback: ActivityType = 'course';
    expect(toActivityType('inconnu', fallback)).toBe('course');
  });
});
