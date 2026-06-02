import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

type Props = {
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  title: string;
  subtitle?: string;
};

export function EmptyState({ icon, title, subtitle }: Props) {
  const theme = useTheme();
  return (
    <View style={{ alignItems: 'center', gap: 8, paddingVertical: 48, paddingHorizontal: 24 }}>
      <MaterialCommunityIcons name={icon} size={48} color={theme.textSecondary} />
      <Text style={{ color: theme.text, fontSize: 18, fontWeight: '700', textAlign: 'center' }}>
        {title}
      </Text>
      {subtitle ? (
        <Text style={{ color: theme.textSecondary, fontSize: 14, textAlign: 'center' }}>
          {subtitle}
        </Text>
      ) : null}
    </View>
  );
}
