import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import { Text, View } from 'react-native';

import { PressableScale } from '@/components/pressable-scale';
import { Radius } from '@/constants/theme';
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
        paddingVertical: 8,
        paddingHorizontal: 14,
        borderRadius: Radius.pill,
        backgroundColor: connected ? theme.heart + '1F' : theme.backgroundElement,
        borderWidth: 1,
        borderColor: connected ? theme.heart + '66' : theme.border,
        // Halo cardiaque quand la ceinture émet.
        ...(connected
          ? {
              shadowColor: theme.heart,
              shadowOpacity: 0.5,
              shadowRadius: 12,
              shadowOffset: { width: 0, height: 0 },
              elevation: 6,
            }
          : null),
      }}>
      <MaterialCommunityIcons
        name={connected ? 'heart-pulse' : 'heart-off-outline'}
        size={18}
        color={connected ? theme.heart : theme.textSecondary}
      />
      <Text
        style={{
          color: connected ? theme.heart : theme.textSecondary,
          fontWeight: '800',
          fontSize: 15,
          fontVariant: ['tabular-nums'],
        }}>
        {label}
      </Text>
      {connected && bpm != null ? (
        <Text style={{ color: theme.textSecondary, fontSize: 12, fontWeight: '600' }}>bpm</Text>
      ) : null}
    </View>
  );

  if (connected) return content;

  return (
    <Link href="/settings" asChild>
      <PressableScale haptic="selection">{content}</PressableScale>
    </Link>
  );
}
