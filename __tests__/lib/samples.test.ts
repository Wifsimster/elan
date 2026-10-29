import {
  meanMax,
  nearestSample,
  pushDownsampled,
  summarizeCadence,
  summarizeHr,
} from '@/lib/samples';
import type { HrSample } from '@/lib/types';

describe('meanMax', () => {
  it('renvoie null sur une série vide (pas de faux 0)', () => {
    expect(meanMax([])).toEqual({ avg: null, max: null });
  });

  it('arrondit la moyenne et garde le max réel', () => {
    expect(meanMax([100, 101, 102])).toEqual({ avg: 101, max: 102 });
    expect(meanMax([100, 101])).toEqual({ avg: 101, max: 101 });
  });

  it('gère une valeur unique', () => {
    expect(meanMax([142])).toEqual({ avg: 142, max: 142 });
  });
});

describe('summarizeHr', () => {
  it('null/null sans échantillon', () => {
    expect(summarizeHr([])).toEqual({ avgHr: null, maxHr: null });
  });

  it('moyenne et max FC', () => {
    const s: HrSample[] = [
      { ts: 0, hr: 120 },
      { ts: 1000, hr: 130 },
      { ts: 2000, hr: 140 },
    ];
    expect(summarizeHr(s)).toEqual({ avgHr: 130, maxHr: 140 });
  });
});

describe('summarizeCadence', () => {
  it('null/null sans échantillon', () => {
    expect(summarizeCadence([])).toEqual({ avgCadence: null, maxCadence: null });
  });

  it('exclut la roue libre (0) de la moyenne mais pas du max', () => {
    expect(summarizeCadence([0, 90, 0, 100, 0])).toEqual({ avgCadence: 95, maxCadence: 100 });
  });

  it('que des 0 : moyenne null, max null (0 || null)', () => {
    expect(summarizeCadence([0, 0, 0])).toEqual({ avgCadence: null, maxCadence: null });
  });

  it('arrondit la moyenne en mouvement', () => {
    expect(summarizeCadence([80, 81])).toEqual({ avgCadence: 81, maxCadence: 81 });
  });
});

describe('pushDownsampled', () => {
  it('ignore une valeur identique reçue trop tôt (< minGapMs)', () => {
    const buf: HrSample[] = [];
    pushDownsampled(buf, { ts: 0, hr: 120 }, (s) => s.hr);
    pushDownsampled(buf, { ts: 500, hr: 120 }, (s) => s.hr);
    expect(buf).toHaveLength(1);
  });

  it('garde une valeur identique reçue après minGapMs', () => {
    const buf: HrSample[] = [];
    pushDownsampled(buf, { ts: 0, hr: 120 }, (s) => s.hr);
    pushDownsampled(buf, { ts: 1000, hr: 120 }, (s) => s.hr);
    expect(buf).toHaveLength(2);
  });

  it('garde toujours une valeur différente, même rapprochée', () => {
    const buf: HrSample[] = [];
    pushDownsampled(buf, { ts: 0, hr: 120 }, (s) => s.hr);
    pushDownsampled(buf, { ts: 100, hr: 121 }, (s) => s.hr);
    expect(buf).toHaveLength(2);
  });

  it('respecte un minGapMs personnalisé', () => {
    const buf: { ts: number; v: number }[] = [];
    pushDownsampled(buf, { ts: 0, v: 5 }, (s) => s.v, 5000);
    pushDownsampled(buf, { ts: 3000, v: 5 }, (s) => s.v, 5000);
    pushDownsampled(buf, { ts: 6000, v: 5 }, (s) => s.v, 5000);
    expect(buf.map((s) => s.ts)).toEqual([0, 6000]);
  });
});

describe('nearestSample', () => {
  const samples = [
    { ts: 0, hr: 100 },
    { ts: 1000, hr: 110 },
    { ts: 2000, hr: 120 },
    { ts: 5000, hr: 150 },
  ];

  it('renvoie null sans échantillon', () => {
    expect(nearestSample([], 1234, (s: HrSample) => s.hr)).toBeNull();
  });

  it('trouve l’échantillon exact', () => {
    expect(nearestSample(samples, 1000, (s) => s.hr)).toBe(110);
  });

  it('choisit le plus proche entre deux voisins', () => {
    expect(nearestSample(samples, 1400, (s) => s.hr)).toBe(110);
    expect(nearestSample(samples, 1600, (s) => s.hr)).toBe(120);
  });

  it('borne avant le premier et après le dernier', () => {
    expect(nearestSample(samples, -500, (s) => s.hr)).toBe(100);
    expect(nearestSample(samples, 4900, (s) => s.hr)).toBe(150);
  });

  it('renvoie null au-delà de la tolérance', () => {
    expect(nearestSample(samples, 3000, (s) => s.hr)).toBe(120);
    expect(nearestSample(samples, 3000, (s) => s.hr, 500)).toBeNull();
  });
});
