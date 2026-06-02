import { useId } from 'react';
import { StyleSheet, View, type ViewProps } from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

import { Gradients, type GradientName } from '@/constants/theme';

type Props = ViewProps & {
  /** Nom d'un dégradé du thème, ou liste de couleurs explicite. */
  colors?: GradientName | readonly string[];
  /** Point de départ en fractions 0–1 (défaut : coin haut-gauche). */
  start?: { x: number; y: number };
  /** Point d'arrivée en fractions 0–1 (défaut : coin bas-droit, diagonale). */
  end?: { x: number; y: number };
};

/**
 * Fond en dégradé linéaire rendu via `react-native-svg` (aucune dépendance
 * réseau, 100 % local). Le contenu se pose par-dessus. Pensez à donner un
 * `borderRadius` + `overflow:'hidden'` via `style`.
 */
export function Gradient({
  colors = 'accent',
  start = { x: 0, y: 0 },
  end = { x: 1, y: 1 },
  style,
  children,
  ...rest
}: Props) {
  // useId() renvoie des deux-points (":r0:") qui cassent `url(#…)` sur web.
  const id = `grad-${useId().replace(/:/g, '')}`;
  const stops = typeof colors === 'string' ? Gradients[colors] : colors;

  return (
    <View style={[{ overflow: 'hidden' }, style]} {...rest}>
      <Svg style={StyleSheet.absoluteFill} width="100%" height="100%">
        <Defs>
          <LinearGradient id={id} x1={start.x} y1={start.y} x2={end.x} y2={end.y}>
            {stops.map((c, i) => (
              <Stop key={i} offset={stops.length === 1 ? 0 : i / (stops.length - 1)} stopColor={c} />
            ))}
          </LinearGradient>
        </Defs>
        <Rect x="0" y="0" width="100%" height="100%" fill={`url(#${id})`} />
      </Svg>
      {children}
    </View>
  );
}
