import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ActivityIndicator, Pressable, Text, View, type PressableProps } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

type Variant = 'primary' | 'secondary' | 'danger';

type Props = PressableProps & {
  title: string;
  icon?: keyof typeof MaterialCommunityIcons.glyphMap;
  variant?: Variant;
  color?: string;
  loading?: boolean;
};

export function Button({
  title,
  icon,
  variant = 'primary',
  color,
  loading,
  disabled,
  style,
  ...rest
}: Props) {
  const theme = useTheme();
  const accent = color ?? theme.accent;

  const bg =
    variant === 'primary' ? accent : variant === 'danger' ? theme.heart : 'transparent';
  const fg =
    variant === 'primary' || variant === 'danger' ? '#FFFFFF' : accent;
  const borderColor = variant === 'secondary' ? accent : 'transparent';

  return (
    <Pressable
      disabled={disabled || loading}
      style={(state) => [
        {
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
          paddingVertical: 14,
          paddingHorizontal: 18,
          borderRadius: 14,
          borderCurve: 'continuous',
          backgroundColor: bg,
          borderWidth: variant === 'secondary' ? 1.5 : 0,
          borderColor,
          opacity: disabled ? 0.45 : state.pressed ? 0.85 : 1,
        },
        typeof style === 'function' ? style(state) : style,
      ]}
      {...rest}>
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          {icon ? <MaterialCommunityIcons name={icon} size={20} color={fg} /> : null}
          <Text style={{ color: fg, fontSize: 16, fontWeight: '700' }}>{title}</Text>
        </View>
      )}
    </Pressable>
  );
}
