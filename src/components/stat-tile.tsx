import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import { Radius, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Évolution affichée sous la valeur (ex. comparaison à la période précédente). */
export type Trend = {
  label: string;
  /** `positive` = vert, `negative` = atténué, `neutral` = stable. */
  tone: 'positive' | 'negative' | 'neutral';
};

type Props = {
  label: string;
  value: string;
  unit?: string;
  icon?: keyof typeof MaterialCommunityIcons.glyphMap;
  color?: string;
  /** Affichage compact (police plus petite). */
  compact?: boolean;
  /** Métrique mise en avant (chiffre plus grand) — usage : valeur clé à lire en effort. */
  hero?: boolean;
  /** Petite ligne d'évolution sous la valeur. */
  trend?: Trend;
};

/**
 * Tuile de métrique « bento » : pastille d'icône teintée + grand chiffre tabulaire.
 * Conçue pour s'aligner en grille fluide (flexWrap) dans une Card.
 */
export function StatTile({ label, value, unit, icon, color, compact, hero, trend }: Props) {
  const theme = useTheme();
  const tint = color ?? theme.text;
  const valueSize = hero ? 44 : compact ? 24 : 32;

  const trendColor =
    trend?.tone === 'positive'
      ? theme.success
      : trend?.tone === 'negative'
        ? theme.textSecondary
        : theme.textMuted;
  const trendIcon =
    trend?.tone === 'positive' ? 'arrow-up' : trend?.tone === 'negative' ? 'arrow-down' : 'minus';

  return (
    <View style={{ gap: 8, flex: 1, minWidth: hero ? '100%' : 96 }}>
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
          maxFontSizeMultiplier={1.3}
          style={{ ...Type.metric, fontSize: valueSize, color: tint }}>
          {value}
        </Text>
        {unit ? (
          <Text style={{ color: theme.textSecondary, fontSize: 14, fontWeight: '700' }}>{unit}</Text>
        ) : null}
      </View>
      {trend ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}>
          <MaterialCommunityIcons name={trendIcon} size={12} color={trendColor} />
          <Text style={{ ...Type.caption, color: trendColor }}>{trend.label}</Text>
        </View>
      ) : null}
    </View>
  );
}
