// Projection géographique d'un tracé GPS vers des coordonnées écran.
// Fonction pure, sans état ni React : réutilisable par le rendu statique,
// live et interactif. Aucune donnée n'est envoyée à un serveur.

export type GeoPoint = { lat: number; lon: number };

export type Projection = {
  width: number;
  height: number;
  /** Projette un point géographique vers les coordonnées du viewBox SVG. */
  project: (p: GeoPoint) => readonly [number, number];
};

/**
 * Construit une projection cadrant l'ensemble des points dans un viewBox
 * `width × height`, en conservant le ratio géographique réel (à cette latitude,
 * 1° de longitude couvre moins de distance que 1° de latitude).
 */
export function createProjection(
  points: GeoPoint[],
  opts: { width: number; height: number; pad?: number },
): Projection {
  const { width, height, pad = 40 } = opts;

  const lats = points.map((p) => p.lat);
  const lons = points.map((p) => p.lon);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLon = Math.min(...lons);
  const maxLon = Math.max(...lons);

  const latRange = Math.max(1e-6, maxLat - minLat);
  const lonRange = Math.max(1e-6, maxLon - minLon);
  const lonScale = Math.cos((((minLat + maxLat) / 2) * Math.PI) / 180);
  const geoW = lonRange * lonScale;
  const geoH = latRange;
  const scale = Math.min((width - 2 * pad) / geoW, (height - 2 * pad) / geoH);

  const offsetX = (width - geoW * scale) / 2;
  const offsetY = (height - geoH * scale) / 2;

  const project = (p: GeoPoint) => {
    const x = offsetX + (p.lon - minLon) * lonScale * scale;
    const y = offsetY + (maxLat - p.lat) * scale; // y inversé (nord en haut)
    return [x, y] as const;
  };

  return { width, height, project };
}
