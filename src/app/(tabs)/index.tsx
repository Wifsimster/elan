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
import { StatTile, type Trend } from '@/components/stat-tile';
import { Radius, Type } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import { dailyDurations, listSessions, statsBetween, statsSince } from '@/lib/db';
import { hasMuscuDraft } from '@/lib/muscu-draft';
import {
  DEFAULT_WEEK_PLAN,
  getEffectiveWeekPlan,
  planForDay,
  templateById,
  type PlannedSession,
} from '@/lib/program';
import {
  formatDistance,
  formatDurationShort,
  formatDateTime,
  formatRelativeDays,
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
const WEEK_MS = 7 * 86_400_000;

/** Construit l'évolution d'une métrique par rapport à la semaine précédente. */
function buildTrend(current: number, previous: number, fmt: (n: number) => string): Trend {
  const delta = current - previous;
  if (delta === 0) return { label: 'stable', tone: 'neutral' };
  const sign = delta > 0 ? '+' : '−';
  return { label: `${sign}${fmt(Math.abs(delta))}`, tone: delta > 0 ? 'positive' : 'negative' };
}

export default function HomeScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const [stats, setStats] = useState<PeriodStats | null>(null);
  const [lastStats, setLastStats] = useState<PeriodStats | null>(null);
  const [bars, setBars] = useState<Bar[]>([]);
  const [recent, setRecent] = useState<Session[]>([]);
  const [resumable, setResumable] = useState(false);

  const load = useCallback(async () => {
    const weekStart = startOfWeek();
    const [s, prev, daily, sessions, draft] = await Promise.all([
      statsSince(weekStart),
      statsBetween(weekStart - WEEK_MS, weekStart),
      dailyDurations(7),
      listSessions(3),
      hasMuscuDraft(),
    ]);
    setStats(s);
    setLastStats(prev);
    setResumable(draft);

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
          <Text style={{ ...Type.title, color: theme.text }}>Élan</Text>
        </View>
        <HrBadge />
      </View>

      {/* Séance du jour (programme perso) */}
      <TodayCard lastSessionAt={recent[0]?.startedAt ?? null} resumable={resumable} />

      {/* Démarrer une séance — ou reprendre la muscu en pause */}
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Button title="Vélo" icon="bike" size="lg" color={theme.velo} onPress={() => router.push('/velo')} />
        </View>
        <View style={{ flex: 1 }}>
          <Button
            title={resumable ? 'Reprendre' : 'Muscu'}
            icon={resumable ? 'play' : 'dumbbell'}
            size="lg"
            color={theme.muscu}
            onPress={() => router.push('/muscu')}
          />
        </View>
      </View>

      {/* Résumé de la semaine */}
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <Text style={{ ...Type.headline, color: theme.text }}>Cette semaine</Text>
          {stats && lastStats ? (
            <Text style={{ ...Type.caption, color: theme.textMuted }}>vs sem. dernière</Text>
          ) : null}
        </View>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 16 }}>
          <StatTile
            label="Séances"
            value={String(stats?.sessionCount ?? 0)}
            icon="calendar-check"
            compact
            trend={
              stats && lastStats
                ? buildTrend(stats.sessionCount, lastStats.sessionCount, (n) => String(n))
                : undefined
            }
          />
          <StatTile
            label="Durée"
            value={formatDurationShort(stats?.totalDurationSec ?? 0)}
            icon="clock-outline"
            compact
            trend={
              stats && lastStats
                ? buildTrend(stats.totalDurationSec, lastStats.totalDurationSec, formatDurationShort)
                : undefined
            }
          />
          <StatTile
            label="Distance"
            value={formatDistance(stats?.totalDistanceM ?? 0)}
            icon="map-marker-distance"
            color={theme.velo}
            compact
            trend={
              stats && lastStats
                ? buildTrend(stats.totalDistanceM, lastStats.totalDistanceM, formatDistance)
                : undefined
            }
          />
          <StatTile
            label="Calories"
            value={String(Math.round(stats?.totalCalories ?? 0))}
            unit="kcal"
            icon="fire"
            color={theme.warning}
            compact
            trend={
              stats && lastStats
                ? buildTrend(stats.totalCalories, lastStats.totalCalories, (n) =>
                    String(Math.round(n)),
                  )
                : undefined
            }
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

const WEEKDAYS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];

function TodayCard({
  lastSessionAt,
  resumable,
}: {
  lastSessionAt: number | null;
  /** Une séance muscu est en pause : le bouton invite à reprendre plutôt qu'à démarrer. */
  resumable: boolean;
}) {
  const theme = useTheme();
  const router = useRouter();
  const jsDay = new Date().getDay();
  const [weekPlan, setWeekPlan] = useState<PlannedSession[]>(DEFAULT_WEEK_PLAN);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      getEffectiveWeekPlan().then((p) => {
        if (!cancelled) setWeekPlan(p);
      });
      return () => {
        cancelled = true;
      };
    }, []),
  );

  const plan = planForDay(jsDay, weekPlan);
  const dayName = WEEKDAYS[jsDay];
  const lastLabel = lastSessionAt != null ? `Dernière séance ${formatRelativeDays(lastSessionAt)}` : null;

  if (plan.kind === 'repos') {
    return (
      <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        <View
          style={{
            width: 46,
            height: 46,
            borderRadius: Radius.sm,
            borderCurve: 'continuous',
            backgroundColor: theme.textMuted + '22',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
          <MaterialCommunityIcons name="sleep" size={24} color={theme.textSecondary} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={{ ...Type.label, color: theme.textSecondary }}>Aujourd’hui · {dayName}</Text>
          <Text style={{ ...Type.subtitle, color: theme.text }}>Repos</Text>
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            Récupération — pas de séance prévue.
          </Text>
          {lastLabel ? (
            <Text style={{ color: theme.textMuted, fontSize: 12, marginTop: 2 }}>{lastLabel}</Text>
          ) : null}
        </View>
      </Card>
    );
  }

  const isVelo = plan.kind === 'velo';
  // Séance muscu du jour mise en pause : on propose de la reprendre.
  const isResumeMuscu = plan.kind === 'muscu' && resumable;
  const color = isVelo ? theme.velo : theme.muscu;
  const icon = isVelo ? 'bike' : 'dumbbell';
  const tmpl = plan.kind === 'muscu' ? templateById(plan.templateId) : undefined;
  const subtitle = isVelo
    ? 'Récup active / cardio'
    : tmpl?.exercises.map((e) => e.name).join(' · ');

  const start = () =>
    isVelo
      ? router.push('/velo')
      : router.push({ pathname: '/muscu', params: { template: (plan as { templateId: string }).templateId } });

  return (
    <Card style={{ gap: 12 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
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
          <MaterialCommunityIcons name={icon} size={24} color={color} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={{ ...Type.label, color: theme.textSecondary }}>Aujourd’hui · {dayName}</Text>
          <Text style={{ ...Type.subtitle, color: theme.text }}>{plan.label}</Text>
          {subtitle ? (
            <Text style={{ color: theme.textSecondary, fontSize: 13 }} numberOfLines={2}>
              {subtitle}
            </Text>
          ) : null}
          {lastLabel ? (
            <Text style={{ color: theme.textMuted, fontSize: 12, marginTop: 2 }}>{lastLabel}</Text>
          ) : null}
        </View>
      </View>
      <Button
        title={isResumeMuscu ? 'Reprendre' : 'Démarrer'}
        icon="play"
        color={color}
        onPress={start}
      />
    </Card>
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
