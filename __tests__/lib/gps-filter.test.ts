import { GpsConsolidator, type GpsFix } from '@/lib/gps-filter';

const fix = (over: Partial<GpsFix>): GpsFix => ({
  ts: 0,
  lat: 0,
  lon: 0,
  altitude: null,
  accuracy: 5,
  altitudeAccuracy: null,
  speed: 5,
  ...over,
});

describe('GpsConsolidator — antiméridien', () => {
  it('ne fait pas sauter l’estimation à l’autre bout du globe en croisant ±180°', () => {
    const c = new GpsConsolidator();
    c.process(fix({ ts: 0, lat: 0, lon: 179.9999 }));
    const r = c.process(fix({ ts: 1000, lat: 0, lon: -179.9999 }));
    expect(r.point).not.toBeNull();
    // L'estimation doit rester au voisinage de l'antiméridien (|lon| ~180),
    // pas retomber vers 0 (ce que produisait une innovation non enroulée).
    expect(Math.abs(r.point!.lon)).toBeGreaterThan(179);
  });
});
