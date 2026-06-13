import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Pressable, Text, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

/**
 * Incrémenteur numérique d'un réglage (poids, taille, FC max, heure de rappel,
 * circonférence de pneu…), borné par `min`/`max`. Partagé par les cartes de
 * l'écran Réglages.
 */
export function SettingStepper({
  label,
  value,
  unit,
  step,
  min,
  max,
  onChange,
}: {
  label: string;
  value: number;
  unit: string;
  step: number;
  min: number;
  max: number;
  onChange: (v: number) => void;
}) {
  const theme = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        <Pressable
          onPress={() => onChange(Math.max(min, value - step))}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel={`Diminuer ${label}`}>
          <MaterialCommunityIcons name="minus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
        <Text
          accessibilityLabel={`${label} : ${value} ${unit}`}
          maxFontSizeMultiplier={1.3}
          style={{
            color: theme.text,
            fontSize: 17,
            fontWeight: '800',
            minWidth: 70,
            textAlign: 'center',
            fontVariant: ['tabular-nums'],
          }}>
          {value} {unit}
        </Text>
        <Pressable
          onPress={() => onChange(Math.min(max, value + step))}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel={`Augmenter ${label}`}>
          <MaterialCommunityIcons name="plus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
      </View>
    </View>
  );
}
