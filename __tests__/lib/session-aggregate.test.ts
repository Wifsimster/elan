import { aggregateFromPoints, type AggPoint } from '@/lib/session-aggregate';

const p = (over: Partial<AggPoint>): AggPoint => ({
  ts: 0,
  lat: 48.85,
  lon: 2.35,
  altitude: null,
  speedKmh: null,
  hr: null,
  cadence: null,
  ...over,
});

describe('aggregateFromPoints', () => {
  it('renvoie null pour moins de deux points', () => {
    expect(aggregateFromPoints([])).toBeNull();
    expect(aggregateFromPoints([p({ ts: 0 })])).toBeNull();
  });

  it('recalcule durée, distance et vitesse max plausibles', () => {
    // ~15,7 m entre deux points à 0,0001° de longitude (≈ à cette latitude),
    // sur 1 s → mouvement. On roule 10 s.
    const points: AggPoint[] = [];
    for (let i = 0; i <= 10; i++) {
      points.push(p({ ts: i * 1000, lon: 2.35 + i * 0.0002, speedKmh: 30, hr: 140 + (i % 3), cadence: 85 }));
    }
    const agg = aggregateFromPoints(points)!;
    expect(agg).not.toBeNull();
    expect(agg.durationSec).toBe(10);
    expect(agg.endedAt).toBe(10_000);
    expect(agg.distanceM).toBeGreaterThan(0);
    expect(agg.avgSpeedKmh).toBeGreaterThan(0);
    expect(agg.maxHr).toBe(142);
    expect(agg.avgCadence).toBe(85);
  });

  it('plafonne la vitesse max (fix Doppler glitché ignoré)', () => {
    const agg = aggregateFromPoints([
      p({ ts: 0, speedKmh: 25 }),
      p({ ts: 1000, lon: 2.3502, speedKmh: 500 }), // aberrant
      p({ ts: 2000, lon: 2.3504, speedKmh: 28 }),
    ])!;
    expect(agg.maxSpeedKmh).toBe(28);
  });

  it('ne crédite pas la distance à l’arrêt (dérive GPS)', () => {
    // Points quasi immobiles avec vitesse Doppler nulle : pas de distance.
    const agg = aggregateFromPoints([
      p({ ts: 0, speedKmh: 0 }),
      p({ ts: 1000, lat: 48.850001, speedKmh: 0 }),
      p({ ts: 2000, lat: 48.850002, speedKmh: 0 }),
    ]);
    expect(agg?.distanceM).toBeNull();
  });
});
