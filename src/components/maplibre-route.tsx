import { Camera, GeoJSONSource, Layer, Map as MapView } from '@maplibre/maplibre-react-native';
import type { Feature, LineString, Point } from 'geojson';
import { useMemo } from 'react';
import { View } from 'react-native';

import { Radius } from '@/constants/theme';
import type { GeoPoint } from '@/lib/route-projection';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  points: GeoPoint[];
  /** URL du style MapLibre auto-hébergé. */
  styleUrl: string;
  height: number;
  color: string;
  /** Suivi temps réel : recadre sur la position courante, gestes désactivés. */
  live?: boolean;
  /** Carte explorable (pinch/pan) — réservé à l'historique, à l'arrêt. */
  interactive?: boolean;
  /** Plein écran : remplit le parent (flex), sans bordure ni coins arrondis. */
  fill?: boolean;
};

const lonLat = (p: GeoPoint): [number, number] => [p.lon, p.lat];

/**
 * Fond de carte MapLibre (tuiles servies par le homelab) avec le tracé en
 * surimpression. Aucune tuile n'est demandée à un tiers : le style pointe
 * uniquement vers le serveur de l'utilisateur.
 */
export function MapLibreRoute({ points, styleUrl, height, color, live, interactive, fill }: Props) {
  const theme = useTheme();

  const coords = useMemo(() => points.map(lonLat), [points]);
  const first = coords[0];
  const last = coords[coords.length - 1];

  const bounds = useMemo(() => {
    if (coords.length < 2) return null;
    let w = Infinity,
      s = Infinity,
      e = -Infinity,
      n = -Infinity;
    for (const [lon, lat] of coords) {
      if (lon < w) w = lon;
      if (lon > e) e = lon;
      if (lat < s) s = lat;
      if (lat > n) n = lat;
    }
    return [w, s, e, n] as [number, number, number, number];
  }, [coords]);

  const line = useMemo<Feature<LineString>>(
    () => ({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } }),
    [coords],
  );
  const startPt = useMemo<Feature<Point> | null>(
    () => (first ? { type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: first } } : null),
    [first],
  );
  const endPt = useMemo<Feature<Point> | null>(
    () => (last ? { type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: last } } : null),
    [last],
  );

  return (
    <View
      style={
        fill
          ? { flex: 1, overflow: 'hidden' }
          : {
              height,
              borderRadius: Radius.md,
              borderCurve: 'continuous',
              overflow: 'hidden',
              borderWidth: 1,
              borderColor: theme.border,
            }
      }
      // Mode live : lecture passive, on bloque les gestes (sécurité à vélo).
      pointerEvents={interactive ? 'auto' : 'none'}>
      <MapView
        style={{ flex: 1 }}
        mapStyle={styleUrl}
        logo={false}
        attribution={false}
        compass={!!interactive}>
        {live && last ? (
          <Camera center={last} zoom={15} duration={600} />
        ) : bounds ? (
          <Camera
            bounds={bounds}
            padding={{ top: 48, right: 48, bottom: 48, left: 48 }}
            duration={0}
          />
        ) : last ? (
          <Camera center={last} zoom={14} duration={0} />
        ) : null}

        {coords.length >= 2 ? (
          <GeoJSONSource id="route" data={line}>
            <Layer
              id="route-line"
              type="line"
              layout={{ 'line-join': 'round', 'line-cap': 'round' }}
              paint={{ 'line-color': color, 'line-width': 4 }}
            />
          </GeoJSONSource>
        ) : null}

        {startPt ? (
          <GeoJSONSource id="route-start" data={startPt}>
            <Layer
              id="route-start-dot"
              type="circle"
              paint={{
                'circle-radius': 6,
                'circle-color': theme.success,
                'circle-stroke-width': 2,
                'circle-stroke-color': '#FFFFFF',
              }}
            />
          </GeoJSONSource>
        ) : null}

        {endPt ? (
          <GeoJSONSource id="route-end" data={endPt}>
            <Layer
              id="route-end-dot"
              type="circle"
              paint={{
                'circle-radius': 7,
                'circle-color': color,
                'circle-stroke-width': 3,
                'circle-stroke-color': theme.background,
              }}
            />
          </GeoJSONSource>
        ) : null}
      </MapView>
    </View>
  );
}
