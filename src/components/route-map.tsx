import { View } from 'react-native';
import Svg, { Circle, Polyline } from 'react-native-svg';

import type { TrackPoint } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

type Props = { points: TrackPoint[]; height?: number; color?: string };

/**
 * Trace du parcours, normalisé dans la vue (pas de fond cartographique :
 * aucune donnée n'est envoyée à un serveur).
 */
export function RouteMap({ points, height = 200, color }: Props) {
  const theme = useTheme();
  const stroke = color ?? theme.velo;

  if (points.length < 2) return null;

  const W = 1000;
  const H = (1000 * height) / 340; // ratio cohérent avec la largeur dessinée
  const pad = 40;

  const lats = points.map((p) => p.lat);
  const lons = points.map((p) => p.lon);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLon = Math.min(...lons);
  const maxLon = Math.max(...lons);

  // Conserve le ratio géographique (à cette latitude, 1° de longitude < 1° de latitude).
  const latRange = Math.max(1e-6, maxLat - minLat);
  const lonRange = Math.max(1e-6, maxLon - minLon);
  const lonScale = Math.cos((((minLat + maxLat) / 2) * Math.PI) / 180);
  const geoW = lonRange * lonScale;
  const geoH = latRange;
  const scale = Math.min((W - 2 * pad) / geoW, (H - 2 * pad) / geoH);

  const offsetX = (W - geoW * scale) / 2;
  const offsetY = (H - geoH * scale) / 2;

  const project = (p: TrackPoint) => {
    const x = offsetX + (p.lon - minLon) * lonScale * scale;
    const y = offsetY + (maxLat - p.lat) * scale; // y inversé
    return [x, y] as const;
  };

  const coords = points.map(project);
  const pointsStr = coords.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const [sx, sy] = coords[0];
  const [ex, ey] = coords[coords.length - 1];

  return (
    <View
      style={{
        height,
        borderRadius: 14,
        borderCurve: 'continuous',
        overflow: 'hidden',
        backgroundColor: theme.background,
        borderWidth: 1,
        borderColor: theme.border,
      }}>
      <Svg width="100%" height="100%" viewBox={`0 0 ${W} ${H}`}>
        <Polyline
          points={pointsStr}
          fill="none"
          stroke={stroke}
          strokeWidth={10}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <Circle cx={sx} cy={sy} r={14} fill={theme.success} />
        <Circle cx={ex} cy={ey} r={14} fill={theme.heart} />
      </Svg>
    </View>
  );
}
