import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { Text, View } from 'react-native';

import { Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

export function SettingCardHeader({
  icon,
  color,
  title,
}: {
  icon: IconName;
  color: string;
  title: string;
}) {
  const theme = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <MaterialCommunityIcons name={icon} size={22} color={color} />
      <Text style={{ ...Type.sectionTitle, color: theme.text }}>{title}</Text>
    </View>
  );
}
