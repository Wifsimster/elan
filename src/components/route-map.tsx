import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Line, Polyline, Text as SvgText } from 'react-native-svg';

import { MapLibreRoute } from '@/components/maplibre-route';
import { Radius } from '@/constants/theme';
import { getMapStyleUrl, peekMapStyleUrl } from '@/lib/map';
import { createProjection, type GeoPoint } from '@/lib/route-projection';
import { useTheme } from '@/hooks/use-theme';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

const MIN_SCALE = 1;
const MAX_SCALE = 8;

function niceMeters(m: number): number {
  if (!(m > 0)) return 0;
  const pow = Math.pow(10, Math.floor(Math.log10(m)));
  const f = m / pow;
  return (f >= 5 ? 5 : f >= 2 ? 2 : 1) * pow;
}

function scaleLabel(m: number): string {
  if (m >= 1000) {
    const km = m / 1000;
    return `${(Number.isInteger(km) ? km : km.toFixed(1)).toString().replace('.', ',')} km`;
  }
  return `${Math.round(m)} m`;
}

type Props = {
  points: GeoPoint[];
  height?: number;
  color?: string;
  interactive?: boolean;
  live?: boolean;
  scrollRef?: React.RefObject<any>;
  fill?: boolean;
};

export function RouteMap(props: Props) {
  const theme = useTheme();
  const [styleUrl, setStyleUrl] = useState<string | undefined>(peekMapStyleUrl);

  useEffect(() => {
    if (styleUrl === undefined) getMapStyleUrl().then((u) => setStyleUrl(u ?? ''));
  }, [styleUrl]);

  if (styleUrl === undefined) {
    return props.fill ? (
      <View style={{ flex: 1, backgroundColor: theme.background }} />
    ) : (
      <View
        style={{
          height: props.height ?? 200,
          borderRadius: Radius.md,
          borderCurve: 'continuous',
          backgroundColor: theme.background,
          borderWidth: 1,
          borderColor: theme.border,
        }}
      />
    );
  }

  if (styleUrl && props.points.length >= 2) {
    return (
      <MapLibreRoute
        points={props.points}
        styleUrl={styleUrl}
        height={props.height ?? 200}
        color={props.color ?? theme.velo}
        live={props.live}
        interactive={props.interactive}
        fill={props.fill}
      />
    );
  }
  return <SvgRoute {...props} />;
}

