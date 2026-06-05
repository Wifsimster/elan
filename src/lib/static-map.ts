// Capture d'un fond de carte statique (tuiles du homelab) pour la séance
// partagée. MapLibre rend une image PNG hors écran via `StaticMapImageManager`,
// puis on superpose le tracé en SVG côté React. La projection ci-dessous est
// une vraie projection Web Mercator (tuiles 512 px, convention MapLibre) afin
// que le tracé se cale exactement sur les tuiles. Aucune tuile n'est demandée à
// un tiers : le style pointe uniquement vers le serveur de l'utilisateur, et en
// cas d'échec (hors-ligne, pas de style) on retourne `null` — l'appelant
// retombe alors sur le tracé SVG sur fond uni.
import { StaticMapImageManager } from '@maplibre/maplibre-react-native';

import { getMapStyleUrl } from '@/lib/map';
import type { GeoPoint } from '@/lib/route-projection';

/** Taille de tuile MapLibre : le monde fait `512 · 2^zoom` pixels. */
const TILE = 512;

export type RouteSnapshot = {
  /** URI fichier du PNG (fond de carte rendu par MapLibre). */
  uri: string;
  /** Dimensions du PNG, en pixels (espace de projection du tracé superposé). */
  width: number;
  height: number;
  /** Projette un point géographique vers les pixels du PNG (cale sur les tuiles). */
  project: (p: GeoPoint) => readonly [number, number];
};

// Coordonnées monde normalisées [0, 1] en Web Mercator.
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

/**
 * Génère un fond de carte statique cadré sur le tracé, à la même résolution que
 * la superposition SVG. Retourne `null` si aucun style n'est configuré ou si le
 * rendu échoue (l'appelant retombe sur le tracé sans fond).
 *
 * `width`/`height` sont en points logiques ; `scale` suréchantillonne le PNG
 * pour un rendu net une fois capturé en haute densité.
 */
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

  // Cadrage : on ajuste le zoom pour faire tenir l'emprise du tracé dans le
  // PNG (marge `pad`), puis on en déduit le centre géographique.
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
  // Pixels couvrant le monde entier à ce zoom (cale le tracé sur les tuiles).
  const worldSize = Math.min((cw - 2 * pad) / worldW, (ch - 2 * pad) / worldH);
  const zoom = Math.min(20, Math.max(0, Math.log2(worldSize / TILE)));
  // Le zoom a pu être plafonné : on recalcule la taille monde réellement utilisée.
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
    // Hors-ligne ou serveur de tuiles injoignable : on retombe sur le SVG.
    return null;
  }
}
