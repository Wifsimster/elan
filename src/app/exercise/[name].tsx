import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BarChart, type Bar } from '@/components/bar-chart';
import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { PressableScale } from '@/components/pressable-scale';
import { Type } from '@/constants/theme';
import { exerciseHistory, type ExercisePoint } from '@/lib/db';
import { formatDateTime } from '@/lib/format';
import { epley1RM } from '@/lib/strength';
import { useTheme } from '@/hooks/use-theme';

const fmtKg = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

export default function ExerciseScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { name } = useLocalSearchParams<{ name: string }>();
  const exercise = typeof name === 'string' ? name : '';

  const [points, setPoints] = useState<ExercisePoint[] | null>(null);

  useEffect(() => {
    if (exercise) exerciseHistory(exercise).then(setPoints);
  }, [exercise]);

  const best = points && points.length ? Math.max(...points.map((p) => p.maxWeightKg)) : 0;
  const first = points && points.length ? points[0].maxWeightKg : 0;
  const last = points && points.length ? points[points.length - 1].maxWeightKg : 0;
  const delta = last - first;

  // Force estimée (1RM Epley) à partir de la série la plus lourde de chaque séance.
  // Vaut 0 pour les exercices sans charge (gainage chronométré).
  const best1rm = points && points.length
    ? Math.max(...points.map((p) => epley1RM(p.maxWeightKg, p.topReps)))
    : 0;
  const last1rm = points && points.length
    ? epley1RM(points[points.length - 1].maxWeightKg, points[points.length - 1].topReps)
    : 0;

  // Courbe de la charge maximale, 10 dernières séances.
  const bars: Bar[] = (points ?? []).slice(-10).map((p) => {
    const d = new Date(p.startedAt);
    return { label: `${d.getDate()}/${d.getMonth() + 1}`, value: p.maxWeightKg };
  });

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        paddingTop: 12,
        paddingBottom: insets.bottom + 32,
        paddingHorizontal: 16,
        gap: 14,
      }}>
      <Text style={{ ...Type.title, color: theme.text }}>{exercise}</Text>

      {points && points.length === 0 ? (
        <EmptyState
          icon="chart-line"
          title="Aucune donnée"
          subtitle="Cet exercice n'a pas encore de séance enregistrée."
        />
      ) : null}

      {points && points.length > 0 ? (
        <>
          {/* Résumé */}
          <Card style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
            <Metric label="Record" value={`${fmtKg(best)} kg`} theme={theme} color={theme.muscu} />
            <Metric label="Actuel" value={`${fmtKg(last)} kg`} theme={theme} />
            <Metric
              label="Depuis le début"
              value={`${delta >= 0 ? '+' : ''}${fmtKg(delta)} kg`}
              theme={theme}
              color={delta > 0 ? theme.success : delta < 0 ? theme.warning : theme.textSecondary}
            />
          </Card>

          {/* Force estimée (1RM) — masquée pour les exercices sans charge */}
          {best1rm > 0 ? (
            <Card style={{ gap: 10 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
                <Metric label="1RM actuel" value={`${Math.round(last1rm)} kg`} theme={theme} />
                <Metric
                  label="1RM record"
                  value={`${Math.round(best1rm)} kg`}
                  theme={theme}
                  color={theme.muscu}
                />
              </View>
              <Text style={{ ...Type.caption, color: theme.textMuted, textAlign: 'center' }}>
                Estimation Epley d’après la série la plus lourde de chaque séance.
              </Text>
            </Card>
          ) : null}

          {/* Courbe des charges */}
          <Card>
            <Text style={{ ...Type.headline, color: theme.text }}>Charge max par séance</Text>
            <BarChart data={bars} gradient="muscu" formatValue={(v) => `${fmtKg(v)} kg`} />
          </Card>

          {/* Détail des séances (récentes d'abord) */}
          <Text style={{ ...Type.headline, color: theme.text }}>Séances</Text>
          <View style={{ gap: 10 }}>
            {[...points].reverse().map((p) => (
              <Link
                key={p.sessionId}
                href={{ pathname: '/session/[id]', params: { id: p.sessionId } }}
                asChild>
                <PressableScale>
                  <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 14 }}>
                    <View style={{ flex: 1, gap: 2 }}>
                      <Text style={{ ...Type.subtitle, color: theme.text }}>
                        {fmtKg(p.maxWeightKg)} kg × {p.topReps}
                      </Text>
                      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                        {formatDateTime(p.startedAt)}
                      </Text>
                    </View>
                    <View style={{ alignItems: 'flex-end', gap: 2 }}>
                      {epley1RM(p.maxWeightKg, p.topReps) > 0 ? (
                        <Text
                          style={{
                            color: theme.muscu,
                            fontSize: 13,
                            fontWeight: '700',
                            fontVariant: ['tabular-nums'],
                          }}>
                          ≈ {Math.round(epley1RM(p.maxWeightKg, p.topReps))} kg 1RM
                        </Text>
                      ) : null}
                      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                        {p.sets} série{p.sets > 1 ? 's' : ''}
                      </Text>
                      <Text style={{ color: theme.textSecondary, fontSize: 13, fontVariant: ['tabular-nums'] }}>
                        {Math.round(p.volume)} kg vol.
                      </Text>
                    </View>
                    <MaterialCommunityIcons name="chevron-right" size={20} color={theme.textMuted} />
                  </Card>
                </PressableScale>
              </Link>
            ))}
          </View>
        </>
      ) : null}
    </ScrollView>
  );
}

function Metric({
  label,
  value,
  theme,
  color,
}: {
  label: string;
  value: string;
  theme: ReturnType<typeof useTheme>;
  color?: string;
}) {
  return (
    <View style={{ alignItems: 'center', gap: 2 }}>
      <Text style={{ ...Type.metric, fontSize: 20, color: color ?? theme.text }}>{value}</Text>
      <Text style={{ ...Type.caption, color: theme.textSecondary }}>{label}</Text>
    </View>
  );
}
