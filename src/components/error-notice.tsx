import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function ErrorNotice({ message }: { message: string }) {
  const theme = useTheme();
  return (
    <View
      accessibilityRole="alert"
      style={{
        flexDirection: 'row',
        alignItems: 'flex-start',
        gap: 10,
        padding: 12,
        borderRadius: Radius.md,
        borderCurve: 'continuous',
        backgroundColor: theme.danger + '1A',
        borderWidth: 1,
        borderColor: theme.danger + '55',
      }}>
      <MaterialCommunityIcons name="alert-circle-outline" size={20} color={theme.danger} />
      <Text style={{ color: theme.text, fontSize: 13, lineHeight: 19, flex: 1 }}>{message}</Text>
    </View>
  );
}
