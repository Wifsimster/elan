import { useId, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent, type ViewProps } from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

import { Gradients, type GradientName } from '@/constants/theme';

type Props = ViewProps & {
  colors?: GradientName | readonly string[];
  start?: { x: number; y: number };
  end?: { x: number; y: number };
};

export function Gradient({
  colors = 'accent',
  start = { x: 0, y: 0 },
  end = { x: 1, y: 1 },
  style,
  children,
  ...rest
}: Props) {
  const id = `grad-${useId().replace(/:/g, '')}`;
  const stops = typeof colors === 'string' ? Gradients[colors] : colors;

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
