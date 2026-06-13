import { Text, TextInput, View } from 'react-native';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Champ texte étiqueté d'un réglage (endpoint S3, bucket, URL de carte…).
 * Désactive auto-capitalisation et correction (saisie d'identifiants/URLs).
 */
export function SettingField({
  label,
  value,
  onChangeText,
  placeholder,
  secureTextEntry,
  keyboardType,
}: {
  label: string;
  value: string;
  onChangeText: (t: string) => void;
  placeholder?: string;
  secureTextEntry?: boolean;
  keyboardType?: 'default' | 'url';
}) {
  const theme = useTheme();
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={theme.textMuted}
        secureTextEntry={secureTextEntry}
        keyboardType={keyboardType}
        autoCapitalize="none"
        autoCorrect={false}
        style={{
          color: theme.text,
          backgroundColor: theme.backgroundSelected,
          borderRadius: Radius.sm,
          borderCurve: 'continuous',
          paddingHorizontal: 12,
          paddingVertical: 10,
          fontSize: 15,
          borderWidth: 1,
          borderColor: theme.border,
        }}
      />
    </View>
  );
}
