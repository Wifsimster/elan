import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

import { Card } from '@/components/card';
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
      <Pressable>
        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 14 }}>
          <View
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              borderCurve: 'continuous',
              backgroundColor: color + '22',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <MaterialCommunityIcons name={meta.icon} size={24} color={color} />
          </View>
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={{ color: theme.text, fontSize: 16, fontWeight: '700' }}>{meta.label}</Text>
            <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
              {formatDateTime(session.startedAt)}
            </Text>
          </View>
          <View style={{ alignItems: 'flex-end', gap: 2 }}>
            <Text style={{ color: theme.text, fontWeight: '700' }}>
              {formatDurationShort(session.durationSec)}
            </Text>
            {session.type === 'velo' && session.distanceM != null ? (
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
                {formatDistance(session.distanceM)}
              </Text>
            ) : session.avgHr != null ? (
              <Text style={{ color: theme.textSecondary, fontSize: 13 }}>{session.avgHr} bpm moy.</Text>
            ) : null}
          </View>
          <MaterialCommunityIcons name="chevron-right" size={22} color={theme.textSecondary} />
        </Card>
      </Pressable>
    </Link>
  );
}
