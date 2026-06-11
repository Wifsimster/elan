import { useId, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent, type ViewProps } from 'react-native';
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

  // Les pourcentages SVG (`width="100%"`) ne s'étirent pas fiablement en React
  // Native : le dégradé gardait une largeur par défaut et laissait la couleur
  // de fond apparaître à droite. On mesure le conteneur et on passe des
  // dimensions en pixels (même pattern que line-chart.tsx).
  const [size, setSize] = useState({ width: 0, height: 0 });
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev.width === width && prev.height === height ? prev : { width, height }));
  };

  return (
    <View style={[{ overflow: 'hidden' }, style]} {...rest} onLayout={onLayout}>
      {size.width > 0 && size.height > 0 ? (
        <Svg style={StyleSheet.absoluteFill} width={size.width} height={size.height}>
          <Defs>
            <LinearGradient id={id} x1={start.x} y1={start.y} x2={end.x} y2={end.y}>
              {stops.map((c, i) => (
                <Stop key={i} offset={stops.length === 1 ? 0 : i / (stops.length - 1)} stopColor={c} />
              ))}
            </LinearGradient>
          </Defs>
          <Rect x="0" y="0" width={size.width} height={size.height} fill={`url(#${id})`} />
        </Svg>
      ) : null}
      {children}
    </View>
  );
}
