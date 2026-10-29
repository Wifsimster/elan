export type GeoPoint = { lat: number; lon: number };

export type Projection = {
  width: number;
  height: number;
  project: (p: GeoPoint) => readonly [number, number];
  metersPerUnit: number;
};

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
    const y = offsetY + (maxLat - p.lat) * scale;
    return [x, y] as const;
  };

  const metersPerUnit = Number.isFinite(scale) && scale > 0 ? 111320 / scale : 0;

  return { width, height, project, metersPerUnit };
}
