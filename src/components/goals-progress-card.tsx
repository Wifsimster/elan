import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Text, View } from 'react-native';

import { Card } from '@/components/card';
import { Type } from '@/constants/theme';
import { describeGoal, formatGoalValue, goalProgressList, type GoalProgress } from '@/lib/goals';
import { nowMs } from '@/lib/time';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte d'accueil : avancement des objectifs sur la période courante. Recharge
 * à chaque focus (la progression évolue dès qu'une séance est enregistrée).
 * Ne s'affiche pas tant qu'aucun objectif n'est défini.
 */
export function GoalsProgressCard() {
  const theme = useTheme();
  const [items, setItems] = useState<GoalProgress[]>([]);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      goalProgressList(nowMs()).then((list) => {
        if (!cancelled) setItems(list);
      });
      return () => {
        cancelled = true;
      };
    }, []),
  );

  if (items.length === 0) return null;

  return (
    <Card style={{ gap: 14 }}>
      <Text style={{ ...Type.headline, color: theme.text }}>Objectifs</Text>
      {items.map((p) => (
        <View key={p.goal.id} style={{ gap: 6 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <Text style={{ color: theme.text, fontSize: 14, fontWeight: '600', flex: 1 }} numberOfLines={1}>
              {describeGoal(p.goal)}
            </Text>
            {p.done ? (
              <MaterialCommunityIcons name="check-circle" size={18} color={theme.success} />
            ) : (
              <Text style={{ color: theme.textSecondary, fontSize: 13, fontVariant: ['tabular-nums'] }}>
                {Math.round(p.ratio * 100)} %
              </Text>
            )}
          </View>
          {/* Barre d'avancement (bornée [0, 1]). */}
          <View
            style={{
              height: 8,
              borderRadius: 4,
              backgroundColor: theme.backgroundSelected,
              overflow: 'hidden',
            }}>
            <View
              style={{
                height: '100%',
                width: `${Math.round(p.ratio * 100)}%`,
                borderRadius: 4,
                backgroundColor: p.done ? theme.success : theme.accent,
              }}
            />
          </View>
          <Text style={{ color: theme.textMuted, fontSize: 12 }}>
            {formatGoalValue(p.goal, p.value)} réalisé
          </Text>
        </View>
      ))}
    </Card>
  );
}
