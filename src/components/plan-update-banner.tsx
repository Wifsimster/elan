import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { Text, View } from 'react-native';

import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { Radius, Type } from '@/constants/theme';
import {
  changeSummaryLine,
  dismissProgressionBanner,
  getAutoProgressionState,
  isoWeekKey,
  type AutoProgressionState,
} from '@/lib/auto-progression';
import { nowMs } from '@/lib/time';
import { useTheme } from '@/hooks/use-theme';

/**
 * Bannière d'accueil annonçant que le programme muscu a été relevé pour la
 * semaine (progression auto). Canal garanti, sans permission notification :
 * s'affiche tant que l'utilisateur ne l'a pas masquée et qu'on est bien dans la
 * semaine des changements. Touche la bannière -> revue sur la page Progression.
 */
export function PlanUpdateBanner() {
  const theme = useTheme();
  const router = useRouter();
  const [state, setState] = useState<AutoProgressionState | null>(null);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      getAutoProgressionState().then((s) => {
        if (!cancelled) setState(s);
      });
      return () => {
        cancelled = true;
      };
    }, []),
  );

  if (!state) return null;
  const currentWeek = isoWeekKey(new Date(nowMs()));
  if (state.week !== currentWeek || state.dismissed || state.changes.length === 0) return null;

  const dismiss = async () => {
    setState({ ...state, dismissed: true });
    await dismissProgressionBanner();
  };

  return (
    <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
      <PressableScale
        onPress={() => router.push('/progression')}
        haptic="light"
        style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View
          style={{
            width: 42,
            height: 42,
            borderRadius: Radius.sm,
            borderCurve: 'continuous',
            backgroundColor: theme.muscu + '22',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
          <MaterialCommunityIcons name="trending-up" size={22} color={theme.muscu} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={{ ...Type.subtitle, color: theme.text }}>Programme mis à jour cette semaine</Text>
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {changeSummaryLine(state.changes)}
          </Text>
        </View>
      </PressableScale>
      <PressableScale
        onPress={dismiss}
        haptic="selection"
        hitSlop={8}
        accessibilityLabel="Masquer l'annonce de progression">
        <MaterialCommunityIcons name="close" size={20} color={theme.textMuted} />
      </PressableScale>
    </Card>
  );
}
