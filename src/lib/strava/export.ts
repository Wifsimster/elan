import type { Session, TrackPoint } from '@/lib/types';

const GPX_NS = 'http://www.topografix.com/GPX/1/1';
const TPX_NS = 'http://www.garmin.com/xmlschemas/TrackPointExtension/v1';

function escapeXml(s: string): string {
  return s.replace(/[<>&'"]/g, (c) =>
    c === '<' ? '&lt;' : c === '>' ? '&gt;' : c === '&' ? '&amp;' : c === "'" ? '&apos;' : '&quot;',
  );
}

function iso(ts: number): string {
  return new Date(ts).toISOString();
}

function coord(n: number): string {
  return Number(n.toFixed(7)).toString();
}

export function rideName(session: Session): string {
  const d = new Date(session.startedAt);
  const h = d.getHours();
  const moment = h < 6 ? 'Nuit' : h < 12 ? 'Matin' : h < 18 ? 'Après-midi' : 'Soir';
  return `Sortie vélo — ${moment}`;
}

export function rideFileName(session: Session): string {
  const d = new Date(session.startedAt);
  const p = (n: number) => String(n).padStart(2, '0');
  const stamp = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}-${p(d.getHours())}${p(
    d.getMinutes(),
  )}`;
  return `elan-velo-${stamp}.gpx`;
}

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
  lines.push('    <type>cycling</type>');
  lines.push('    <trkseg>');

  for (const p of points) {
    lines.push(`      <trkpt lat="${coord(p.lat)}" lon="${coord(p.lon)}">`);
    if (p.altitude != null) lines.push(`        <ele>${Number(p.altitude.toFixed(1))}</ele>`);
    lines.push(`        <time>${iso(p.ts)}</time>`);

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
