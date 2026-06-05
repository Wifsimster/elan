import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link, Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';
import Animated, { useAnimatedRef } from 'react-native-reanimated';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { LineChart } from '@/components/line-chart';
import { RouteMap } from '@/components/route-map';
import { ShareCard } from '@/components/share-card';
import { StatTile } from '@/components/stat-tile';
import { Radius, Type } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import { elevationProfile, hrProfile, speedProfile } from '@/lib/chart-data';
import {
  deleteSession,
  getMuscuSets,
  getProfile,
  getSession,
  getTrackPoints,
  sessionRecords,
  type RecordKind,
  type SessionRecord,
} from '@/lib/db';
import { sessionEffort } from '@/lib/effort';
import {
  formatCalories,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatHr,
  formatSpeed,
} from '@/lib/format';
import type { MuscuSet, Session, TrackPoint } from '@/lib/types';
import { useSessionShare } from '@/hooks/use-session-share';
import { useTheme } from '@/hooks/use-theme';

export default function SessionDetailScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const sessionId = Number(id);

  const [session, setSession] = useState<Session | null>(null);
  const [points, setPoints] = useState<TrackPoint[]>([]);
  const [sets, setSets] = useState<MuscuSet[]>([]);
  const [records, setRecords] = useState<SessionRecord[]>([]);
  const [maxHr, setMaxHr] = useState(0);
  const [loading, setLoading] = useState(true);
  const share = useSessionShare();
  // Ref de la carte partageable (capturée en PNG lors du partage).
  const shareCardRef = useRef<View>(null);
  // Ref de la ScrollView : permet au pan de la carte de bloquer le défilement.
  const scrollRef = useAnimatedRef<Animated.ScrollView>();

  useEffect(() => {
    if (share.error) Alert.alert('Partage', share.error);
  }, [share.error]);

  useEffect(() => {
    (async () => {
      const s = await getSession(sessionId);
      setSession(s);
      if (s?.type === 'velo') setPoints(await getTrackPoints(sessionId));
      if (s?.type === 'muscu') setSets(await getMuscuSets(sessionId));
      if (s) {
        setRecords(await sessionRecords(s));
        setMaxHr((await getProfile()).maxHr);
      }
      setLoading(false);
    })();
  }, [sessionId]);

  const confirmDelete = () => {
    Alert.alert('Supprimer la séance ?', 'Cette action est définitive.', [
      { text: 'Annuler', style: 'cancel' },
      {
        text: 'Supprimer',
        style: 'destructive',
        onPress: async () => {
          await deleteSession(sessionId);
          router.back();
        },
      },
    ]);
  };

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.background, justifyContent: 'center' }}>
        <ActivityIndicator color={theme.accent} />
      </View>
    );
  }

  if (!session) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.background, justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ color: theme.textSecondary }}>Séance introuvable.</Text>
      </View>
    );
  }

  const meta = ACTIVITY_META[session.type];
  const color = theme[meta.colorKey];
  const effort = sessionEffort(session, maxHr);

  // Profils de données (vélo) — calculés depuis les points GPS, 100 % local.
  const speed = session.type === 'velo' ? speedProfile(points) : [];
  const elevation = session.type === 'velo' ? elevationProfile(points) : [];
  const hr = session.type === 'velo' ? hrProfile(points) : [];
  const fmtKm = (v: number) => (v >= 10 ? String(Math.round(v)) : v.toFixed(1).replace('.', ','));

  return (
    <>
      <Stack.Screen
        options={{
          title: meta.label,
          headerRight: () => (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 18 }}>
              <Pressable
                onPress={() => share.share(shareCardRef)}
                hitSlop={10}
                disabled={share.sharing}>
                <MaterialCommunityIcons
                  name="share-variant"
                  size={21}
                  color={share.sharing ? theme.textMuted : theme.text}
                />
              </Pressable>
              <Pressable onPress={confirmDelete} hitSlop={10}>
                <MaterialCommunityIcons name="trash-can-outline" size={22} color={theme.heart} />
              </Pressable>
            </View>
          ),
        }}
      />
      <Animated.ScrollView
        ref={scrollRef}
        style={{ backgroundColor: theme.background }}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{ padding: 16, gap: 16, paddingBottom: 40 }}>
        {/* En-tête */}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <View
            style={{
              width: 54,
              height: 54,
              borderRadius: Radius.md,
              borderCurve: 'continuous',
              backgroundColor: color + '22',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <MaterialCommunityIcons name={meta.icon} size={28} color={color} />
          </View>
          <View>
            <Text style={{ ...Type.headline, color: theme.text }}>{meta.label}</Text>
            <Text style={{ color: theme.textSecondary, fontSize: 14 }}>
              {formatDateTime(session.startedAt, true)}
            </Text>
          </View>
        </View>

        {/* Records personnels (façon « PR » Strava) */}
        <RecordsBanner records={records} year={new Date(session.startedAt).getFullYear()} />

        {/* Tracé GPS (vélo) */}
        {session.type === 'velo' && points.length >= 2 ? (
          <RouteMap points={points} color={color} interactive scrollRef={scrollRef} />
        ) : null}

        {/* Statistiques */}
        <Card>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 18 }}>
            <StatTile label="Durée" value={formatDuration(session.durationSec)} icon="clock-outline" compact />
            {session.type === 'velo' ? (
              <>
                <StatTile
                  label="Distance"
                  value={formatDistance(session.distanceM)}
                  icon="map-marker-distance"
                  color={color}
                  compact
                />
                <StatTile
                  label="Vitesse moy."
                  value={formatSpeed(session.avgSpeedKmh)}
                  icon="speedometer"
                  compact
                />
                <StatTile
                  label="Vitesse max"
                  value={formatSpeed(session.maxSpeedKmh)}
                  icon="speedometer-medium"
                  compact
                />
                <StatTile
                  label="Dénivelé +"
                  value={`${Math.round(session.elevationGainM ?? 0)} m`}
                  icon="elevation-rise"
                  compact
                />
                {session.avgCadence != null ? (
                  <StatTile
                    label="Cadence moy."
                    value={`${Math.round(session.avgCadence)} tr/min`}
                    icon="rotate-right"
                    color={color}
                    compact
                  />
                ) : null}
                {session.maxCadence != null ? (
                  <StatTile
                    label="Cadence max"
                    value={`${Math.round(session.maxCadence)} tr/min`}
                    icon="rotate-right"
                    color={color}
                    compact
                  />
                ) : null}
              </>
            ) : null}
            <StatTile
              label="FC moy."
              value={formatHr(session.avgHr)}
              icon="heart-pulse"
              color={theme.heart}
              compact
            />
            <StatTile
              label="FC max"
              value={formatHr(session.maxHr)}
              icon="heart"
              color={theme.heart}
              compact
            />
            <StatTile
              label="Calories"
              value={formatCalories(session.calories)}
              icon="fire"
              color={theme.warning}
              compact
            />
            <StatTile
              label="Effort"
              value={effort.label}
              icon="speedometer"
              color={theme[effort.colorKey]}
              compact
            />
          </View>
        </Card>

        {/* Profils de données (vélo) — vitesse / altitude / FC sur la distance */}
        {speed.length >= 2 ? (
          <ChartCard title="Vitesse" unit="km/h">
            <LineChart
              data={speed}
              color={color}
              avg={session.avgSpeedKmh}
              formatX={fmtKm}
            />
          </ChartCard>
        ) : null}

        {elevation.length >= 2 ? (
          <ChartCard title="Altitude" unit="m">
            <LineChart data={elevation} color={theme.textSecondary} formatX={fmtKm} />
          </ChartCard>
        ) : null}

        {hr.length >= 2 ? (
          <ChartCard title="Fréquence cardiaque" unit="bpm">
            <LineChart data={hr} color={theme.heart} avg={session.avgHr} formatX={fmtKm} />
          </ChartCard>
        ) : null}

        {/* Exercices (muscu) */}
        {session.type === 'muscu' ? <MuscuBreakdown sets={sets} color={color} /> : null}

        {session.notes ? (
          <Card>
            <Text style={{ ...Type.overline, color: theme.textSecondary }}>Notes</Text>
            <Text selectable style={{ color: theme.text, fontSize: 15 }}>
              {session.notes}
            </Text>
          </Card>
        ) : null}

        <Button
          title="Partager la séance"
          icon="share-variant"
          variant="secondary"
          color={color}
          loading={share.sharing}
          onPress={() => share.share(shareCardRef)}
        />
        <Button title="Supprimer la séance" icon="trash-can-outline" variant="danger" onPress={confirmDelete} />
      </Animated.ScrollView>

      {/* Carte partageable, rendue hors écran puis capturée en PNG au partage. */}
      <View style={{ position: 'absolute', left: -9999, top: 0 }} pointerEvents="none">
        <ShareCard
          ref={shareCardRef}
          session={session}
          points={points}
          sets={sets}
          records={records}
          effort={effort}
        />
      </View>
    </>
  );
}

