import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { PressableScale } from '@/components/pressable-scale';
import { Radius, Type } from '@/constants/theme';
import {
  changeSummaryLine,
  describeChange,
  getAutoProgressionState,
  isoWeekKey,
  type ProgressionChange,
} from '@/lib/auto-progression';
import { listMuscuExercises, type ExerciseSummary } from '@/lib/db';
import { formatDateShort } from '@/lib/format';
import { nowMs } from '@/lib/time';
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
  // Changements de la progression auto pour la semaine en cours (revue).
  const [changes, setChanges] = useState<ProgressionChange[]>([]);

  useFocusEffect(
    useCallback(() => {
      listMuscuExercises().then(setItems);
      getAutoProgressionState().then((s) => {
        const currentWeek = isoWeekKey(new Date(nowMs()));
        setChanges(s.week === currentWeek ? s.changes : []);
      });
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
      {changes.length > 0 ? (
        <Card style={{ gap: 10 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <MaterialCommunityIcons name="trending-up" size={20} color={theme.muscu} />
            <Text style={{ ...Type.subtitle, color: theme.text }}>Changements de la semaine</Text>
          </View>
          <Text style={{ ...Type.caption, color: theme.textSecondary, marginTop: -4 }}>
            {`Programme relevé selon ton ressenti · ${changeSummaryLine(changes)}. Ces charges sont pré-remplies à ta prochaine séance — modifiables à tout moment.`}
          </Text>
          {changes.map((c) => (
            <View
              key={c.exercise}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
              <MaterialCommunityIcons
                name={c.direction === 'up' ? 'arrow-up-bold' : 'arrow-down-bold'}
                size={18}
                color={c.direction === 'up' ? theme.success : theme.warning}
              />
              <Text style={{ color: theme.text, fontSize: 14, flex: 1 }} numberOfLines={1}>
                {describeChange(c)}
              </Text>
            </View>
          ))}
        </Card>
      ) : null}

      <Text style={{ ...Type.label, color: theme.textSecondary }}>
        Suivi des charges, exercice par exercice. Touche un exercice pour voir sa courbe.
      </Text>

      {items === null ? null : items.length === 0 ? (
        <EmptyState
          icon="chart-line"
          tint={theme.muscu}
          title="Ta progression démarre ici"
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
                {/* Indice rapide d'après le dernier ressenti : facile = on peut
                    monter, dur = on lève le pied. Le conseil complet (sur tout
                    l'historique) est sur la fiche de l'exercice. */}
                {it.lastDifficulty === 'facile' ? (
                  <MaterialCommunityIcons
                    name="arrow-up-bold"
                    size={18}
                    color={theme.success}
                    accessibilityLabel="Ressenti facile : tu peux monter la charge"
                  />
                ) : it.lastDifficulty === 'dur' ? (
                  <MaterialCommunityIcons
                    name="arrow-down-bold"
                    size={18}
                    color={theme.warning}
                    accessibilityLabel="Ressenti dur : lève le pied"
                  />
                ) : null}
                <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
              </Card>
            </PressableScale>
          </Link>
        ))
      )}
    </ScrollView>
  );
}
