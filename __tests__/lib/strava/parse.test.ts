// Tests pour le parseur GPX / TCX (regex, pure JS, sans dépendance XML).
import { parseStravaFile } from '@/lib/strava/parse';

const GPX_SAMPLE = `<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <time>2025-06-02T08:00:00Z</time>
  </metadata>
  <trk>
    <name>Sortie matinale</name>
    <trkseg>
      <trkpt lat="48.8566" lon="2.3522">
        <ele>35.0</ele>
        <time>2025-06-02T08:00:00Z</time>
        <extensions>
          <gpxtpx:TrackPointExtension xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
            <gpxtpx:hr>120</gpxtpx:hr>
            <gpxtpx:cad>80</gpxtpx:cad>
          </gpxtpx:TrackPointExtension>
        </extensions>
      </trkpt>
      <trkpt lat="48.8570" lon="2.3525">
        <ele>36.0</ele>
        <time>2025-06-02T08:00:10Z</time>
      </trkpt>
      <trkpt lat="48.8580" lon="2.3530">
        <ele>37.0</ele>
        <time>2025-06-02T08:00:20Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>`;

const TCX_SAMPLE = `<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
  <Activities>
    <Activity Sport="Biking">
      <Id>2025-06-02T08:00:00Z</Id>
      <Lap StartTime="2025-06-02T08:00:00Z">
        <TotalTimeSeconds>1800</TotalTimeSeconds>
        <DistanceMeters>12000</DistanceMeters>
        <Calories>320</Calories>
        <Track>
          <Trackpoint>
            <Time>2025-06-02T08:00:00Z</Time>
            <Position>
              <LatitudeDegrees>48.8566</LatitudeDegrees>
              <LongitudeDegrees>2.3522</LongitudeDegrees>
            </Position>
            <AltitudeMeters>35.0</AltitudeMeters>
            <HeartRateBpm><Value>118</Value></HeartRateBpm>
            <Cadence>82</Cadence>
          </Trackpoint>
          <Trackpoint>
            <Time>2025-06-02T08:00:10Z</Time>
            <Position>
              <LatitudeDegrees>48.8570</LatitudeDegrees>
              <LongitudeDegrees>2.3525</LongitudeDegrees>
            </Position>
            <AltitudeMeters>36.0</AltitudeMeters>
            <HeartRateBpm><Value>122</Value></HeartRateBpm>
          </Trackpoint>
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>`;

describe('parseStravaFile — GPX', () => {
  it('détecte le format GPX et renvoie une seule activité', () => {
    const r = parseStravaFile(GPX_SAMPLE);
    expect(r.format).toBe('gpx');
    expect(r.activities).toHaveLength(1);
  });

  it('extrait l’ensemble des trackpoints avec lat/lon/ele', () => {
    const r = parseStravaFile(GPX_SAMPLE);
    const a = r.activities[0];
    expect(a.points).toHaveLength(3);
    expect(a.points[0].lat).toBeCloseTo(48.8566, 4);
    expect(a.points[0].lon).toBeCloseTo(2.3522, 4);
    expect(a.points[0].ele).toBe(35);
  });

  it('extrait la fréquence cardiaque et la cadence des extensions', () => {
    const r = parseStravaFile(GPX_SAMPLE);
    const a = r.activities[0];
    expect(a.points[0].hr).toBe(120);
    expect(a.points[0].cad).toBe(80);
    // Sans extension : null.
    expect(a.points[1].hr).toBeNull();
  });

  it('parse le timestamp de la métadonnée comme date de début', () => {
    const r = parseStravaFile(GPX_SAMPLE);
    const a = r.activities[0];
    expect(a.startedAt).toBe(Date.parse('2025-06-02T08:00:00Z'));
  });

  it('sport "unknown" pour GPX (Strava n’expose pas le sport)', () => {
    const r = parseStravaFile(GPX_SAMPLE);
    expect(r.activities[0].sport).toBe('unknown');
    expect(r.activities[0].distanceM).toBeNull();
    expect(r.activities[0].calories).toBeNull();
  });
});

describe('parseStravaFile — TCX', () => {
  it('détecte le format TCX', () => {
    const r = parseStravaFile(TCX_SAMPLE);
    expect(r.format).toBe('tcx');
    expect(r.activities).toHaveLength(1);
  });

  it('reconnaît le sport "Biking" → cycling', () => {
    const r = parseStravaFile(TCX_SAMPLE);
    expect(r.activities[0].sport).toBe('cycling');
  });

  it('somme distance et calories sur l’ensemble des laps', () => {
    const r = parseStravaFile(TCX_SAMPLE);
    const a = r.activities[0];
    expect(a.distanceM).toBe(12_000);
    expect(a.calories).toBe(320);
  });

  it('extrait les trackpoints avec position et FC', () => {
    const r = parseStravaFile(TCX_SAMPLE);
    const a = r.activities[0];
    expect(a.points).toHaveLength(2);
    expect(a.points[0].lat).toBeCloseTo(48.8566, 4);
    expect(a.points[0].hr).toBe(118);
    expect(a.points[0].cad).toBe(82);
  });

  it('startedAt vient de <Id>', () => {
    const r = parseStravaFile(TCX_SAMPLE);
    expect(r.activities[0].startedAt).toBe(Date.parse('2025-06-02T08:00:00Z'));
  });
});

describe('parseStravaFile — sécurité / robustesse', () => {
  it('rejette un fichier contenant une DOCTYPE (XXE / billion laughs)', () => {
    const malicious = `<?xml version="1.0"?>
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
<gpx></gpx>`;
    expect(() => parseStravaFile(malicious)).toThrow(/DOCTYPE|ENTITY/);
  });

  it('rejette un format inconnu (ni GPX ni TCX)', () => {
    expect(() => parseStravaFile('<html></html>')).toThrow(/Format non reconnu/);
  });
});
