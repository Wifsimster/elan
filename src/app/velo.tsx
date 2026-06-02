import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useEffect, useRef, useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { StatTile } from '@/components/stat-tile';
import { Elevation, Type } from '@/constants/theme';
import { estimateCalories } from '@/lib/calories';
import {
  createSession,
  getProfile,
  insertTrackPoints,
  updateSession,
} from '@/lib/db';
import { formatDistance, formatDuration } from '@/lib/format';
import { nowMs } from '@/lib/time';
import { useCadenceSpeed } from '@/hooks/use-cadence-speed';
import { useGpsTracker } from '@/hooks/use-gps-tracker';
import { useHeartRate } from '@/hooks/use-heart-rate';
import { useStopwatch } from '@/hooks/use-stopwatch';
import { useTheme } from '@/hooks/use-theme';

type Phase = 'idle' | 'active' | 'paused' | 'saving';

type HrSample = { ts: number; hr: number };
type CadenceSample = { ts: number; cadence: number };

export default function VeloScreen() {
  useKeepAwake();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const gps = useGpsTracker();
  const watch = useStopwatch();
  const { bpm } = useHeartRate();
  const { cadenceRpm, speedKmh: sensorSpeedKmh, devices: cscDevices } = useCadenceSpeed();

  const [phase, setPhase] = useState<Phase>('idle');
  const [weightKg, setWeightKg] = useState(70);
  const startedAtRef = useRef<number>(0);
  const hrSamplesRef = useRef<HrSample[]>([]);
  const cadenceSamplesRef = useRef<CadenceSample[]>([]);

  const hasCadenceSensor = cscDevices.length > 0 || cadenceRpm != null;
  const hasSpeedSensor = sensorSpeedKmh != null;

  useEffect(() => {
    getProfile().then((p) => setWeightKg(p.weightKg));
  }, []);

  // Échantillonnage de la fréquence cardiaque pendant l'effort.
  useEffect(() => {
    if (phase === 'active' && bpm != null) {
      hrSamplesRef.current.push({ ts: nowMs(), hr: bpm });
    }
  }, [bpm, phase]);

  // Échantillonnage de la cadence pendant l'effort.
  useEffect(() => {
    if (phase === 'active' && cadenceRpm != null) {
      cadenceSamplesRef.current.push({ ts: nowMs(), cadence: cadenceRpm });
    }
  }, [cadenceRpm, phase]);

  const begin = async () => {
    const ok = await gps.start();
    if (!ok) {
      Alert.alert(
        'Localisation refusée',
        "Autorisez l'accès à la position pour mesurer votre sortie. Vous pouvez l'activer dans les réglages Android.",
      );
      return;
    }
    startedAtRef.current = nowMs();
    hrSamplesRef.current = [];
    cadenceSamplesRef.current = [];
    watch.start();
    setPhase('active');
  };

  const pause = () => {
    watch.pause();
    gps.setPaused(true);
    setPhase('paused');
  };

  const resume = () => {
    watch.start();
    gps.setPaused(false);
    setPhase('active');
  };

  const computeHr = () => {
    const samples = hrSamplesRef.current;
    if (samples.length === 0) return { avgHr: null, maxHr: null };
    const sum = samples.reduce((a, s) => a + s.hr, 0);
    const max = samples.reduce((a, s) => Math.max(a, s.hr), 0);
    return { avgHr: Math.round(sum / samples.length), maxHr: max };
  };

  // Moyenne « en mouvement » : on exclut les phases de roue libre (cadence 0).
  const computeCadence = () => {
    const all = cadenceSamplesRef.current;
    if (all.length === 0) return { avgCadence: null, maxCadence: null };
    const moving = all.filter((s) => s.cadence > 0);
    const max = all.reduce((a, s) => Math.max(a, s.cadence), 0);
    if (moving.length === 0) return { avgCadence: null, maxCadence: max || null };
    const sum = moving.reduce((a, s) => a + s.cadence, 0);
    return { avgCadence: Math.round(sum / moving.length), maxCadence: max };
  };

  const finish = () => {
    Alert.alert('Terminer la sortie ?', 'La séance sera enregistrée.', [
      { text: 'Continuer', style: 'cancel' },
      { text: 'Terminer', style: 'default', onPress: save },
    ]);
  };

  const save = async () => {
    setPhase('saving');
    const result = gps.stop();
    watch.pause();
    const durationSec = watch.elapsedSec;
    const { avgHr, maxHr } = computeHr();
    const { avgCadence, maxCadence } = computeCadence();
    const avgSpeedKmh =
      durationSec > 0 ? result.distanceM / 1000 / (durationSec / 3600) : 0;
    const calories = estimateCalories({
      type: 'velo',
      weightKg,
      durationSec,
      avgSpeedKmh,
    });

    const id = await createSession('velo', startedAtRef.current);
    await updateSession(id, {
      endedAt: nowMs(),
      durationSec,
      distanceM: result.distanceM,
      avgSpeedKmh,
      maxSpeedKmh: result.maxSpeedKmh,
      elevationGainM: result.elevationGainM,
      avgHr,
      maxHr,
      avgCadence,
      maxCadence,
      calories,
    });

    // Attache la FC et la cadence les plus proches à chaque point GPS.
    const samples = hrSamplesRef.current;
    const cadenceSamples = cadenceSamplesRef.current;
    const points = result.points.map((p) => ({
      ts: p.ts,
      lat: p.lat,
      lon: p.lon,
      altitude: p.altitude,
      speedKmh: p.speedKmh,
      hr: nearestHr(samples, p.ts),
      cadence: nearestSample(cadenceSamples, p.ts, (s) => s.cadence),
    }));
    await insertTrackPoints(id, points);

    router.replace({ pathname: '/session/[id]', params: { id } });
  };

  const discard = () => {
    if (phase === 'idle') {
      router.back();
      return;
    }
    Alert.alert('Abandonner la sortie ?', 'Les données ne seront pas enregistrées.', [
      { text: 'Continuer', style: 'cancel' },
      {
        text: 'Abandonner',
        style: 'destructive',
        onPress: () => {
          gps.stop();
          router.back();
        },
      },
    ]);
  };

  const liveCalories = estimateCalories({
    type: 'velo',
    weightKg,
    durationSec: watch.elapsedSec,
    avgSpeedKmh: gps.speedKmh,
  });

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <ScrollView
        contentContainerStyle={{
          paddingTop: insets.top + 8,
          paddingBottom: insets.bottom + 120,
          paddingHorizontal: 16,
          gap: 16,
        }}>
        {/* En-tête */}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <PressableScale onPress={discard} haptic="selection" scaleTo={0.88} hitSlop={12}>
            <MaterialCommunityIcons name="close" size={26} color={theme.text} />
          </PressableScale>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <MaterialCommunityIcons name="bike" size={22} color={theme.velo} />
            <Text style={{ ...Type.headline, color: theme.text }}>Vélo</Text>
          </View>
          <View style={{ width: 26 }} />
        </View>

        {/* Chrono */}
        <View style={{ alignItems: 'center', paddingVertical: 8 }}>
          <Text style={{ ...Type.overline, color: theme.velo }}>Durée</Text>
          <Text selectable style={{ ...Type.metricLg, color: theme.text }}>
            {formatDuration(watch.elapsedSec)}
          </Text>
          <GpsStatusPill status={gps.status} accuracyM={gps.accuracyM} phase={phase} />
        </View>

        {/* Statistiques live */}
        <Card>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 20 }}>
            <StatTile
              label="Distance"
              value={formatDistance(gps.distanceM).replace(/\s?(km|m)$/, '')}
              unit={gps.distanceM >= 1000 ? 'km' : 'm'}
              icon="map-marker-distance"
              color={theme.velo}
            />
            <StatTile
              label="Vitesse"
              value={gps.speedKmh.toFixed(1)}
              unit="km/h"
              icon="speedometer"
            />
            <StatTile
              label="Vitesse max"
              value={gps.maxSpeedKmh.toFixed(1)}
              unit="km/h"
              icon="speedometer-medium"
              compact
            />
            {hasSpeedSensor ? (
              <StatTile
                label="Vitesse roue"
                value={(sensorSpeedKmh ?? 0).toFixed(1)}
                unit="km/h"
                icon="bike-fast"
                color={theme.velo}
                compact
              />
            ) : null}
            {hasCadenceSensor ? (
              <StatTile
                label="Cadence"
                value={cadenceRpm != null ? String(cadenceRpm) : '—'}
                unit="tr/min"
                icon="rotate-right"
                color={theme.velo}
                compact
              />
            ) : null}
            <StatTile
              label="Dénivelé +"
              value={String(Math.round(gps.elevationGainM))}
              unit="m"
              icon="elevation-rise"
              compact
            />
            <StatTile
              label="Cardio"
              value={bpm != null ? String(bpm) : '—'}
              unit="bpm"
              icon="heart-pulse"
              color={theme.heart}
              compact
            />
            <StatTile
              label="Calories"
              value={String(Math.round(liveCalories))}
              unit="kcal"
              icon="fire"
              color={theme.warning}
              compact
            />
          </View>
        </Card>
      </ScrollView>

      {/* Contrôles */}
      <View
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          paddingHorizontal: 16,
          paddingTop: 12,
          paddingBottom: insets.bottom + 12,
          backgroundColor: theme.backgroundElement,
          borderTopWidth: 1,
          borderTopColor: theme.hairline,
          flexDirection: 'row',
          gap: 12,
          ...Elevation.lg,
        }}>
        {phase === 'idle' ? (
          <View style={{ flex: 1 }}>
            <Button
              title="Démarrer la sortie"
              icon="play"
              color={theme.velo}
              loading={gps.status === 'requesting'}
              onPress={begin}
            />
          </View>
        ) : (
          <>
            <View style={{ flex: 1 }}>
              <Button
                title={phase === 'paused' ? 'Reprendre' : 'Pause'}
                icon={phase === 'paused' ? 'play' : 'pause'}
                variant="secondary"
                color={theme.velo}
                onPress={phase === 'paused' ? resume : pause}
              />
            </View>
            <View style={{ flex: 1 }}>
              <Button
                title="Terminer"
                icon="flag-checkered"
                color={theme.velo}
                loading={phase === 'saving'}
                onPress={finish}
              />
            </View>
          </>
        )}
      </View>
    </View>
  );
}

