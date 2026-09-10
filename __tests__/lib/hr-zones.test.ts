// Tests de la répartition du temps par zone cardiaque (lib/hr-zones.ts).
// FC max de 200 pour des bornes rondes : zones à 0 / 120 / 140 / 160 / 180 bpm.
import { dominantZone, zoneBounds, zoneDistribution } from '@/lib/hr-zones';

const MAX_HR = 200;

/** Série d'échantillons régulièrement espacés (par défaut une mesure par seconde). */
const series = (hrs: (number | null)[], stepMs = 1000) =>
  hrs.map((hr, i) => ({ ts: i * stepMs, hr }));

describe('zoneBounds', () => {
  it('donne cinq zones contiguës, la dernière sans plafond', () => {
    expect(zoneBounds(MAX_HR)).toEqual([
      { zone: 1, minBpm: 0, maxBpm: 119 },
      { zone: 2, minBpm: 120, maxBpm: 139 },
      { zone: 3, minBpm: 140, maxBpm: 159 },
      { zone: 4, minBpm: 160, maxBpm: 179 },
      { zone: 5, minBpm: 180, maxBpm: null },
    ]);
  });
});

describe('zoneDistribution', () => {
  it('renvoie null sans FC max exploitable', () => {
    expect(zoneDistribution(series([130, 130]), 0)).toBeNull();
  });

  it('renvoie null quand il n’y a pas d’intervalle à mesurer', () => {
    expect(zoneDistribution([], MAX_HR)).toBeNull();
    expect(zoneDistribution(series([130]), MAX_HR)).toBeNull();
  });

  it('attribue chaque intervalle à la zone de la mesure qui l’ouvre', () => {
    // 3 s en zone 2 (130 bpm) puis 2 s en zone 4 (165 bpm) ; la dernière mesure
    // n'ouvre aucun intervalle.
    const d = zoneDistribution(series([130, 130, 130, 165, 165, 165]), MAX_HR);
    expect(d).not.toBeNull();
    expect(d!.totalSec).toBe(5);
    expect(d!.slices.map((s) => s.seconds)).toEqual([0, 3, 0, 2, 0]);
    expect(d!.slices[1].ratio).toBeCloseTo(0.6);
    expect(d!.slices[3].ratio).toBeCloseTo(0.4);
  });

  it('somme des parts égale à 1', () => {
    const d = zoneDistribution(series([110, 130, 150, 170, 190, 190]), MAX_HR)!;
    expect(d.slices.reduce((sum, s) => sum + s.ratio, 0)).toBeCloseTo(1);
  });

  it('plafonne les trous de mesure à 30 s', () => {
    // Ceinture muette pendant 5 min : seules 30 s sont comptées, pas 300.
    const d = zoneDistribution([{ ts: 0, hr: 130 }, { ts: 300_000, hr: 130 }], MAX_HR)!;
    expect(d.totalSec).toBe(30);
    expect(d.slices[1].seconds).toBe(30);
  });

  it('ignore un horodatage identique ou en arrière', () => {
    const d = zoneDistribution(
      [{ ts: 5000, hr: 130 }, { ts: 5000, hr: 130 }, { ts: 1000, hr: 130 }, { ts: 7000, hr: 130 }],
      MAX_HR,
    )!;
    // Seul l'intervalle 1000 → 7000 est valide (6 s) : aucun temps négatif compté.
    expect(d.totalSec).toBe(6);
  });

  it('coupe l’intervalle sur un point sans FC au lieu de prolonger la dernière valeur', () => {
    // 130 → (trou sans FC) → 130 : le tronçon sans capteur n'est attribué à personne.
    const d = zoneDistribution(series([130, null, 130, 130]), MAX_HR)!;
    expect(d.totalSec).toBe(1);
    expect(d.slices[1].seconds).toBe(1);
  });

  it('ignore une FC nulle ou négative (contact perdu)', () => {
    const d = zoneDistribution(series([130, 0, 130, 130]), MAX_HR)!;
    expect(d.totalSec).toBe(1);
  });

  it('porte le libellé et les bornes de chaque zone', () => {
    const d = zoneDistribution(series([130, 130]), MAX_HR)!;
    expect(d.slices[0].label).toBe('Récupération');
    expect(d.slices[4]).toMatchObject({ zone: 5, minBpm: 180, maxBpm: null });
  });
});

describe('dominantZone', () => {
  it('renvoie la zone où l’on a passé le plus de temps', () => {
    const d = zoneDistribution(series([130, 130, 130, 165, 165]), MAX_HR)!;
    expect(dominantZone(d).zone).toBe(2);
  });

  it('en cas d’égalité, retient la zone la plus basse', () => {
    const d = zoneDistribution(series([130, 130, 165, 165, 165]), MAX_HR)!;
    expect(d.slices[1].seconds).toBe(d.slices[3].seconds);
    expect(dominantZone(d).zone).toBe(2);
  });
});
