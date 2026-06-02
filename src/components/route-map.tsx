import { useEffect } from 'react';
import { View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Polyline } from 'react-native-svg';

import { Radius } from '@/constants/theme';
import { createProjection, type GeoPoint } from '@/lib/route-projection';
import { useTheme } from '@/hooks/use-theme';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

const MIN_SCALE = 1;
const MAX_SCALE = 8;

type Props = {
  points: GeoPoint[];
  height?: number;
  color?: string;
  /** Active le pinch-zoom + pan + double-tap reset (réservé à l'historique, à l'arrêt). */
  interactive?: boolean;
  /** Affichage temps réel : marqueur de position pulsant, sans geste (sécurité à vélo). */
  live?: boolean;
  /** Ref de la ScrollView parente : le pan de la carte bloque son défilement. */
  scrollRef?: React.RefObject<any>;
};

/**
 * Tracé du parcours, normalisé dans la vue. Aucun fond cartographique : aucune
 * donnée de localisation n'est envoyée à un serveur (promesse 100 % locale).
 *
 * Trois modes :
 * - statique (défaut) — vignette figée ;
 * - `live` — recadrage automatique + marqueur de position courante pulsant ;
 * - `interactive` — pinch-zoom, pan et double-tap pour réinitialiser.
 */
export function RouteMap({ points, height = 200, color, interactive, live, scrollRef }: Props) {
  const theme = useTheme();
  const stroke = color ?? theme.velo;

  // Transform de vue (pinch/pan), découplé de la projection géographique.
  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const tx = useSharedValue(0);
  const savedTx = useSharedValue(0);
  const ty = useSharedValue(0);
  const savedTy = useSharedValue(0);
  // Halo pulsant du marqueur de position courante (mode live).
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

  // Tous les hooks sont appelés avant cette sortie anticipée.
  if (points.length < 2) return null;

  const W = 1000;
  const H = (1000 * height) / 340; // ratio cohérent avec la largeur dessinée
  const proj = createProjection(points, { width: W, height: H });
  const coords = points.map((p) => proj.project(p));
  const pointsStr = coords.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const [sx, sy] = coords[0];
  const [ex, ey] = coords[coords.length - 1];

  const svg = (
    <Svg width="100%" height="100%" viewBox={`0 0 ${W} ${H}`}>
      <Polyline
        points={pointsStr}
        fill="none"
        stroke={stroke}
        strokeWidth={10}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      {/* Départ : disque plein vert. Distinction non chromatique avec l'arrivée (a11y). */}
      <Circle cx={sx} cy={sy} r={13} fill={theme.success} />
      {live ? (
        <>
          <AnimatedCircle cx={ex} cy={ey} fill={stroke} animatedProps={haloProps} />
          <Circle cx={ex} cy={ey} r={13} fill={stroke} stroke={theme.background} strokeWidth={4} />
        </>
      ) : (
        // Arrivée : anneau (forme distincte du départ).
        <Circle cx={ex} cy={ey} r={13} fill="none" stroke={theme.heart} strokeWidth={6} />
      )}
    </Svg>
  );

  const container = (
    <View
      accessible
      accessibilityLabel={live ? 'Tracé GPS de la sortie en cours' : 'Tracé GPS de la sortie'}
      style={{
        height,
        borderRadius: Radius.md,
        borderCurve: 'continuous',
        overflow: 'hidden',
        backgroundColor: theme.background,
        borderWidth: 1,
        borderColor: theme.border,
      }}>
      {interactive ? <Animated.View style={viewStyle}>{svg}</Animated.View> : svg}
    </View>
  );

  if (!interactive) return container;

  // Les shared values Reanimated se mutent par `.value` ; la règle d'immutabilité
  // du React Compiler est un faux positif dans les callbacks de gestes (worklets).
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
  // Le pan de la carte prend le pas sur le défilement vertical de la page.
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