function nearestHr(samples: HrSample[], ts: number): number | null {
  return nearestSample(samples, ts, (s) => s.hr);
}

/** Valeur de l'échantillon temporellement le plus proche (tolérance 10 s). */
function nearestSample<T extends { ts: number }>(
  samples: T[],
  ts: number,
  pick: (s: T) => number,
): number | null {
  if (samples.length === 0) return null;
  let best = samples[0];
  let bestDiff = Math.abs(samples[0].ts - ts);
  for (const s of samples) {
    const diff = Math.abs(s.ts - ts);
    if (diff < bestDiff) {
      bestDiff = diff;
      best = s;
    }
  }
  return bestDiff <= 10000 ? pick(best) : null;
}

function GpsStatusPill({
  status,
  accuracyM,
  phase,
}: {
  status: ReturnType<typeof useGpsTracker>['status'];
  accuracyM: number | null;
  phase: Phase;
}) {
  const theme = useTheme();
  if (phase === 'idle') return null;

  let label = 'Recherche GPS…';
  let color: string = theme.warning;
  if (status === 'tracking') {
    if (accuracyM != null && accuracyM <= 10) {
      label = 'GPS précis';
      color = theme.success;
    } else if (accuracyM != null) {
      label = `GPS ±${Math.round(accuracyM)} m`;
      color = theme.warning;
    }
  } else if (status === 'denied') {
    label = 'GPS refusé';
    color = theme.heart;
  }

  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 6 }}>
      <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: color }} />
      <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>{label}</Text>
    </View>
  );
}
