// Carte partageable d'une séance, façon Strava : tracé (ou héros d'activité) +
// stats + marque « Élan ». Rendue hors écran puis capturée en PNG par
// `useSessionShare`, sans aucun appel réseau (le partage est délégué à l'OS).
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { forwardRef } from 'react';
import { Text, View } from 'react-native';
import Svg, { Circle, Polyline } from 'react-native-svg';

import { Gradient } from '@/components/gradient';
import { Radius, Type } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import type { SessionRecord } from '@/lib/db';
import type { Effort } from '@/lib/effort';
import {
  formatCalories,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatHr,
  formatSpeed,
} from '@/lib/format';
import { decimateByDistance } from '@/lib/geo';
import { createProjection } from '@/lib/route-projection';
import type { MuscuSet, Session, TrackPoint } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

export const SHARE_CARD_WIDTH = 360;

type Props = {
  session: Session;
  points: TrackPoint[];
  sets: MuscuSet[];
  records: SessionRecord[];
  effort: Effort;
};

type StatItem = { label: string; value: string; color?: string };

const RECORD_NOUN: Record<SessionRecord['kind'], string> = {
  distance: 'distance',
  elevation: 'dénivelé',
  duration: 'durée',
  speed: 'vitesse',
};

export const ShareCard = forwardRef<View, Props>(function ShareCard(
  { session, points, sets, records, effort },
  ref,
) {
  const theme = useTheme();
  const meta = ACTIVITY_META[session.type];
  const color = theme[meta.colorKey];
  const isVelo = session.type === 'velo';
  const hasRoute = isVelo && points.length >= 2;

  const stats: StatItem[] = [];
  if (isVelo) {
    stats.push({ label: 'Distance', value: formatDistance(session.distanceM), color });
    stats.push({ label: 'Durée', value: formatDuration(session.durationSec) });
    stats.push({ label: 'Vitesse moy', value: formatSpeed(session.avgSpeedKmh) });
    stats.push({ label: 'Dénivelé +', value: `${Math.round(session.elevationGainM ?? 0)} m` });
    if (session.avgHr != null) stats.push({ label: 'FC moy', value: formatHr(session.avgHr), color: theme.heart });
    stats.push({ label: 'Calories', value: formatCalories(session.calories), color: theme.warning });
  } else {
    const volume = sets.reduce((a, s) => a + s.reps * s.weightKg, 0);
    const exercises = new Set(sets.map((s) => s.exercise)).size;
    stats.push({ label: 'Durée', value: formatDuration(session.durationSec) });
    stats.push({ label: 'Exercices', value: String(exercises) });
    stats.push({ label: 'Volume', value: `${Math.round(volume)} kg`, color });
    if (session.avgHr != null) stats.push({ label: 'FC moy', value: formatHr(session.avgHr), color: theme.heart });
    stats.push({ label: 'Calories', value: formatCalories(session.calories), color: theme.warning });
  }

  // Badge : un record si la séance en détient un, sinon le niveau d'effort.
  const topRecord = [...records].sort((a, b) => (a.scope === b.scope ? 0 : a.scope === 'all' ? -1 : 1))[0];
  const badge = topRecord
    ? {
        icon: 'trophy' as const,
        label: `Record ${topRecord.scope === 'all' ? 'perso' : 'de l’année'} · ${RECORD_NOUN[topRecord.kind]}`,
        color: theme.warning,
      }
    : { icon: 'speedometer' as const, label: `Effort · ${effort.label}`, color: theme[effort.colorKey] };

  return (
    <View
      ref={ref}
      collapsable={false}
      style={{
        width: SHARE_CARD_WIDTH,
        borderRadius: Radius.xl,
        borderCurve: 'continuous',
        overflow: 'hidden',
        backgroundColor: theme.background,
      }}>
      {hasRoute ? (
        <RouteHero points={points} color={color} theme={theme} />
      ) : (
        <IconHero gradient={meta.colorKey} icon={meta.icon} />
      )}

      <View style={{ padding: 18, gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ gap: 2, flex: 1 }}>
            <Text style={{ ...Type.overline, color: theme.textMuted }}>ÉLAN</Text>
            <Text style={{ ...Type.headline, color: theme.text }}>{meta.label}</Text>
            <Text style={{ ...Type.caption, color: theme.textSecondary }}>
              {formatDateTime(session.startedAt)}
            </Text>
          </View>
          <View
            style={{
              width: 44,
              height: 44,
              borderRadius: Radius.sm,
              borderCurve: 'continuous',
              backgroundColor: color + '22',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <MaterialCommunityIcons name={meta.icon} size={24} color={color} />
          </View>
        </View>

        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, rowGap: 14 }}>
          {stats.map((s) => (
            <View key={s.label} style={{ width: (SHARE_CARD_WIDTH - 36 - 24) / 3, gap: 2 }}>
              <Text style={{ ...Type.metric, fontSize: 20, color: s.color ?? theme.text }}>{s.value}</Text>
              <Text style={{ ...Type.caption, color: theme.textSecondary }}>{s.label}</Text>
            </View>
          ))}
        </View>

        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            alignSelf: 'flex-start',
            gap: 6,
            paddingVertical: 6,
            paddingHorizontal: 12,
            borderRadius: Radius.pill,
            backgroundColor: badge.color + '22',
          }}>
          <MaterialCommunityIcons name={badge.icon} size={15} color={badge.color} />
          <Text style={{ color: badge.color, fontSize: 13, fontWeight: '700' }}>{badge.label}</Text>
        </View>
      </View>
    </View>
  );
});

function RouteHero({
  points,
  color,
  theme,
}: {
  points: TrackPoint[];
  color: string;
  theme: ReturnType<typeof useTheme>;
}) {
  const W = SHARE_CARD_WIDTH;
  const H = 200;
  const pad = 24;
  const pts = points.length > 400 ? decimateByDistance(points, 10) : points;
  const proj = createProjection(pts, { width: W - pad * 2, height: H - pad * 2 });
  const coords = pts.map((p) => proj.project(p));
  const str = coords.map(([x, y]) => `${(x + pad).toFixed(1)},${(y + pad).toFixed(1)}`).join(' ');
  const [sx, sy] = coords[0];
  const [ex, ey] = coords[coords.length - 1];

  return (
    <View style={{ width: W, height: H, backgroundColor: theme.backgroundElement }}>
      <Svg width={W} height={H}>
        <Polyline
          points={str}
          fill="none"
          stroke={color}
          strokeWidth={5}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <Circle cx={sx + pad} cy={sy + pad} r={7} fill={theme.success} />
        <Circle cx={ex + pad} cy={ey + pad} r={7} fill="none" stroke={theme.heart} strokeWidth={3.5} />
      </Svg>
    </View>
  );
}

function IconHero({
  gradient,
  icon,
}: {
  gradient: 'velo' | 'muscu';
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
}) {
  return (
    <Gradient colors={gradient} style={{ width: SHARE_CARD_WIDTH, height: 140 }}>
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <MaterialCommunityIcons name={icon} size={56} color="#FFFFFF" />
      </View>
    </Gradient>
  );
}