function SvgRoute({ points, height = 200, color, interactive, live, scrollRef, fill }: Props) {
  const theme = useTheme();
  const stroke = color ?? theme.velo;

  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const tx = useSharedValue(0);
  const savedTx = useSharedValue(0);
  const ty = useSharedValue(0);
  const savedTy = useSharedValue(0);
  const pulse = useSharedValue(1);

  useEffect(() => {
    if (live) {
      pulse.value = withRepeat(withTiming(1.9, { duration: 1100 }), -1, true);
    }
  }, [live, pulse]);

  const viewStyle = useAnimatedStyle(() => ({
    flex: 1,
    transform: [
      { translateX: tx.value },
      { translateY: ty.value },
      { scale: scale.value },
    ],
  }));

  const haloProps = useAnimatedProps(() => ({ r: 13 * pulse.value, opacity: 1 / pulse.value }));

  if (points.length < 2) return null;

  const W = 1000;
  const H = (1000 * height) / 340;
  const proj = createProjection(points, { width: W, height: H });
  const coords = points.map((p) => proj.project(p));
  const pointsStr = coords.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const [sx, sy] = coords[0];
  const [ex, ey] = coords[coords.length - 1];

  const mpp = proj.metersPerUnit;
  const niceM = !live && mpp > 0 ? niceMeters(W * 0.28 * mpp) : 0;
  const barUnits = niceM > 0 ? niceM / mpp : 0;
  const nx = W - 46;

  const svg = (
    <Svg width="100%" height="100%" viewBox={`0 0 ${W} ${H}`}>
      {!live
        ? [1, 2, 3, 4, 5].map((i) => (
            <Line
              key={`grid-v-${i}`}
              x1={(W / 6) * i}
              y1={0}
              x2={(W / 6) * i}
              y2={H}
              stroke={theme.border}
              strokeWidth={2}
              strokeOpacity={0.5}
            />
          ))
        : null}
      {!live
        ? [1, 2, 3, 4, 5].map((i) => (
            <Line
              key={`grid-h-${i}`}
              x1={0}
              y1={(H / 6) * i}
              x2={W}
              y2={(H / 6) * i}
              stroke={theme.border}
              strokeWidth={2}
              strokeOpacity={0.5}
            />
          ))
        : null}

      <Polyline
        points={pointsStr}
        fill="none"
        stroke={stroke}
        strokeOpacity={0.16}
        strokeWidth={22}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <Polyline
        points={pointsStr}
        fill="none"
        stroke={stroke}
        strokeWidth={10}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <Circle cx={sx} cy={sy} r={13} fill={theme.success} />
      {live ? (
        <>
          <AnimatedCircle cx={ex} cy={ey} fill={stroke} animatedProps={haloProps} />
          <Circle cx={ex} cy={ey} r={13} fill={stroke} stroke={theme.background} strokeWidth={4} />
        </>
      ) : (
        <Circle cx={ex} cy={ey} r={13} fill="none" stroke={theme.heart} strokeWidth={6} />
      )}

      {!live && barUnits > 0 ? (
        <>
          <SvgText
            x={36}
            y={H - 50}
            fill={theme.textSecondary}
            fontSize={30}
            fontWeight="700">
            {scaleLabel(niceM)}
          </SvgText>
          <Line
            x1={36}
            y1={H - 34}
            x2={36 + barUnits}
            y2={H - 34}
            stroke={theme.textSecondary}
            strokeWidth={5}
            strokeLinecap="round"
          />
          <Line x1={36} y1={H - 42} x2={36} y2={H - 26} stroke={theme.textSecondary} strokeWidth={5} />
          <Line
            x1={36 + barUnits}
            y1={H - 42}
            x2={36 + barUnits}
            y2={H - 26}
            stroke={theme.textSecondary}
            strokeWidth={5}
          />
        </>
      ) : null}

      {!live ? (
        <>
          <Line x1={nx} y1={68} x2={nx} y2={32} stroke={theme.textSecondary} strokeWidth={5} strokeLinecap="round" />
          <Polyline
            points={`${nx - 9},46 ${nx},30 ${nx + 9},46`}
            fill="none"
            stroke={theme.textSecondary}
            strokeWidth={5}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          <SvgText x={nx} y={92} fill={theme.textSecondary} fontSize={30} fontWeight="700" textAnchor="middle">
            N
          </SvgText>
        </>
      ) : null}
    </Svg>
  );

  const container = (
    <View
      accessible
      accessibilityLabel={live ? 'Tracé GPS de la sortie en cours' : 'Tracé GPS de la sortie'}
      style={
        fill
          ? { flex: 1, overflow: 'hidden', backgroundColor: theme.background }
          : {
              height,
              borderRadius: Radius.md,
              borderCurve: 'continuous',
              overflow: 'hidden',
              backgroundColor: theme.background,
              borderWidth: 1,
              borderColor: theme.border,
            }
      }>
      {interactive ? <Animated.View style={viewStyle}>{svg}</Animated.View> : svg}
    </View>
  );

  if (!interactive) return container;

  /* eslint-disable react-hooks/immutability */
  const pinch = Gesture.Pinch()
    .onUpdate((e) => {
      scale.value = Math.min(MAX_SCALE, Math.max(MIN_SCALE, savedScale.value * e.scale));
    })
    .onEnd(() => {
      savedScale.value = scale.value;
    });

  let pan = Gesture.Pan()
    .onUpdate((e) => {
      tx.value = savedTx.value + e.translationX;
      ty.value = savedTy.value + e.translationY;
    })
    .onEnd(() => {
      savedTx.value = tx.value;
      savedTy.value = ty.value;
    });
  if (scrollRef) pan = pan.blocksExternalGesture(scrollRef);

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      scale.value = withTiming(1);
      savedScale.value = 1;
      tx.value = withTiming(0);
      savedTx.value = 0;
      ty.value = withTiming(0);
      savedTy.value = 0;
    });
  /* eslint-enable react-hooks/immutability */

  const gesture = Gesture.Exclusive(doubleTap, Gesture.Simultaneous(pinch, pan));

  return <GestureDetector gesture={gesture}>{container}</GestureDetector>;
}
