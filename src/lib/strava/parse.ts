// Parseur GPX / TCX en JavaScript pur — aucune dépendance, aucun module natif.
// Conçu pour les fichiers d'export Strava (une activité par fichier .gpx/.tcx).
//
// Durci contre les attaques XML : toute déclaration DOCTYPE/ENTITY est refusée
// (XXE / « billion laughs »), aucune entité externe n'est résolue, et la taille
// d'entrée est plafonnée. On scanne les balises connues plutôt que de construire
// un arbre DOM — suffisant et robuste pour la sortie bien formée de Strava.

export type ParsedPoint = {
  /** ms epoch (null si absent). */
  ts: number | null;
  lat: number | null;
  lon: number | null;
  /** altitude en mètres. */
  ele: number | null;
  /** fréquence cardiaque en bpm. */
  hr: number | null;
  /** cadence en tours/min. */
  cad: number | null;
};

export type ParsedActivity = {
  /** 'cycling' = vélo, 'other' = sport non vélo identifié, 'unknown' = indéterminé. */
  sport: 'cycling' | 'other' | 'unknown';
  startedAt: number | null;
  points: ParsedPoint[];
  /** Distance fournie par le fichier (TCX), en mètres, si présente. */
  distanceM: number | null;
  /** Calories fournies par le fichier (TCX), si présentes. */
  calories: number | null;
};

export type ParseResult = {
  format: 'gpx' | 'tcx' | 'fit';
  activities: ParsedActivity[];
};

/** Taille maximale acceptée (caractères) — garde-fou mémoire / DoS. */
const MAX_CHARS = 30_000_000;

function num(s: string | null | undefined): number | null {
  if (s == null) return null;
  const v = Number(s.trim());
  return Number.isFinite(v) ? v : null;
}

function parseTime(s: string | null | undefined): number | null {
  if (s == null) return null;
  const t = Date.parse(s.trim());
  return Number.isFinite(t) ? t : null;
}

/** Contenu texte du premier élément `tag` (préfixe de namespace ignoré). */
function firstTag(body: string, tag: string): string | null {
  const re = new RegExp(`<(?:\\w+:)?${tag}\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?${tag}>`, 'i');
  const m = re.exec(body);
  return m ? m[1] : null;
}

/** Valeur d'un attribut sur une balise ouvrante. */
function attr(openTag: string, name: string): string | null {
  const m = new RegExp(`\\b${name}\\s*=\\s*"([^"]*)"`, 'i').exec(openTag);
  return m ? m[1] : null;
}

function rejectUnsafeXml(content: string): void {
  if (/<!DOCTYPE/i.test(content) || /<!ENTITY/i.test(content)) {
    throw new Error('Fichier refusé : déclaration DOCTYPE/ENTITY non autorisée.');
  }
}

/** Parse un export Strava (GPX ou TCX). Lève si le format est inconnu/non sûr. */
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
  // Strava n'expose pas le sport dans le corps GPX → indéterminé (importé en vélo).
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

    // Distance / calories au niveau des tours (Lap), sommées sur l'activité.
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
