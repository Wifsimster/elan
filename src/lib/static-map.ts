import { StaticMapImageManager } from '@maplibre/maplibre-react-native';

import { getMapStyleUrl } from '@/lib/map';
import type { GeoPoint } from '@/lib/route-projection';

const TILE = 512;

export type RouteSnapshot = {
  uri: string;
  width: number;
  height: number;
  project: (p: GeoPoint) => readonly [number, number];
};

function lonToWorldX(lon: number): number {
  return (lon + 180) / 360;
}
function latToWorldY(lat: number): number {
  const sin = Math.sin((lat * Math.PI) / 180);
  return 0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI);
}
function worldXToLon(x: number): number {
  return x * 360 - 180;
}
function worldYToLat(y: number): number {
  return (Math.atan(Math.sinh((0.5 - y) * 2 * Math.PI)) * 180) / Math.PI;
}

export async function createRouteSnapshot(
  points: GeoPoint[],
  opts: { width: number; height: number; scale?: number; pad?: number; styleUrl?: string },
): Promise<RouteSnapshot | null> {
  if (points.length < 2) return null;

  const styleUrl = opts.styleUrl ?? (await getMapStyleUrl());
  if (!styleUrl) return null;

  const scale = opts.scale ?? 2;
  const cw = Math.round(opts.width * scale);
  const ch = Math.round(opts.height * scale);
  const pad = (opts.pad ?? 24) * scale;

  let minX = Infinity,
    maxX = -Infinity,
    minY = Infinity,
    maxY = -Infinity;
  for (const p of points) {
    const x = lonToWorldX(p.lon);
    const y = latToWorldY(p.lat);
    if (x < minX) minX = x;
    if (x > maxX) maxX = x;
    if (y < minY) minY = y;
    if (y > maxY) maxY = y;
  }

  const worldW = Math.max(maxX - minX, 1e-9);
  const worldH = Math.max(maxY - minY, 1e-9);
  const worldSize = Math.min((cw - 2 * pad) / worldW, (ch - 2 * pad) / worldH);
  const zoom = Math.min(20, Math.max(0, Math.log2(worldSize / TILE)));
  const usedWorldSize = TILE * Math.pow(2, zoom);

  const centerX = (minX + maxX) / 2;
  const centerY = (minY + maxY) / 2;
  const centerLon = worldXToLon(centerX);
  const centerLat = worldYToLat(centerY);

  const project = (p: GeoPoint) => {
    const x = (lonToWorldX(p.lon) - centerX) * usedWorldSize + cw / 2;
    const y = (latToWorldY(p.lat) - centerY) * usedWorldSize + ch / 2;
    return [x, y] as const;
  };

  try {
    const uri = await StaticMapImageManager.createImage({
      mapStyle: styleUrl,
      center: [centerLon, centerLat],
      zoom,
      width: cw,
      height: ch,
      output: 'file',
      logo: false,
    });
    if (!uri) return null;
    return { uri, width: cw, height: ch, project };
  } catch {
    return null;
  }
}
