import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useEffect, useRef, useState } from 'react';
import { Alert, BackHandler, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { RouteMap } from '@/components/route-map';
import { StatTile } from '@/components/stat-tile';
import { Elevation, Radius, Type } from '@/constants/theme';
import { autoBackup } from '@/lib/backup';
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
  const { bpm, subscribe: subscribeHr } = useHeartRate();
  const {
    cadenceRpm,
    speedKmh: sensorSpeedKmh,
    devices: cscDevices,
    subscribe: subscribeCsc,
  } = useCadenceSpeed();

  const [phase, setPhase] = useState<Phase>('idle');
  const [weightKg, setWeightKg] = useState(70);
  const [profileMaxHr, setProfileMaxHr] = useState(190);
  const phaseRef = useRef<Phase>('idle');
  const startedAtRef = useRef<number>(0);
  const hrSamplesRef = useRef<HrSample[]>([]);
  const cadenceSamplesRef = useRef<CadenceSample[]>([]);

  const hasCadenceSensor = cscDevices.length > 0 || cadenceRpm != null;
  const hasSpeedSensor = sensorSpeedKmh != null;

  useEffect(() => {
    getProfile().then((p) => {
      setWeightKg(p.weightKg);
      setProfileMaxHr(p.maxHr);
    });
  }, []);

  useEffect(() => {
    phaseRef.current = phase;
  }, [phase]);

  // Échantillonnage des capteurs BLE pendant l'effort. On s'abonne aux trames
  // brutes plutôt qu'à `bpm`/`cadenceRpm` : React déduplique les setStates
  // identiques, ce qui ferait perdre les trames d'un palier (FC stable,
  // cadence stable) et appauvrirait la moyenne ainsi que l'attachement
  // FC↔point GPS.
  useEffect(() => {
    return subscribeHr(({ ts, hr }) => {
      if (phaseRef.current === 'active') hrSamplesRef.current.push({ ts, hr });
    });
  }, [subscribeHr]);

  useEffect(() => {
    return subscribeCsc(({ ts, cadenceRpm: rpm }) => {
      if (rpm == null) return;
      if (phaseRef.current === 'active') cadenceSamplesRef.current.push({ ts, cadence: rpm });
    });
  }, [subscribeCsc]);

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
      elevationGainM: result.elevationGainM,
      avgHr,
      maxHr: profileMaxHr,
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

    autoBackup(); // sauvegarde homelab best-effort (ne bloque pas la navigation)
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

  // Le bouton retour matériel (Android) doit suivre le même chemin que la croix
  // (confirmation d'abandon), sinon il quitterait la modale en perdant la sortie
  // en cours et en laissant le suivi GPS actif (batterie). Cf. muscu.tsx.
  const discardRef = useRef(discard);
  useEffect(() => {
    discardRef.current = discard;
  });
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      discardRef.current();
      return true;
    });
    return () => sub.remove();
  }, []);

  // Filet de sécurité : si l'écran est démonté par un chemin imprévu, on coupe
  // le suivi GPS pour ne pas laisser `watchPositionAsync` tourner en fond.
  useEffect(() => {
    const stop = gps.stop;
    return () => {
      stop();
    };
  }, [gps.stop]);

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

        {/* Tracé GPS en temps réel (lecture passive : aucun geste pendant l'effort).
            Tant qu'il n'y a pas assez de points, on montre un cadre d'attente
            plutôt que rien — sinon la carte semble absente à l'arrêt/en intérieur. */}
        {phase !== 'idle' ? (
          gps.livePath.length >= 2 ? (
            <RouteMap points={gps.livePath} live color={theme.velo} height={220} />
          ) : (
            <MapPlaceholder status={gps.status} />
          )
        ) : null}
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

/**
 * Valeur de l'échantillon temporellement le plus proche (tolérance 10 s).
 * Les échantillons sont accumulés dans l'ordre chronologique (push lors de la
 * réception BLE) : recherche dichotomique en O(log N) au lieu d'un scan
 * complet pour chaque point GPS — sur une longue sortie cela évite plusieurs
 * secondes de calcul au moment de l'enregistrement.
 */
function nearestSample<T extends { ts: number }>(
  samples: T[],
  ts: number,
  pick: (s: T) => number,
): number | null {
  if (samples.length === 0) return null;
  // Borne basse via recherche dichotomique : premier index dont ts >= cible.
  let lo = 0;
  let hi = samples.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (samples[mid].ts < ts) lo = mid + 1;
    else hi = mid;
  }
  const candidates: T[] = [];
  if (lo < samples.length) candidates.push(samples[lo]);
  if (lo > 0) candidates.push(samples[lo - 1]);
  let best = candidates[0];
  let bestDiff = Math.abs(best.ts - ts);
  for (let i = 1; i < candidates.length; i++) {
    const diff = Math.abs(candidates[i].ts - ts);
    if (diff < bestDiff) {
      bestDiff = diff;
      best = candidates[i];
    }
  }
  return bestDiff <= 10000 ? pick(best) : null;
}

/** Cadre d'attente affiché tant que le tracé live n'a pas assez de points. */
function MapPlaceholder({ status }: { status: ReturnType<typeof useGpsTracker>['status'] }) {
  const theme = useTheme();
  const label =
    status === 'tracking'
      ? 'En attente de déplacement — le tracé apparaîtra dès les premiers mètres.'
      : status === 'requesting'
        ? 'Recherche du signal GPS…'
        : status === 'denied'
          ? 'Localisation refusée — activez le GPS pour tracer la sortie.'
          : 'Tracé GPS indisponible.';
  return (
    <View
      style={{
        height: 220,
        borderRadius: Radius.md,
        borderCurve: 'continuous',
        overflow: 'hidden',
        backgroundColor: theme.backgroundElement,
        borderWidth: 1,
        borderColor: theme.border,
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        paddingHorizontal: 28,
      }}>
      <MaterialCommunityIcons name="map-marker-path" size={36} color={theme.textMuted} />
      <Text style={{ color: theme.textSecondary, fontSize: 13, textAlign: 'center', lineHeight: 19 }}>
        {label}
      </Text>
    </View>
  );
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
