// Tests pour les calculs géographiques (haversine + décimation).
import { decimateByDistance, haversineMeters } from '@/lib/geo';

describe('haversineMeters', () => {
  it('distance d’un point à lui-même = 0', () => {
    const p = { lat: 48.8566, lon: 2.3522 };
    expect(haversineMeters(p, p)).toBe(0);
  });

  it('symétrique entre A et B', () => {
    const a = { lat: 48.8566, lon: 2.3522 }; // Paris
    const b = { lat: 45.7640, lon: 4.8357 }; // Lyon
    expect(haversineMeters(a, b)).toBeCloseTo(haversineMeters(b, a), 6);
  });

  it('Paris → Lyon ≈ 392 km (± 5 km)', () => {
    const paris = { lat: 48.8566, lon: 2.3522 };
    const lyon = { lat: 45.764, lon: 4.8357 };
    const d = haversineMeters(paris, lyon);
    expect(d).toBeGreaterThan(387_000);
    expect(d).toBeLessThan(397_000);
  });

  it('un degré de latitude à l’équateur ≈ 111 km', () => {
    const a = { lat: 0, lon: 0 };
    const b = { lat: 1, lon: 0 };
    const d = haversineMeters(a, b);
    expect(d).toBeGreaterThan(110_000);
    expect(d).toBeLessThan(112_000);
  });

  it('résultat strictement positif pour deux points distincts', () => {
    const a = { lat: 10, lon: 10 };
    const b = { lat: 10.0001, lon: 10.0001 };
    expect(haversineMeters(a, b)).toBeGreaterThan(0);
  });
});

describe('decimateByDistance', () => {
  it('renvoie tel quel si ≤ 2 points', () => {
    const pts = [{ lat: 0, lon: 0 }];
    expect(decimateByDistance(pts, 100)).toEqual(pts);
    const pts2 = [
      { lat: 0, lon: 0 },
      { lat: 1, lon: 0 },
    ];
    expect(decimateByDistance(pts2, 100)).toEqual(pts2);
  });

  it('préserve toujours le premier et le dernier point', () => {
    const pts = [
      { lat: 0, lon: 0 },
      { lat: 0.000_001, lon: 0 },
      { lat: 0.000_002, lon: 0 },
      { lat: 0.000_003, lon: 0 },
    ];
    const out = decimateByDistance(pts, 10_000);
    expect(out[0]).toEqual(pts[0]);
    expect(out[out.length - 1]).toEqual(pts[pts.length - 1]);
  });

  it('élimine les points trop proches selon le seuil', () => {
    // Quatre points à 1 mètre d’écart : on garde le 1er et le dernier.
    const pts = [
      { lat: 0, lon: 0 },
      { lat: 0.000_009, lon: 0 }, // ~1 m
      { lat: 0.000_018, lon: 0 }, // ~2 m
      { lat: 0.000_027, lon: 0 }, // ~3 m
    ];
    const out = decimateByDistance(pts, 100);
    expect(out).toHaveLength(2);
  });

  it('conserve les points suffisamment éloignés', () => {
    // Trois points séparés de ~111 m : avec un seuil de 50 m, tous sont gardés.
    const pts = [
      { lat: 0, lon: 0 },
      { lat: 0.001, lon: 0 },
      { lat: 0.002, lon: 0 },
    ];
    const out = decimateByDistance(pts, 50);
    expect(out).toHaveLength(3);
  });

  it('préserve les métadonnées des points (générique)', () => {
    type P = { lat: number; lon: number; ts: number };
    const pts: P[] = [
      { lat: 0, lon: 0, ts: 1 },
      { lat: 1, lon: 0, ts: 2 },
      { lat: 2, lon: 0, ts: 3 },
    ];
    const out = decimateByDistance(pts, 10);
    expect(out[0].ts).toBe(1);
    expect(out[out.length - 1].ts).toBe(3);
  });
});
