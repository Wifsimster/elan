import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, useFocusEffect } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { EmptyState } from '@/components/empty-state';
import { PressableScale } from '@/components/pressable-scale';
import { SessionRow } from '@/components/session-row';
import { Radius, Type } from '@/constants/theme';
import { listSessions } from '@/lib/db';
import type { ActivityType, Session } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

type Filter = 'all' | ActivityType;

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'Tout' },
  { key: 'velo', label: 'Vélo' },
  { key: 'muscu', label: 'Muscu' },
];

export default function HistoryScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [sessions, setSessions] = useState<Session[]>([]);
  const [filter, setFilter] = useState<Filter>('all');

  useFocusEffect(
    useCallback(() => {
      listSessions(200).then(setSessions);
    }, []),
  );

  const filtered = useMemo(
    () => (filter === 'all' ? sessions : sessions.filter((s) => s.type === filter)),
    [sessions, filter],
  );

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 32,
        paddingHorizontal: 16,
        gap: 14,
      }}>
      <Text style={{ ...Type.title, color: theme.text }}>Historique</Text>

      <Link href="/progression" asChild>
        <PressableScale>
          <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14 }}>
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
              <MaterialCommunityIcons name="chart-line" size={22} color={theme.muscu} />
            </View>
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={{ ...Type.subtitle, color: theme.text }}>Progression muscu</Text>
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                Suivi des charges par exercice
              </Text>
            </View>
            <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
          </Card>
        </PressableScale>
      </Link>

      <View style={{ flexDirection: 'row', gap: 8 }}>
        {FILTERS.map((f) => (
          <Chip
            key={f.key}
            label={f.label}
            selected={filter === f.key}
            color={f.key === 'velo' ? theme.velo : f.key === 'muscu' ? theme.muscu : theme.accent}
            onPress={() => setFilter(f.key)}
          />
        ))}
      </View>

      {filtered.length === 0 ? (
        <EmptyState
          icon="clipboard-text-clock-outline"
          title="Rien à afficher"
          subtitle="Vos séances enregistrées apparaîtront ici."
        />
      ) : (
        <View style={{ gap: 10 }}>
          {filtered.map((s) => (
            <SessionRow key={s.id} session={s} />
          ))}
        </View>
      )}
    </ScrollView>
  );
}
