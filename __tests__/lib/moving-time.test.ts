import { movingTimeSec, type TimedPoint } from '@/lib/moving-time';

function pt(s: number, speedKmh: number | null = null, lat = 48, lon = 2): TimedPoint {
  return { ts: s * 1000, lat, lon, speedKmh };
}

describe('movingTimeSec', () => {
  it('renvoie 0 en deçà de deux points', () => {
    expect(movingTimeSec([])).toBe(0);
    expect(movingTimeSec([pt(0, 20)])).toBe(0);
  });

  it('cumule la durée des segments en mouvement (vitesse Doppler)', () => {
    const pts = [pt(0, 20), pt(1, 20), pt(2, 20), pt(3, 20)];
    expect(movingTimeSec(pts)).toBe(3);
  });

  it('exclut les segments à l’arrêt (vitesse nulle)', () => {
    const pts = [pt(0, 0), pt(1, 0), pt(2, 20), pt(3, 20)];
    expect(movingTimeSec(pts)).toBe(2);
  });

  it('coupe le temps oublié à l’arrêt (chrono laissé tourner)', () => {
    const moving = [pt(0, 22), pt(2, 22), pt(4, 22), pt(6, 22), pt(8, 22), pt(10, 22)];
    const idle: TimedPoint[] = [];
    for (let s = 12; s <= 3600; s += 2) idle.push(pt(s, 0));
    const total = movingTimeSec([...moving, ...idle]);
    expect(total).toBeGreaterThan(0);
    expect(total).toBeLessThanOrEqual(12);
  });

  it('plafonne un intervalle anormalement long (perte de signal)', () => {
    const pts = [pt(0, 20), pt(100, 20)];
    expect(movingTimeSec(pts)).toBe(30);
  });

  it('retombe sur la vitesse implicite quand la vitesse Doppler manque', () => {
    const moved = [pt(0, null), pt(10, null, 48.000449, 2)];
    expect(movingTimeSec(moved)).toBe(10);

    const drift = [pt(0, null), pt(10, null, 48.000018, 2)];
    expect(movingTimeSec(drift)).toBe(0);
  });
});
