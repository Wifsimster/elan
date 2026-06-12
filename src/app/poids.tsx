import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { LineChart } from '@/components/line-chart';
import { Radius, Type } from '@/constants/theme';
import {
  deleteBodyMeasurement,
  getProfile,
  listBodyMeasurements,
  logBodyWeight,
} from '@/lib/db';
import { formatDateShort, formatDateTime } from '@/lib/format';
import { nowMs } from '@/lib/time';
import type { BodyMeasurement } from '@/lib/types';
import { useScreenContentStyle } from '@/hooks/use-screen-layout';
import { useTheme } from '@/hooks/use-theme';

const DAY = 86_400_000;

/** 76.4 -> « 76,4 » ; 76 -> « 76 ». */
const fmtKg = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

/** Variation signée : +0,4 / −1,2 / 0. */
const fmtDelta = (v: number) => (v > 0 ? `+${fmtKg(v)}` : v < 0 ? `−${fmtKg(Math.abs(v))}` : '0');

/**
 * Saisie utilisateur (« 76,4 », « 76.4 », « 76 ») -> poids en kg arrondi au
 * dixième, ou `null` si invalide / hors plage plausible.
 */
function parseWeightInput(text: string): number | null {
  const v = Number.parseFloat(text.trim().replace(',', '.'));
  if (!Number.isFinite(v)) return null;
  const rounded = Math.round(v * 10) / 10;
  if (rounded < 20 || rounded > 300) return null;
  return rounded;
}

