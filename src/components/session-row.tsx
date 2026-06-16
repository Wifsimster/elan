import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import { Text, View } from 'react-native';

import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { Radius, Type } from '@/constants/theme';
import { ACTIVITY_META } from '@/lib/activity';
import { formatDateTime, formatDistance, formatDurationShort } from '@/lib/format';
import type { Session } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

export function SessionRow({ session }: { session: Session }) {
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
            {/* En muscu la durée n'a pas de sens : on met en avant la complétion
                (séries faites, puis exercices). Le vélo garde temps + distance. */}
            <Text style={{ color: theme.text, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
              {session.type === 'muscu'
                ? `${session.setCount ?? 0} série${(session.setCount ?? 0) > 1 ? 's' : ''}`
                : formatDurationShort(session.movingTimeSec ?? session.durationSec)}
            </Text>
            {session.type === 'muscu' ? (
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                {`${session.exerciseCount ?? 0} exercice${(session.exerciseCount ?? 0) > 1 ? 's' : ''}`}
              </Text>
            ) : session.type === 'velo' && session.distanceM != null ? (
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                {formatDistance(session.distanceM)}
              </Text>
            ) : session.avgHr != null ? (
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>{session.avgHr} bpm moy.</Text>
            ) : null}
          </View>
          <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textMuted} />
        </Card>
      </PressableScale>
    </Link>
  );
}
