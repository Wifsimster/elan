// Tests sanity sur le barème des circonférences de roue.
import { WHEEL_SIZES, matchWheelSize } from '@/lib/wheel-sizes';

describe('WHEEL_SIZES', () => {
  it('contient au moins quelques presets courants', () => {
    expect(WHEEL_SIZES.length).toBeGreaterThanOrEqual(5);
  });

  it('toutes les valeurs sont dans une plage plausible (1800 – 2400 mm)', () => {
    for (const w of WHEEL_SIZES) {
      expect(w.mm).toBeGreaterThan(1800);
      expect(w.mm).toBeLessThan(2400);
    }
  });

  it('chaque preset a un label non vide', () => {
    for (const w of WHEEL_SIZES) {
      expect(w.label.length).toBeGreaterThan(0);
    }
  });

  it('inclut le preset route classique 700×25c (2105 mm)', () => {
    const route = WHEEL_SIZES.find((w) => w.label === '700×25c');
    expect(route).toBeDefined();
    expect(route?.mm).toBe(2105);
  });

  it('les labels sont uniques (pas de doublon dans la liste)', () => {
    const labels = WHEEL_SIZES.map((w) => w.label);
    expect(new Set(labels).size).toBe(labels.length);
  });
});

describe('matchWheelSize', () => {
  it('retrouve un preset par sa circonférence exacte', () => {
    const m = matchWheelSize(2105);
    expect(m?.label).toBe('700×25c');
  });

  it('retourne undefined pour une valeur non listée', () => {
    expect(matchWheelSize(1234)).toBeUndefined();
  });

  it('comparaison stricte (un mm de différence ne matche pas)', () => {
    expect(matchWheelSize(2104)).toBeUndefined();
  });
});
