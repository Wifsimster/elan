import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import { Radius, Type } from '@/constants/theme';
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

/**
 * Tuile de métrique « bento » : pastille d'icône teintée + grand chiffre tabulaire.
 * Conçue pour s'aligner en grille fluide (flexWrap) dans une Card.
 */
export function StatTile({ label, value, unit, icon, color, compact }: Props) {
  const theme = useTheme();
  const tint = color ?? theme.text;

  return (
    <View style={{ gap: 8, flex: 1, minWidth: 96 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {icon ? (
          <View
            style={{
              width: 28,
              height: 28,
              borderRadius: Radius.sm,
              borderCurve: 'continuous',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: (color ?? theme.textSecondary) + '24',
            }}>
            <MaterialCommunityIcons name={icon} size={16} color={color ?? theme.textSecondary} />
          </View>
        ) : null}
        <Text style={{ ...Type.label, color: theme.textSecondary }}>{label}</Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 4 }}>
        <Text
          selectable
          style={{ ...Type.metric, fontSize: compact ? 24 : 32, color: tint }}>
          {value}
        </Text>
        {unit ? (
          <Text style={{ color: theme.textSecondary, fontSize: 14, fontWeight: '700' }}>{unit}</Text>
        ) : null}
      </View>
    </View>
  );
}
