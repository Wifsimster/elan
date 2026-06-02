import { useFocusEffect } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EmptyState } from '@/components/empty-state';
import { SessionRow } from '@/components/session-row';
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
      <Text style={{ color: theme.text, fontSize: 28, fontWeight: '800' }}>Historique</Text>

      <View style={{ flexDirection: 'row', gap: 8 }}>
        {FILTERS.map((f) => {
          const active = filter === f.key;
          return (
            <Pressable
              key={f.key}
              onPress={() => setFilter(f.key)}
              style={{
                paddingHorizontal: 16,
                paddingVertical: 8,
                borderRadius: 999,
                backgroundColor: active ? theme.accent : theme.backgroundElement,
                borderWidth: 1,
                borderColor: active ? theme.accent : theme.border,
              }}>
              <Text style={{ color: active ? '#fff' : theme.text, fontWeight: '700', fontSize: 14 }}>
                {f.label}
              </Text>
            </Pressable>
          );
        })}
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
