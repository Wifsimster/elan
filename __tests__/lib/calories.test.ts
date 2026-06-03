// Tests pour l'estimation calories (méthode MET) et les zones cardio.
import { ZONE_LABELS, estimateCalories, heartRateZone } from '@/lib/calories';

describe('estimateCalories', () => {
  it('musculation : MUSCU_MET=4,5, 70 kg, 1 h → 315 kcal', () => {
    const kcal = estimateCalories({
      type: 'muscu',
      weightKg: 70,
      durationSec: 3600,
    });
    expect(kcal).toBeCloseTo(4.5 * 70, 5);
  });

  it('vélo à vitesse par défaut (18 km/h, interpolé entre 16/19), 70 kg, 1 h', () => {
    // Interpolation linéaire : 4,0 + (18-16)/(19-16) * (6,8-4,0) = 5,866…
    const kcal = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
    });
    expect(kcal).toBeCloseTo(5.8667 * 70, 1);
  });

  it('vélo très lent (10 km/h ou moins) plafonné au MET de base (3,5)', () => {
    const kcal = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 10,
    });
    expect(kcal).toBeCloseTo(3.5 * 70, 5);
  });

  it('vélo très rapide (≥ 33 km/h) plafonné au MET max (15,8)', () => {
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

  it('points exacts de la table MET vélo', () => {
    // 16 km/h pile → 4,0 MET (point d'ancrage)
    const a = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 16,
    });
    expect(a).toBeCloseTo(4.0 * 70, 5);
    // 22,5 km/h pile → 8,0 MET (point d'ancrage)
    const b = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22.5,
    });
    expect(b).toBeCloseTo(8.0 * 70, 5);
  });

  it('bonus dénivelé positif sur le vélo (~0,77 kcal/m pour 70 kg)', () => {
    const sans = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22.5,
    });
    const avec = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22.5,
      elevationGainM: 200,
    });
    // 70 * 0,011 * 200 = 154 kcal de bonus
    expect(avec - sans).toBeCloseTo(154, 5);
  });

  it('le dénivelé est ignoré pour la musculation', () => {
    const sans = estimateCalories({ type: 'muscu', weightKg: 70, durationSec: 3600 });
    const avec = estimateCalories({
      type: 'muscu',
      weightKg: 70,
      durationSec: 3600,
      elevationGainM: 500,
    });
    expect(avec).toBe(sans);
  });

  it('FC moyenne fournie → mélange MET / cardio (60 % cardio)', () => {
    const sansHr = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22,
    });
    const avecHr = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22,
      avgHr: 145,
      maxHr: 190,
    });
    // Valeurs différentes (cardio à 76 % FCmax tire vers le bas).
    expect(avecHr).not.toBeCloseTo(sansHr, 0);
  });

  it('exemple bilan : 1 h à 22 km/h, 200 m D+, FC 145, 70 kg, FCmax 190 → ~500 kcal', () => {
    const kcal = estimateCalories({
      type: 'velo',
      weightKg: 70,
      durationSec: 3600,
      avgSpeedKmh: 22,
      elevationGainM: 200,
      avgHr: 145,
      maxHr: 190,
    });
    // Cible ~504 kcal d'après l'exemple documenté.
    expect(kcal).toBeGreaterThan(480);
    expect(kcal).toBeLessThan(530);
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
