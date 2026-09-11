import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Champ texte étiqueté d'un réglage (endpoint S3, bucket, URL de carte…).
 * Désactive auto-capitalisation et correction (saisie d'identifiants/URLs).
 * Un champ secret propose « Afficher » : une clé tapée à la main masquée
 * cache ses fautes de frappe, et l'erreur serveur qui en découle
 * (SignatureDoesNotMatch) n'oriente pas vers la saisie.
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
  const [revealed, setRevealed] = useState(false);
  return (
    <View style={{ gap: 6 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>{label}</Text>
        {secureTextEntry ? (
          <Pressable
            onPress={() => setRevealed((r) => !r)}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={revealed ? `Masquer ${label}` : `Afficher ${label}`}>
            <Text style={{ color: theme.accent, fontSize: 13, fontWeight: '600' }}>
              {revealed ? 'Masquer' : 'Afficher'}
            </Text>
          </Pressable>
        ) : null}
      </View>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={theme.textMuted}
        secureTextEntry={secureTextEntry && !revealed}
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
