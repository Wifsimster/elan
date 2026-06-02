import { Text, View } from 'react-native';

import { Gradient } from '@/components/gradient';
import { type GradientName } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type Bar = { label: string; value: number };

type Props = {
  data: Bar[];
  /** Dégradé des barres (défaut : accent). */
  gradient?: GradientName;
  height?: number;
  /** Formate la valeur affichée au-dessus de la barre la plus haute. */
  formatValue?: (v: number) => string;
};

export function BarChart({ data, gradient = 'accent', height = 120, formatValue }: Props) {
  const theme = useTheme();
  const max = Math.max(1, ...data.map((d) => d.value));

  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 6, height: height + 24 }}>
      {data.map((d, i) => {
        const ratio = d.value / max;
        const isMax = d.value === max && d.value > 0;
        const barHeight = Math.max(4, ratio * height);
        return (
          <View key={i} style={{ flex: 1, alignItems: 'center', gap: 6 }}>
            {isMax && formatValue ? (
              <Text style={{ fontSize: 10, color: theme.textSecondary, fontWeight: '800' }}>
                {formatValue(d.value)}
              </Text>
            ) : (
              <View style={{ height: 14 }} />
            )}
            {d.value > 0 ? (
              <Gradient
                colors={gradient}
                start={{ x: 0, y: 0 }}
                end={{ x: 0, y: 1 }}
                style={{ width: '64%', height: barHeight, borderRadius: 8, borderCurve: 'continuous' }}
              />
            ) : (
              <View
                style={{
                  width: '64%',
                  height: 4,
                  borderRadius: 8,
                  backgroundColor: theme.border,
                }}
              />
            )}
            <Text style={{ fontSize: 11, color: theme.textSecondary, fontWeight: '600' }}>
              {d.label}
            </Text>
          </View>
        );
      })}
    </View>
  );
}
