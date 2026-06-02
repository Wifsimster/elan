// Calculs géographiques.

const R = 6371000; // rayon terrestre en mètres

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Distance en mètres entre deux coordonnées (formule de haversine). */
export function haversineMeters(
  a: { lat: number; lon: number },
  b: { lat: number; lon: number },
): number {
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * Décime un tracé en ne conservant qu'un point tous les `minMeters` (départ et
 * arrivée toujours préservés). Réduit le nombre de sommets dessinés sur une
 * longue sortie sans altérer la forme — purement pour l'affichage, la base
 * conserve la résolution pleine.
 */
export function decimateByDistance<T extends { lat: number; lon: number }>(
  points: T[],
  minMeters: number,
): T[] {
  if (points.length <= 2) return points;
  const out: T[] = [points[0]];
  let last = points[0];
  for (let i = 1; i < points.length - 1; i++) {
    if (haversineMeters(last, points[i]) >= minMeters) {
      out.push(points[i]);
      last = points[i];
    }
  }
  out.push(points[points.length - 1]);
  return out;
}
