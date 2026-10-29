const R = 6371000;

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

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
