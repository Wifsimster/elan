// Tests du calcul du temps en mouvement (lib/moving-time.ts) : on vérifie qu'un
// arrêt (vitesse nulle / position figée) n'est pas comptabilisé, que le cas du
// chrono oublié à l'arrêt est correctement coupé, et que les trous de tracé sont
// plafonnés. Pur (aucun accès base/React).
import { movingTimeSec, type TimedPoint } from '@/lib/moving-time';

/** Construit un point ; `s` = secondes depuis le départ. */
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
    // Seuls les segments 2→ et 3→ portent une vitesse > seuil.
    expect(movingTimeSec(pts)).toBe(2);
  });

  it('coupe le temps oublié à l’arrêt (chrono laissé tourner)', () => {
    // 10 s de roulage puis ~1 h immobile (vitesse nulle).
    const moving = [pt(0, 22), pt(2, 22), pt(4, 22), pt(6, 22), pt(8, 22), pt(10, 22)];
    const idle: TimedPoint[] = [];
    for (let s = 12; s <= 3600; s += 2) idle.push(pt(s, 0));
    const total = movingTimeSec([...moving, ...idle]);
    // ~10 s de mouvement, très loin de l'heure de temps total.
    expect(total).toBeGreaterThan(0);
    expect(total).toBeLessThanOrEqual(12);
  });

  it('plafonne un intervalle anormalement long (perte de signal)', () => {
    const pts = [pt(0, 20), pt(100, 20)]; // 100 s entre deux fixes
    expect(movingTimeSec(pts)).toBe(30); // plafonné à MAX_SEGMENT_SEC
  });

  it('retombe sur la vitesse implicite quand la vitesse Doppler manque', () => {
    // ~50 m vers le nord en 10 s ≈ 5 m/s → en mouvement.
    const moved = [pt(0, null), pt(10, null, 48.000449, 2)];
    expect(movingTimeSec(moved)).toBe(10);

    // ~2 m en 10 s → dérive à l'arrêt, non comptée.
    const drift = [pt(0, null), pt(10, null, 48.000018, 2)];
    expect(movingTimeSec(drift)).toBe(0);
  });
});
