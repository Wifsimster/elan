import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

type Props = {
  label: string;
  value: string;
  unit?: string;
  icon?: keyof typeof MaterialCommunityIcons.glyphMap;
  color?: string;
  /** Affichage compact (police plus petite). */
  compact?: boolean;
};

export function StatTile({ label, value, unit, icon, color, compact }: Props) {
  const theme = useTheme();
  return (
    <View style={{ gap: 4, flex: 1, minWidth: 90 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
        {icon ? (
          <MaterialCommunityIcons name={icon} size={16} color={color ?? theme.textSecondary} />
        ) : null}
        <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>
          {label}
        </Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 4 }}>
        <Text
          selectable
          style={{
            color: color ?? theme.text,
            fontSize: compact ? 22 : 30,
            fontWeight: '800',
            fontVariant: ['tabular-nums'],
          }}>
          {value}
        </Text>
        {unit ? (
          <Text style={{ color: theme.textSecondary, fontSize: 14, fontWeight: '600' }}>
            {unit}
          </Text>
        ) : null}
      </View>
    </View>
  );
}
