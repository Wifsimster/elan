export type ParsedPoint = {
  ts: number | null;
  lat: number | null;
  lon: number | null;
  ele: number | null;
  hr: number | null;
  cad: number | null;
};

export type ParsedActivity = {
  sport: 'cycling' | 'running' | 'walking' | 'other' | 'unknown';
  startedAt: number | null;
  points: ParsedPoint[];
  distanceM: number | null;
  calories: number | null;
};

export type ParseResult = {
  format: 'gpx' | 'tcx' | 'fit';
  activities: ParsedActivity[];
};

const MAX_CHARS = 30_000_000;

function num(s: string | null | undefined): number | null {
  if (s == null) return null;
  const t = s.trim();
  if (t === '') return null;
  const v = Number(t);
  return Number.isFinite(v) ? v : null;
}

function parseTime(s: string | null | undefined): number | null {
  if (s == null) return null;
  const t = Date.parse(s.trim());
  return Number.isFinite(t) ? t : null;
}

function firstTag(body: string, tag: string): string | null {
  const re = new RegExp(`<(?:\\w+:)?${tag}\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?${tag}>`, 'i');
  const m = re.exec(body);
  return m ? m[1] : null;
}

function attr(openTag: string, name: string): string | null {
  const m = new RegExp(`\\b${name}\\s*=\\s*"([^"]*)"`, 'i').exec(openTag);
  return m ? m[1] : null;
}

function rejectUnsafeXml(content: string): void {
  if (/<!DOCTYPE/i.test(content) || /<!ENTITY/i.test(content)) {
    throw new Error('Fichier refusé : déclaration DOCTYPE/ENTITY non autorisée.');
  }
}

export function parseStravaFile(content: string): ParseResult {
  if (content.length > MAX_CHARS) throw new Error('Fichier trop volumineux.');
  rejectUnsafeXml(content);

  if (/<gpx[\s>]/i.test(content)) {
    return { format: 'gpx', activities: [parseGpx(content)] };
  }
  if (/<TrainingCenterDatabase[\s>]/i.test(content)) {
    return { format: 'tcx', activities: parseTcx(content) };
  }
  throw new Error('Format non reconnu (ni GPX ni TCX).');
}

function parseGpx(content: string): ParsedActivity {
  const points: ParsedPoint[] = [];
  const re = /<trkpt\b([^>]*)>([\s\S]*?)<\/trkpt>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(content)) !== null) {
    const open = m[1];
    const body = m[2];
    points.push({
      ts: parseTime(firstTag(body, 'time')),
      lat: num(attr(open, 'lat')),
      lon: num(attr(open, 'lon')),
      ele: num(firstTag(body, 'ele')),
      hr: num(firstTag(body, 'hr')),
      cad: num(firstTag(body, 'cad')),
    });
  }
  return {
    sport: 'unknown',
    startedAt: parseTime(firstTag(content, 'time')),
    points,
    distanceM: null,
    calories: null,
  };
}

function parseTcx(content: string): ParsedActivity[] {
  const activities: ParsedActivity[] = [];
  const re = /<Activity\b([^>]*)>([\s\S]*?)<\/Activity>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(content)) !== null) {
    const open = m[1];
    const body = m[2];
    const sportAttr = attr(open, 'Sport') ?? '';
    const sport: ParsedActivity['sport'] = /bik|cycl/i.test(sportAttr)
      ? 'cycling'
      : /run|cours/i.test(sportAttr)
        ? 'running'
        : /walk|hik|march/i.test(sportAttr)
          ? 'walking'
          : sportAttr
            ? 'other'
            : 'unknown';

    const points: ParsedPoint[] = [];
    const tpRe = /<Trackpoint\b[^>]*>([\s\S]*?)<\/Trackpoint>/gi;
    let tp: RegExpExecArray | null;
    while ((tp = tpRe.exec(body)) !== null) {
      const b = tp[1];
      const pos = firstTag(b, 'Position');
      const hb = firstTag(b, 'HeartRateBpm');
      points.push({
        ts: parseTime(firstTag(b, 'Time')),
        lat: pos ? num(firstTag(pos, 'LatitudeDegrees')) : null,
        lon: pos ? num(firstTag(pos, 'LongitudeDegrees')) : null,
        ele: num(firstTag(b, 'AltitudeMeters')),
        hr: hb ? num(firstTag(hb, 'Value')) : null,
        cad: num(firstTag(b, 'Cadence')),
      });
    }

    let distanceM: number | null = null;
    let calories: number | null = null;
    const lapRe = /<Lap\b[^>]*>([\s\S]*?)<\/Lap>/gi;
    let lap: RegExpExecArray | null;
    while ((lap = lapRe.exec(body)) !== null) {
      const d = num(firstTag(lap[1], 'DistanceMeters'));
      const c = num(firstTag(lap[1], 'Calories'));
      if (d != null) distanceM = (distanceM ?? 0) + d;
      if (c != null) calories = (calories ?? 0) + c;
    }

    activities.push({
      sport,
      startedAt: parseTime(firstTag(body, 'Id')) ?? points[0]?.ts ?? null,
      points,
      distanceM,
      calories,
    });
  }
  return activities;
}
