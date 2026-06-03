// Tests pour l'estimation calories (méthode MET) et les zones cardio.
import { ZONE_LABELS, estimateCalories, heartRateZone } from '@/lib/calories';

describe('estimateCalories', () => {
  it('musculation : MET=5, 70 kg, 1 h → 350 kcal', () => {
    const kcal = estimateCalories({
      type: 'muscu',
      weightKg: 70,
      durationSec: 3600,
    });
    expect(kcal).toBeCloseTo(350, 5);
  });

  it('vélo à vitesse par défaut (18 km/h → MET=6), 70 kg, 1 h → 420 kcal', () => {
    const kcal = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
    });
    expect(kcal).toBeCloseTo(420, 5);
  });

  it('vélo lent (< 16 km/h) utilise MET=4', () => {
    const kcal = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 10,
    });
    expect(kcal).toBeCloseTo(4 * 70, 5);
  });

  it('vélo > 30 km/h utilise MET=15,8', () => {
    const kcal = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 35,
    });
    expect(kcal).toBeCloseTo(15.8 * 70, 5);
  });

  it('mise à l’échelle linéaire avec la durée', () => {
    const k1 = estimateCalories({
      type: 'muscu',
      weightKg: 80,
      durationSec: 1800,
    });
    const k2 = estimateCalories({
      type: 'muscu',
      weightKg: 80,
      durationSec: 3600,
    });
    expect(k2).toBeCloseTo(k1 * 2, 5);
  });

  it('durée nulle → 0 kcal', () => {
    expect(
      estimateCalories({ type: 'muscu', weightKg: 70, durationSec: 0 }),
    ).toBe(0);
  });

  it('avgSpeedKmh=null retombe sur la valeur par défaut (18 km/h)', () => {
    const a = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: null,
    });
    const b = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
    });
    expect(a).toBe(b);
  });

  it('limites des paliers MET vélo', () => {
    // 16 km/h pile : pas < 16 → MET=6
    const a = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 16,
    });
    expect(a).toBeCloseTo(6 * 70, 5);
    // 22 km/h pile : pas < 22 → MET=10
    const b = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22,
    });
    expect(b).toBeCloseTo(10 * 70, 5);
  });
});

describe('heartRateZone', () => {
  const max = 180;

  it('zone 1 sous 60 % FCmax', () => {
    expect(heartRateZone(100, max)).toBe(1); // ~56 %
  });

  it('zone 2 entre 60 et 70 %', () => {
    expect(heartRateZone(120, max)).toBe(2); // ~67 %
  });

  it('zone 3 entre 70 et 80 %', () => {
    expect(heartRateZone(135, max)).toBe(3); // 75 %
  });

  it('zone 4 entre 80 et 90 %', () => {
    expect(heartRateZone(155, max)).toBe(4); // ~86 %
  });

  it('zone 5 à 90 % et plus', () => {
    expect(heartRateZone(170, max)).toBe(5); // ~94 %
    expect(heartRateZone(180, max)).toBe(5);
    expect(heartRateZone(200, max)).toBe(5); // dépassement FCmax théorique
  });

  it('frontière 60 % pile → zone 2', () => {
    expect(heartRateZone(108, max)).toBe(2); // 0,6 exact
  });
});

describe('ZONE_LABELS', () => {
  it('définit un libellé pour chacune des 5 zones', () => {
    expect(Object.keys(ZONE_LABELS).sort()).toEqual(['1', '2', '3', '4', '5']);
    expect(ZONE_LABELS[1]).toBe('Récupération');
    expect(ZONE_LABELS[5]).toBe('Maximal');
  });
});
