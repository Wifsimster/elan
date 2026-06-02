import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BarChart, type Bar } from '@/components/bar-chart';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { HrBadge } from '@/components/hr-badge';
import { PressableScale } from '@/components/pressable-scale';
import { StatTile } from '@/components/stat-tile';
import { Radius, Type } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import { dailyDurations, listSessions, statsSince } from '@/lib/db';
import {
  formatDistance,
  formatDurationShort,
  formatDateTime,
} from '@/lib/format';
import type { PeriodStats, Session } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

function startOfWeek(): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  const day = (d.getDay() + 6) % 7; // lundi = 0
  d.setDate(d.getDate() - day);
  return d.getTime();
}

const DAY_LABELS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

export default function HomeScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const [stats, setStats] = useState<PeriodStats | null>(null);
  const [bars, setBars] = useState<Bar[]>([]);
  const [recent, setRecent] = useState<Session[]>([]);

  const load = useCallback(async () => {
    const [s, daily, sessions] = await Promise.all([
      statsSince(startOfWeek()),
      dailyDurations(7),
      listSessions(3),
    ]);
    setStats(s);

    // Construit 7 barres (aujourd'hui à droite).
    const byDay = new Map(daily.map((d) => [d.day, d.durationSec]));
    const out: Bar[] = [];
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate(),
      ).padStart(2, '0')}`;
      out.push({ label: DAY_LABELS[(d.getDay() + 6) % 7], value: byDay.get(key) ?? 0 });
    }
    setBars(out);
    setRecent(sessions);
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 32,
        paddingHorizontal: 16,
        gap: 16,
      }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View style={{ gap: 2 }}>
          <Text style={{ ...Type.label, color: theme.textSecondary }}>Bonjour 👋</Text>
          <Text style={{ ...Type.title, color: theme.text }}>Suivi Sport</Text>
        </View>
        <HrBadge />
      </View>

      {/* Démarrer une séance */}
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Button title="Vélo" icon="bike" size="lg" color={theme.velo} onPress={() => router.push('/velo')} />
        </View>
        <View style={{ flex: 1 }}>
          <Button title="Muscu" icon="dumbbell" size="lg" color={theme.muscu} onPress={() => router.push('/muscu')} />
        </View>
      </View>

      {/* Résumé de la semaine */}
      <Card>
        <Text style={{ ...Type.headline, color: theme.text }}>Cette semaine</Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 16 }}>
          <StatTile
            label="Séances"
            value={String(stats?.sessionCount ?? 0)}
            icon="calendar-check"
            compact
          />
          <StatTile
            label="Durée"
            value={formatDurationShort(stats?.totalDurationSec ?? 0)}
            icon="clock-outline"
            compact
          />
          <StatTile
            label="Distance"
            value={formatDistance(stats?.totalDistanceM ?? 0)}
            icon="map-marker-distance"
            color={theme.velo}
            compact
          />
          <StatTile
            label="Calories"
            value={String(Math.round(stats?.totalCalories ?? 0))}
            unit="kcal"
            icon="fire"
            color={theme.warning}
            compact
          />
        </View>
      </Card>

      {/* Graphe 7 jours */}
      <Card>
        <Text style={{ ...Type.headline, color: theme.text }}>Activité (7 derniers jours)</Text>
        <BarChart data={bars} gradient="accent" formatValue={(v) => formatDurationShort(v)} />
      </Card>

      {/* Séances récentes */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ ...Type.headline, color: theme.text }}>Récent</Text>
        <Link href="/history" style={{ color: theme.accent, fontWeight: '700' }}>
          Tout voir
        </Link>
      </View>

      {recent.length === 0 ? (
        <Card>
          <EmptyState
            icon="run-fast"
            title="Aucune séance"
            subtitle="Démarrez une sortie vélo ou une séance de muscu pour commencer."
          />
        </Card>
      ) : (
        <View style={{ gap: 10 }}>
          {recent.map((s) => (
            <RecentRow key={s.id} session={s} />
          ))}
        </View>
      )}
    </ScrollView>
  );
}

function RecentRow({ session }: { session: Session }) {
  const theme = useTheme();
  const meta = ACTIVITY_META[session.type];
  const color = theme[meta.colorKey];

  return (
    <Link href={{ pathname: '/session/[id]', params: { id: session.id } }} asChild>
      <PressableScale>
        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14 }}>
          <View
            style={{
              width: 46,
              height: 46,
              borderRadius: Radius.sm,
              borderCurve: 'continuous',
              backgroundColor: color + '22',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <MaterialCommunityIcons name={meta.icon} size={24} color={color} />
          </View>
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={{ ...Type.subtitle, color: theme.text }}>{meta.label}</Text>
            <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
              {formatDateTime(session.startedAt)}
            </Text>
          </View>
          <View style={{ alignItems: 'flex-end', gap: 2 }}>
            <Text style={{ color: theme.text, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
              {formatDurationShort(session.durationSec)}
            </Text>
            {session.type === 'velo' && session.distanceM != null ? (
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                {formatDistance(session.distanceM)}
              </Text>
            ) : null}
          </View>
          <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
        </Card>
      </PressableScale>
    </Link>
  );
}
