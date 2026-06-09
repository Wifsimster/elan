// Export d'une sortie vélo au format GPX 1.1 — le format de fichier accepté par
// Strava à l'upload (« Importer une activité » → GPX). Pur : (séance, points) →
// chaîne GPX, aucun IO ni réseau ; l'orchestration (écriture + partage) est
// faite par `use-gpx-export.tsx`.
//
// La fréquence cardiaque et la cadence sont émises via l'extension Garmin
// `TrackPointExtension` (le dialecte que Strava sait relire). Conçu pour
// round-tripper avec le parseur d'import (`parse.ts`) : les balises `hr`/`cad`
// y sont relues en ignorant le préfixe de namespace.
import type { Session, TrackPoint } from '@/lib/types';

const GPX_NS = 'http://www.topografix.com/GPX/1/1';
const TPX_NS = 'http://www.garmin.com/xmlschemas/TrackPointExtension/v1';

/** Échappe les caractères réservés XML dans un texte (nom de séance, notes). */
function escapeXml(s: string): string {
  return s.replace(/[<>&'"]/g, (c) =>
    c === '<' ? '&lt;' : c === '>' ? '&gt;' : c === '&' ? '&amp;' : c === "'" ? '&apos;' : '&quot;',
  );
}

/** Horodatage ms epoch → ISO 8601 UTC (format attendu dans un GPX). */
function iso(ts: number): string {
  return new Date(ts).toISOString();
}

/** Coordonnée GPS à ~1 cm de précision, sans zéros décimaux superflus. */
function coord(n: number): string {
  return Number(n.toFixed(7)).toString();
}

/** Nom lisible de la sortie, dérivé de la date de début (heure locale). */
export function rideName(session: Session): string {
  const d = new Date(session.startedAt);
  const h = d.getHours();
  const moment = h < 6 ? 'Nuit' : h < 12 ? 'Matin' : h < 18 ? 'Après-midi' : 'Soir';
  return `Sortie vélo — ${moment}`;
}

/** Nom de fichier GPX stable et lisible (`elan-velo-2026-06-09-1430.gpx`). */
export function rideFileName(session: Session): string {
  const d = new Date(session.startedAt);
  const p = (n: number) => String(n).padStart(2, '0');
  const stamp = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}-${p(d.getHours())}${p(
    d.getMinutes(),
  )}`;
  return `elan-velo-${stamp}.gpx`;
}

/**
 * Construit le document GPX 1.1 d'une sortie vélo à partir de ses points GPS.
 * Les points sans coordonnées valides ne devraient pas exister en base (lat/lon
 * NOT NULL), mais on émet l'altitude / la FC / la cadence seulement si présentes.
 */
export function buildRideGpx(session: Session, points: TrackPoint[]): string {
  const lines: string[] = [];
  lines.push('<?xml version="1.0" encoding="UTF-8"?>');
  lines.push(
    `<gpx version="1.1" creator="Élan" xmlns="${GPX_NS}" xmlns:gpxtpx="${TPX_NS}">`,
  );
  lines.push('  <metadata>');
  lines.push(`    <time>${iso(session.startedAt)}</time>`);
  lines.push('  </metadata>');
  lines.push('  <trk>');
  lines.push(`    <name>${escapeXml(rideName(session))}</name>`);
  // Type d'activité : indice pour Strava au moment de l'upload.
  lines.push('    <type>cycling</type>');
  lines.push('    <trkseg>');

  for (const p of points) {
    lines.push(`      <trkpt lat="${coord(p.lat)}" lon="${coord(p.lon)}">`);
    if (p.altitude != null) lines.push(`        <ele>${Number(p.altitude.toFixed(1))}</ele>`);
    lines.push(`        <time>${iso(p.ts)}</time>`);

    // FC / cadence : on exclut les zéros de dropout capteur (pas de mesure),
    // cohérent avec le calcul des moyennes côté import.
    const hr = p.hr != null && p.hr > 0 ? Math.round(p.hr) : null;
    const cad = p.cadence != null && p.cadence > 0 ? Math.round(p.cadence) : null;
    if (hr != null || cad != null) {
      lines.push('        <extensions>');
      lines.push('          <gpxtpx:TrackPointExtension>');
      if (hr != null) lines.push(`            <gpxtpx:hr>${hr}</gpxtpx:hr>`);
      if (cad != null) lines.push(`            <gpxtpx:cad>${cad}</gpxtpx:cad>`);
      lines.push('          </gpxtpx:TrackPointExtension>');
      lines.push('        </extensions>');
    }
    lines.push('      </trkpt>');
  }

  lines.push('    </trkseg>');
  lines.push('  </trk>');
  lines.push('</gpx>');
  return lines.join('\n');
}
