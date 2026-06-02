import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { RouteMap } from '@/components/route-map';
import { StatTile } from '@/components/stat-tile';
import { Radius, Type } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import { deleteSession, getMuscuSets, getSession, getTrackPoints } from '@/lib/db';
import {
  formatCalories,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatHr,
  formatSpeed,
} from '@/lib/format';
import type { MuscuSet, Session, TrackPoint } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

export default function SessionDetailScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const sessionId = Number(id);

  const [session, setSession] = useState<Session | null>(null);
  const [points, setPoints] = useState<TrackPoint[]>([]);
  const [sets, setSets] = useState<MuscuSet[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const s = await getSession(sessionId);
      setSession(s);
      if (s?.type === 'velo') setPoints(await getTrackPoints(sessionId));
      if (s?.type === 'muscu') setSets(await getMuscuSets(sessionId));
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

  return (
    <>
      <Stack.Screen
        options={{
          title: meta.label,
          headerRight: () => (
            <Pressable onPress={confirmDelete} hitSlop={10}>
              <MaterialCommunityIcons name="trash-can-outline" size={22} color={theme.heart} />
            </Pressable>
          ),
        }}
      />
      <ScrollView
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
              {formatDateTime(session.startedAt)}
            </Text>
          </View>
        </View>

        {/* Tracé GPS (vélo) */}
        {session.type === 'velo' && points.length >= 2 ? (
          <RouteMap points={points} color={color} />
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
          </View>
        </Card>

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

        <Button title="Supprimer la séance" icon="trash-can-outline" variant="danger" onPress={confirmDelete} />
      </ScrollView>
    </>
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
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={{ color: theme.text, fontSize: 16, fontWeight: '800' }}>{g.name}</Text>
              <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>
                {Math.round(volume)} kg
              </Text>
            </View>
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
