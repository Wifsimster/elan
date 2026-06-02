import { View, type ViewProps } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

export function Card({ style, ...rest }: ViewProps) {
  const theme = useTheme();
  return (
    <View
      style={[
        {
          backgroundColor: theme.backgroundElement,
          borderColor: theme.border,
          borderWidth: 1,
          borderRadius: 16,
          borderCurve: 'continuous',
          padding: 16,
          gap: 12,
        },
        style,
      ]}
      {...rest}
    />
  );
}
