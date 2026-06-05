// Tests pour la projection géo → SVG (cadrage + ratio préservé).
import { createProjection } from '@/lib/route-projection';

describe('createProjection', () => {
  const points = [
    { lat: 48.85, lon: 2.34 },
    { lat: 48.86, lon: 2.35 },
    { lat: 48.87, lon: 2.36 },
  ];

  it('renvoie les dimensions transmises', () => {
    const p = createProjection(points, { width: 200, height: 100 });
    expect(p.width).toBe(200);
    expect(p.height).toBe(100);
  });

  it('le coin nord-ouest (maxLat / minLon) tombe dans le viewBox', () => {
    const proj = createProjection(points, { width: 300, height: 200, pad: 20 });
    const [x, y] = proj.project({ lat: 48.87, lon: 2.34 });
    expect(x).toBeGreaterThanOrEqual(0);
    expect(y).toBeGreaterThanOrEqual(0);
    expect(x).toBeLessThanOrEqual(300);
    expect(y).toBeLessThanOrEqual(200);
  });

  it('inverse l’axe Y : le point le plus au nord a un y inférieur au point sud', () => {
    const proj = createProjection(points, { width: 300, height: 200 });
    const [, yNord] = proj.project({ lat: 48.87, lon: 2.35 });
    const [, ySud] = proj.project({ lat: 48.85, lon: 2.35 });
    expect(yNord).toBeLessThan(ySud);
  });

  it('axe X croissant avec la longitude (est → droite)', () => {
    const proj = createProjection(points, { width: 300, height: 200 });
    const [xOuest] = proj.project({ lat: 48.86, lon: 2.34 });
    const [xEst] = proj.project({ lat: 48.86, lon: 2.36 });
    expect(xEst).toBeGreaterThan(xOuest);
  });

  it('tous les points projetés tiennent dans le viewBox (padding respecté)', () => {
    const W = 400;
    const H = 300;
    const pad = 30;
    const proj = createProjection(points, { width: W, height: H, pad });
    for (const pt of points) {
      const [x, y] = proj.project(pt);
      expect(x).toBeGreaterThanOrEqual(pad - 0.01);
      expect(x).toBeLessThanOrEqual(W - pad + 0.01);
      expect(y).toBeGreaterThanOrEqual(pad - 0.01);
      expect(y).toBeLessThanOrEqual(H - pad + 0.01);
    }
  });

  it('tracé dégénéré (un seul point répété) ne lève pas et reste cadré', () => {
    const same = [
      { lat: 48.85, lon: 2.34 },
      { lat: 48.85, lon: 2.34 },
    ];
    const proj = createProjection(same, { width: 100, height: 100 });
    const [x, y] = proj.project(same[0]);
    expect(Number.isFinite(x)).toBe(true);
    expect(Number.isFinite(y)).toBe(true);
  });

  it('projection identique pour deux points identiques (déterminisme)', () => {
    const proj = createProjection(points, { width: 200, height: 200 });
    const a = proj.project({ lat: 48.86, lon: 2.35 });
    const b = proj.project({ lat: 48.86, lon: 2.35 });
    expect(a).toEqual(b);
  });
});