/** Carte de section pour un graphe : titre + unité + contenu. */
function ChartCard({
  title,
  unit,
  children,
}: {
  title: string;
  unit: string;
  children: React.ReactNode;
}) {
  const theme = useTheme();
  return (
    <Card style={{ gap: 8 }}>
      <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <Text style={{ ...Type.headline, color: theme.text }}>{title}</Text>
        <Text style={{ ...Type.caption, color: theme.textMuted }}>{unit}</Text>
      </View>
      {children}
    </Card>
  );
}

const RECORD_LABELS: Record<RecordKind, { icon: keyof typeof MaterialCommunityIcons.glyphMap; noun: string }> = {
  distance: { icon: 'map-marker-distance', noun: 'distance' },
  elevation: { icon: 'elevation-rise', noun: 'dénivelé' },
  duration: { icon: 'clock-outline', noun: 'durée' },
  speed: { icon: 'speedometer', noun: 'vitesse moyenne' },
};

/**
 * Bannière de records, façon « PR » Strava. On priorise les records absolus
 * (« all ») puis ceux de l'année, et on en montre au plus trois pour éviter la
 * surcharge.
 */
function RecordsBanner({ records, year }: { records: SessionRecord[]; year: number }) {
  const theme = useTheme();
  if (records.length === 0) return null;

  const top = [...records]
    .sort((a, b) => (a.scope === b.scope ? 0 : a.scope === 'all' ? -1 : 1))
    .slice(0, 3);

  return (
    <Card style={{ gap: 12, backgroundColor: theme.warning + '14' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View
          style={{
            width: 40,
            height: 40,
            borderRadius: Radius.sm,
            borderCurve: 'continuous',
            backgroundColor: theme.warning + '24',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
          <MaterialCommunityIcons name="trophy" size={22} color={theme.warning} />
        </View>
        <Text style={{ ...Type.headline, color: theme.text }}>
          {top.some((r) => r.scope === 'all') ? 'Record personnel' : `Record ${year}`}
        </Text>
      </View>
      <View style={{ gap: 8 }}>
        {top.map((r) => {
          const meta = RECORD_LABELS[r.kind];
          return (
            <View key={r.kind} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
              <MaterialCommunityIcons name={meta.icon} size={16} color={theme.warning} />
              <Text style={{ color: theme.text, fontSize: 14, fontWeight: '600' }}>
                Meilleure {meta.noun}{' '}
                <Text style={{ color: theme.textSecondary, fontWeight: '600' }}>
                  {r.scope === 'all' ? 'de tous les temps' : `de ${year}`}
                </Text>
              </Text>
            </View>
          );
        })}
      </View>
    </Card>
  );
}

function MuscuBreakdown({ sets, color }: { sets: MuscuSet[]; color: string }) {
  const theme = useTheme();
  if (sets.length === 0) return null;

  // Regroupe par exercice en conservant l'ordre d'apparition.
  const groups: { name: string; rows: MuscuSet[] }[] = [];
  for (const s of sets) {
    let g = groups.find((x) => x.name === s.exercise);
    if (!g) {
      g = { name: s.exercise, rows: [] };
      groups.push(g);
    }
    g.rows.push(s);
  }

  return (
    <View style={{ gap: 12 }}>
      {groups.map((g) => {
        const volume = g.rows.reduce((a, r) => a + r.reps * r.weightKg, 0);
        return (
          <Card key={g.name} style={{ gap: 8 }}>
            <Link href={{ pathname: '/exercise/[name]', params: { name: g.name } }} asChild>
              <Pressable style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, flex: 1 }}>
                  <Text style={{ color: theme.text, fontSize: 16, fontWeight: '800' }}>{g.name}</Text>
                  <MaterialCommunityIcons name="chart-line" size={16} color={theme.textMuted} />
                </View>
                <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>
                  {Math.round(volume)} kg
                </Text>
              </Pressable>
            </Link>
            {g.rows.map((r) => (
              <View
                key={r.id}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 2 }}>
                <Text style={{ width: 22, color: theme.textSecondary, fontWeight: '700' }}>
                  {r.setIndex}
                </Text>
                <Text style={{ color: theme.text, fontWeight: '600', fontVariant: ['tabular-nums'] }}>
                  {r.reps} reps
                </Text>
                <Text style={{ color: theme.textSecondary }}>×</Text>
                <Text style={{ color: color, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                  {r.weightKg} kg
                </Text>
              </View>
            ))}
          </Card>
        );
      })}
    </View>
  );
}
