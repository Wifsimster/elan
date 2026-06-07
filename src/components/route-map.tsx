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
import { getMapStyleUrl } from '@/lib/map';
import { createProjection, type GeoPoint } from '@/lib/route-projection';
import { useTheme } from '@/hooks/use-theme';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

const MIN_SCALE = 1;
const MAX_SCALE = 8;

/** Arrondit une distance à une valeur « ronde » (1/2/5 ×10ⁿ) pour l'échelle. */
function niceMeters(m: number): number {
  if (!(m > 0)) return 0;
  const pow = Math.pow(10, Math.floor(Math.log10(m)));
  const f = m / pow;
  return (f >= 5 ? 5 : f >= 2 ? 2 : 1) * pow;
}

/** Libellé court d'une distance ronde : « 500 m », « 1 km », « 2,5 km ». */
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
  /** Active le pinch-zoom + pan + double-tap reset (réservé à l'historique, à l'arrêt). */
  interactive?: boolean;
  /** Affichage temps réel : marqueur de position pulsant, sans geste (sécurité à vélo). */
  live?: boolean;
  /** Ref de la ScrollView parente : le pan de la carte bloque son défilement. */
  scrollRef?: React.RefObject<any>;
  /** Plein écran : la carte remplit son parent (flex), sans bordure ni coins arrondis. */
  fill?: boolean;
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
/**
 * Carte du parcours. Si un style MapLibre auto-hébergé est configuré, affiche
 * un vrai fond de carte (tuiles du homelab) avec le tracé par-dessus ; sinon,
 * retombe sur le tracé SVG sur fond uni (`SvgRoute`).
 */
export function RouteMap(props: Props) {
  const theme = useTheme();
  const [styleUrl, setStyleUrl] = useState<string | null>(null);

  useEffect(() => {
    getMapStyleUrl().then((u) => setStyleUrl(u || null));
  }, []);

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

  // Décor « carte » (grille, échelle, nord) — affiché hors mode live ; en live
  // on garde l'écran épuré (sécurité à vélo).
  const mpp = proj.metersPerUnit;
  const niceM = !live && mpp > 0 ? niceMeters(W * 0.28 * mpp) : 0;
  const barUnits = niceM > 0 ? niceM / mpp : 0;
  const nx = W - 46; // axe de la rose des vents (coin haut-droit)

  const svg = (
    <Svg width="100%" height="100%" viewBox={`0 0 ${W} ${H}`}>
      {/* Grille discrète : repère visuel façon carte, sous le tracé. */}
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

      {/* Halo sous le tracé : profondeur + lisibilité au-dessus de la grille. */}
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

      {/* Échelle de distance (bas-gauche) : repère métrique pour le tracé. */}
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

      {/* Nord (haut-droit) : la projection garde toujours le nord en haut. */}
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
