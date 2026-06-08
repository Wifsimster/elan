import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { PressableScale } from '@/components/pressable-scale';
import { Radius, Type } from '@/constants/theme';
import { listMuscuExercises, type ExerciseSummary } from '@/lib/db';
import { formatDateShort } from '@/lib/format';
import { useScreenContentStyle } from '@/hooks/use-screen-layout';
import { useTheme } from '@/hooks/use-theme';

const fmtKg = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

export default function ProgressionScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const contentStyle = useScreenContentStyle();
  const router = useRouter();
  // null = chargement en cours (évite un flash d'état vide avant la 1re requête).
  const [items, setItems] = useState<ExerciseSummary[] | null>(null);

  useFocusEffect(
    useCallback(() => {
      listMuscuExercises().then(setItems);
    }, []),
  );

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        ...contentStyle,
        paddingTop: 12,
        paddingBottom: insets.bottom + 32,
        gap: 12,
      }}>
      <Text style={{ ...Type.label, color: theme.textSecondary }}>
        Suivi des charges, exercice par exercice. Touche un exercice pour voir sa courbe.
      </Text>

      {items === null ? null : items.length === 0 ? (
        <EmptyState
          icon="chart-line"
          tint={theme.muscu}
          title="Pas encore de données"
          subtitle="Enregistre quelques séances de muscu pour suivre ta progression."
          action={{
            label: 'Démarrer une séance muscu',
            icon: 'dumbbell',
            onPress: () => router.push('/muscu'),
          }}
        />
      ) : (
        items.map((it) => (
          <Link
            key={it.exercise}
            href={{ pathname: '/exercise/[name]', params: { name: it.exercise } }}
            asChild>
            <PressableScale>
              <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14 }}>
                <View
                  style={{
                    width: 46,
                    height: 46,
                    borderRadius: Radius.sm,
                    borderCurve: 'continuous',
                    backgroundColor: theme.muscu + '22',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}>
                  <MaterialCommunityIcons name="dumbbell" size={22} color={theme.muscu} />
                </View>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={{ ...Type.subtitle, color: theme.text }} numberOfLines={1}>
                    {it.exercise}
                  </Text>
                  <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                    {it.sessions} séance{it.sessions > 1 ? 's' : ''} · dernière {formatDateShort(it.lastAt)}
                  </Text>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <Text style={{ color: theme.muscu, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                    {fmtKg(it.lastWeightKg)} kg
                  </Text>
                </View>
                <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
              </Card>
            </PressableScale>
          </Link>
        ))
      )}
    </ScrollView>
  );
}
