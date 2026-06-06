// Tests de la normalisation Strava (lib/strava/import.ts) : construction des
// brouillons importables, clé de déduplication (externalId) stable, et motifs
// d'exclusion. Pur (aucun accès base) — on alimente buildDrafts en octets.
import { buildDrafts } from '@/lib/strava/import';

/** Encode une chaîne ASCII en octets (entrée attendue par buildDrafts). */
function bytes(str: string): Uint8Array {
  const out = new Uint8Array(str.length);
  for (let i = 0; i < str.length; i++) out[i] = str.charCodeAt(i) & 0xff;
  return out;
}

const WEIGHT_KG = 75;

// GPX minimal valide : 3 points horodatés et géolocalisés sur 20 s.
const GPX = `<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata><time>2025-06-02T08:00:00Z</time></metadata>
  <trk><name>Sortie</name><trkseg>
    <trkpt lat="48.8566" lon="2.3522"><ele>35.0</ele><time>2025-06-02T08:00:00Z</time></trkpt>
    <trkpt lat="48.8570" lon="2.3525"><ele>36.0</ele><time>2025-06-02T08:00:10Z</time></trkpt>
    <trkpt lat="48.8580" lon="2.3530"><ele>40.0</ele><time>2025-06-02T08:00:20Z</time></trkpt>
  </trkseg></trk>
</gpx>`;

// Même trace mais démarrée 1 h plus tard : doit produire un externalId différent.
const GPX_AUTRE_DEPART = GPX.replace(/08:00:/g, '09:00:');

// GPX sans horodatage par point : aucune donnée temporelle exploitable.
const GPX_SANS_TEMPS = `<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><trkseg>
    <trkpt lat="48.8566" lon="2.3522"><ele>35.0</ele></trkpt>
    <trkpt lat="48.8570" lon="2.3525"><ele>36.0</ele></trkpt>
  </trkseg></trk>
</gpx>`;

describe('buildDrafts — construction du brouillon', () => {
  it('produit un brouillon vélo depuis un GPX valide', () => {
    const { drafts, skipped } = buildDrafts(bytes(GPX), WEIGHT_KG);
    expect(skipped).toHaveLength(0);
    expect(drafts).toHaveLength(1);

    const { session, points } = drafts[0];
    expect(session.type).toBe('velo');
    expect(session.source).toBe('strava');
    expect(session.startedAt).toBe(Date.parse('2025-06-02T08:00:00Z'));
    expect(session.durationSec).toBe(20);
    expect(points).toHaveLength(3);
  });

  it('calcule distance, vitesse et dénivelé positif depuis les points GPS', () => {
    const { session } = buildDrafts(bytes(GPX), WEIGHT_KG).drafts[0];
    // Aucune distance dans le GPX : elle est dérivée des points.
    expect(session.distanceM).not.toBeNull();
    expect(session.distanceM as number).toBeGreaterThan(0);
    expect(session.avgSpeedKmh as number).toBeGreaterThan(0);
    // Montée 35 → 36 → 40 = +5 m (le bruit < 0,5 m est filtré).
    expect(session.elevationGainM).toBe(5);
  });
});

describe('buildDrafts — clé de déduplication (externalId)', () => {
  it('préfixée « strava- » et déterministe pour des octets identiques', () => {
    const a = buildDrafts(bytes(GPX), WEIGHT_KG).drafts[0].session.externalId;
    const b = buildDrafts(bytes(GPX), WEIGHT_KG).drafts[0].session.externalId;
    expect(a).toMatch(/^strava-/);
    expect(a).toBe(b); // ré-import du même fichier → même clé → pas de doublon
  });

  it('diffère pour une activité au départ différent', () => {
    const a = buildDrafts(bytes(GPX), WEIGHT_KG).drafts[0].session.externalId;
    const c = buildDrafts(bytes(GPX_AUTRE_DEPART), WEIGHT_KG).drafts[0].session.externalId;
    expect(c).not.toBe(a);
  });

  it('indépendante du poids de l’utilisateur (n’entre pas dans la clé)', () => {
    const a = buildDrafts(bytes(GPX), 60).drafts[0].session.externalId;
    const b = buildDrafts(bytes(GPX), 95).drafts[0].session.externalId;
    expect(a).toBe(b);
  });
});

describe('buildDrafts — exclusions', () => {
  it('ignore une activité sans aucun horodatage exploitable', () => {
    const { drafts, skipped } = buildDrafts(bytes(GPX_SANS_TEMPS), WEIGHT_KG);
    expect(drafts).toHaveLength(0);
    expect(skipped).toHaveLength(1);
    expect(skipped[0]).toMatch(/horodatage/i);
  });
});