export default function PoidsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const contentStyle = useScreenContentStyle();

  // null = chargement (évite un flash d'état vide), sinon de la plus récente
  // à la plus ancienne (ordre de listBodyMeasurements).
  const [items, setItems] = useState<BodyMeasurement[] | null>(null);
  const [input, setInput] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const list = await listBodyMeasurements();
    setItems(list);
    // Pré-remplit la saisie avec le dernier poids connu (journal, sinon profil).
    if (list.length > 0) {
      setInput(fmtKg(list[0].weightKg));
    } else {
      const profile = await getProfile();
      setInput(fmtKg(profile.weightKg));
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const save = async () => {
    const weightKg = parseWeightInput(input);
    if (weightKg == null) {
      setError('Poids invalide : saisis une valeur entre 20 et 300 kg (ex. 76,4).');
      return;
    }
    setError(null);
    await logBodyWeight(weightKg, nowMs());
    await load();
  };

  const confirmDelete = (m: BodyMeasurement) => {
    Alert.alert(
      'Supprimer cette pesée ?',
      `${fmtKg(m.weightKg)} kg — ${formatDateTime(m.measuredAt, true)}`,
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Supprimer',
          style: 'destructive',
          onPress: async () => {
            await deleteBodyMeasurement(m.id);
            await load();
          },
        },
      ],
    );
  };

  // Séries dérivées (entrées triées de la plus récente à la plus ancienne).
  const latest = items?.[0] ?? null;
  const oldest = items && items.length > 0 ? items[items.length - 1] : null;
  const monthAgo = latest ? findBefore(items!, latest.measuredAt - 30 * DAY) : null;
  const chartData = (items ?? [])
    .slice()
    .reverse()
    .map((m) => ({ x: m.measuredAt, y: m.weightKg }));

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        ...contentStyle,
        paddingTop: 12,
        paddingBottom: insets.bottom + 32,
        gap: 14,
      }}>
      <Text style={{ ...Type.label, color: theme.textSecondary }}>
        {'Note ton poids régulièrement : la dernière pesée sert de référence pour les calories et les charges conseillées. Tout reste sur l’appareil.'}
      </Text>

      {/* Nouvelle pesée */}
      <Card style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="scale-bathroom" size={22} color={theme.accent} />
          <Text style={{ ...Type.sectionTitle, color: theme.text }}>Nouvelle pesée</Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <TextInput
            value={input}
            onChangeText={setInput}
            keyboardType="decimal-pad"
            maxLength={6}
            accessibilityLabel="Poids en kilogrammes"
            style={{
              flex: 1,
              color: theme.text,
              backgroundColor: theme.backgroundSelected,
              borderRadius: Radius.sm,
              borderCurve: 'continuous',
              paddingHorizontal: 12,
              paddingVertical: 10,
              fontSize: 17,
              fontWeight: '800',
              fontVariant: ['tabular-nums'],
              borderWidth: 1,
              borderColor: theme.border,
            }}
          />
          <Text style={{ ...Type.subtitle, color: theme.textSecondary }}>kg</Text>
        </View>
        <Button title="Enregistrer" icon="check" color={theme.accent} onPress={save} />
        {error ? <Text style={{ color: theme.danger, fontSize: 13 }}>{error}</Text> : null}
      </Card>

      {items !== null && items.length === 0 ? (
        <EmptyState
          icon="scale-bathroom"
          tint={theme.accent}
          title="Aucune pesée pour l’instant"
          subtitle="Enregistre ta première pesée pour suivre l’évolution de ton poids."
        />
      ) : null}

      {latest ? (
        <Card style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
          <Metric label="Actuel" value={`${fmtKg(latest.weightKg)} kg`} color={theme.accent} />
          <Metric
            label="30 jours"
            value={monthAgo ? `${fmtDelta(latest.weightKg - monthAgo.weightKg)} kg` : '—'}
          />
          <Metric
            label="Depuis le début"
            value={oldest ? `${fmtDelta(latest.weightKg - oldest.weightKg)} kg` : '—'}
          />
        </Card>
      ) : null}

      {chartData.length >= 2 ? (
        <Card>
          <Text style={{ ...Type.headline, color: theme.text }}>Évolution</Text>
          <LineChart
            data={chartData}
            color={theme.accent}
            label="Poids"
            formatY={(v) => fmtKg(Math.round(v * 10) / 10)}
            formatX={(x) => formatDateShort(x)}
          />
        </Card>
      ) : null}

      {items && items.length > 0 ? (
        <>
          <Text style={{ ...Type.headline, color: theme.text }}>Pesées</Text>
          <Card style={{ gap: 0, paddingVertical: 4 }}>
            {items.map((m, i) => {
              const prev = items[i + 1];
              return (
                <View
                  key={m.id}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 12,
                    paddingVertical: 12,
                    borderTopWidth: i === 0 ? 0 : 1,
                    borderTopColor: theme.hairline,
                  }}>
                  <View style={{ flex: 1, gap: 2 }}>
                    <Text style={{ ...Type.subtitle, color: theme.text, fontVariant: ['tabular-nums'] }}>
                      {fmtKg(m.weightKg)} kg
                    </Text>
                    <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                      {formatDateTime(m.measuredAt, true)}
                    </Text>
                  </View>
                  {prev ? (
                    <Text
                      style={{
                        color: theme.textSecondary,
                        fontSize: 13,
                        fontWeight: '700',
                        fontVariant: ['tabular-nums'],
                      }}>
                      {fmtDelta(m.weightKg - prev.weightKg)} kg
                    </Text>
                  ) : null}
                  <Pressable
                    onPress={() => confirmDelete(m)}
                    hitSlop={12}
                    accessibilityRole="button"
                    accessibilityLabel={`Supprimer la pesée du ${formatDateTime(m.measuredAt, true)}`}>
                    <MaterialCommunityIcons name="trash-can-outline" size={20} color={theme.textMuted} />
                  </Pressable>
                </View>
              );
            })}
          </Card>
        </>
      ) : null}
    </ScrollView>
  );
}

/** Pesée la plus récente antérieure ou égale à `beforeMs` (liste triée desc). */
function findBefore(items: BodyMeasurement[], beforeMs: number): BodyMeasurement | null {
  for (const m of items) {
    if (m.measuredAt <= beforeMs) return m;
  }
  return null;
}

function Metric({ label, value, color }: { label: string; value: string; color?: string }) {
  const theme = useTheme();
  return (
    <View style={{ alignItems: 'center', gap: 2 }}>
      <Text style={{ ...Type.metric, fontSize: 20, color: color ?? theme.text }}>{value}</Text>
      <Text style={{ ...Type.caption, color: theme.textSecondary }}>{label}</Text>
    </View>
  );
}
