import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Text, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PressableScale } from '@/components/pressable-scale';
import { RouteMap } from '@/components/route-map';
import { Elevation, Radius } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import { getSession, getTrackPoints } from '@/lib/db';
import type { Session, TrackPoint } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte plein écran d'une sortie. On y arrive en touchant la vignette du tracé
 * sur l'écran de détail : la carte remplit l'écran et devient explorable
 * (pinch-zoom, pan). Le bouton flottant en haut à gauche revient en arrière.
 */
export default function SessionMapScreen() {
  const theme = useTheme();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  // Réactif à la rotation (Dimensions.get est figé au premier rendu).
  const { height: windowHeight } = useWindowDimensions();
  const { id } = useLocalSearchParams<{ id: string }>();
  const sessionId = Number(id);

  const [session, setSession] = useState<Session | null>(null);
  const [points, setPoints] = useState<TrackPoint[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const s = await getSession(sessionId);
      setSession(s);
      if (s?.type === 'velo') setPoints(await getTrackPoints(sessionId));
      setLoading(false);
    })();
  }, [sessionId]);

  const color = session ? theme[ACTIVITY_META[session.type].colorKey] : theme.velo;

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      {loading ? (
        <View style={{ flex: 1, justifyContent: 'center' }}>
          <ActivityIndicator color={theme.accent} />
        </View>
      ) : points.length >= 2 ? (
        <RouteMap
          points={points}
          color={color}
          interactive
          fill
          height={windowHeight}
        />
      ) : (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Text style={{ color: theme.textSecondary }}>Aucun tracé à afficher.</Text>
        </View>
      )}

      {/* Bouton retour flottant — respecte l'encoche, par-dessus la carte. */}
      <PressableScale
        onPress={() => router.back()}
        haptic="selection"
        scaleTo={0.88}
        hitSlop={12}
        accessibilityLabel="Revenir à la séance"
        style={{
          position: 'absolute',
          top: insets.top + 8,
          left: insets.left + 16,
          width: 44,
          height: 44,
          borderRadius: Radius.pill,
          borderCurve: 'continuous',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: theme.backgroundElement,
          ...Elevation.md,
        }}>
        <MaterialCommunityIcons name="arrow-left" size={24} color={theme.text} />
      </PressableScale>
    </View>
  );
}
