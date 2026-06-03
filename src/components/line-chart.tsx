import { useId, useState } from 'react';
import { Text, View } from 'react-native';
import Svg, { Defs, Line, LinearGradient, Path, Stop } from 'react-native-svg';

import { useTheme } from '@/hooks/use-theme';

export type ChartPoint = { x: number; y: number };

type Props = {
  /** Série triée par `x` croissant. Rendu nul en dessous de 2 points. */
  data: ChartPoint[];
  /** Couleur du tracé et du dégradé de remplissage. */
  color: string;
  height?: number;
  /** Trace une ligne pointillée horizontale (la moyenne, façon Strava). */
  avg?: number | null;
  /** Formatte les libellés de l'axe Y (défaut : entier). */
  formatY?: (y: number) => string;
  /** Formatte les libellés de l'axe X (défaut : entier). */
  formatX?: (x: number) => string;
};

// Marges réservées aux libellés d'axes (en pixels).
const PAD_L = 40;
const PAD_R = 10;
const PAD_T = 12;
const PAD_B = 22;

/**
 * Graphe d'aire façon Strava : tracé + remplissage en dégradé vertical (couleur
 * → transparent), grille discrète, ligne de moyenne pointillée et libellés
 * d'axes. 100 % SVG local — aucune dépendance réseau (invariant produit).
 *
 * La largeur est mesurée au `onLayout` ; on dessine ensuite en pixels réels pour
 * des libellés nets (pas de mise à l'échelle d'un `viewBox`).
 */
export function LineChart({ data, color, height = 168, avg, formatY, formatX }: Props) {
  const theme = useTheme();
  const gradId = `chart-${useId().replace(/:/g, '')}`;
  const [width, setWidth] = useState(0);

  if (data.length < 2) return null;

  const xs = data.map((d) => d.x);
  const ys = data.map((d) => d.y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  let minY = Math.min(...ys);
  let maxY = Math.max(...ys);
  if (minY === maxY) {
    minY -= 1;
    maxY += 1;
  }
  // Un peu de marge verticale pour ne pas coller le tracé aux bords.
  const headroom = (maxY - minY) * 0.08;
  minY -= headroom;
  maxY += headroom;

  const innerW = Math.max(1, width - PAD_L - PAD_R);
  const innerH = height - PAD_T - PAD_B;
  const baseY = PAD_T + innerH;

  const sx = (x: number) => PAD_L + ((x - minX) / (maxX - minX || 1)) * innerW;
  const sy = (y: number) => PAD_T + (1 - (y - minY) / (maxY - minY || 1)) * innerH;

  const line = data
    .map((d, i) => `${i === 0 ? 'M' : 'L'}${sx(d.x).toFixed(1)} ${sy(d.y).toFixed(1)}`)
    .join(' ');
  const area = `${line} L${sx(maxX).toFixed(1)} ${baseY.toFixed(1)} L${sx(minX).toFixed(1)} ${baseY.toFixed(1)} Z`;

  const yTicks = [maxY, (maxY + minY) / 2, minY];
  const xTickCount = 4;
  const xTicks = Array.from({ length: xTickCount + 1 }, (_, i) => minX + ((maxX - minX) * i) / xTickCount);
  const fmtY = formatY ?? ((v: number) => String(Math.round(v)));
  const fmtX = formatX ?? ((v: number) => String(Math.round(v)));

  return (
    <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)} style={{ height }}>
      {width > 0 ? (
        <>
          <Svg width={width} height={height}>
            <Defs>
              <LinearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
                <Stop offset="0" stopColor={color} stopOpacity={0.35} />
                <Stop offset="1" stopColor={color} stopOpacity={0.02} />
              </LinearGradient>
            </Defs>
            {yTicks.map((t, i) => (
              <Line
                key={`g${i}`}
                x1={PAD_L}
                y1={sy(t)}
                x2={width - PAD_R}
                y2={sy(t)}
                stroke={theme.hairline}
                strokeWidth={1}
              />
            ))}
            <Path d={area} fill={`url(#${gradId})`} />
            <Path
              d={line}
              fill="none"
              stroke={color}
              strokeWidth={2.5}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
            {avg != null ? (
              <Line
                x1={PAD_L}
                y1={sy(avg)}
                x2={width - PAD_R}
                y2={sy(avg)}
                stroke={theme.textMuted}
                strokeWidth={1.5}
                strokeDasharray="5 4"
              />
            ) : null}
          </Svg>

          {/* Libellés d'axe Y (haut / milieu / bas). */}
          {yTicks.map((t, i) => (
            <Text
              key={`y${i}`}
              style={{
                position: 'absolute',
                left: 0,
                top: sy(t) - 7,
                width: PAD_L - 6,
                textAlign: 'right',
                fontSize: 10,
                fontWeight: '600',
                color: theme.textMuted,
                fontVariant: ['tabular-nums'],
              }}>
              {fmtY(t)}
            </Text>
          ))}

          {/* Libellés d'axe X (alignés aux extrémités pour ne pas déborder). */}
          {xTicks.map((t, i) => {
            const first = i === 0;
            const last = i === xTicks.length - 1;
            return (
              <Text
                key={`x${i}`}
                style={{
                  position: 'absolute',
                  top: height - 14,
                  left: first ? PAD_L : last ? undefined : sx(t) - 24,
                  right: last ? PAD_R : undefined,
                  width: first || last ? undefined : 48,
                  textAlign: first ? 'left' : last ? 'right' : 'center',
                  fontSize: 10,
                  fontWeight: '600',
                  color: theme.textMuted,
                  fontVariant: ['tabular-nums'],
                }}>
                {fmtX(t)}
              </Text>
            );
          })}
        </>
      ) : null}
    </View>
  );
}
