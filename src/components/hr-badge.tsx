import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

import { useHeartRate } from '@/hooks/use-heart-rate';
import { useTheme } from '@/hooks/use-theme';

/** Pastille de fréquence cardiaque ; mène aux réglages si non connectée. */
export function HrBadge() {
  const theme = useTheme();
  const { status, bpm } = useHeartRate();

  const connected = status === 'connected';
  const label =
    status === 'connected'
      ? bpm != null
        ? `${bpm}`
        : '··'
      : status === 'connecting'
        ? '...'
        : 'Connecter';

  const content = (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        paddingVertical: 6,
        paddingHorizontal: 12,
        borderRadius: 999,
        backgroundColor: connected ? theme.heart + '22' : theme.backgroundElement,
        borderWidth: 1,
        borderColor: connected ? theme.heart : theme.border,
      }}>
      <MaterialCommunityIcons
        name={connected ? 'heart-pulse' : 'heart-off-outline'}
        size={18}
        color={connected ? theme.heart : theme.textSecondary}
      />
      <Text
        style={{
          color: connected ? theme.heart : theme.textSecondary,
          fontWeight: '700',
          fontSize: 15,
          fontVariant: ['tabular-nums'],
        }}>
        {label}
      </Text>
      {connected && bpm != null ? (
        <Text style={{ color: theme.textSecondary, fontSize: 12 }}>bpm</Text>
      ) : null}
    </View>
  );

  if (connected) return content;

  return (
    <Link href="/settings" asChild>
      <Pressable>{content}</Pressable>
    </Link>
  );
}
