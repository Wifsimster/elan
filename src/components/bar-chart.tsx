import { Text, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

export type Bar = { label: string; value: number };

type Props = {
  data: Bar[];
  color?: string;
  height?: number;
  /** Formate la valeur affichée au-dessus de la barre la plus haute. */
  formatValue?: (v: number) => string;
};

export function BarChart({ data, color, height = 120, formatValue }: Props) {
  const theme = useTheme();
  const max = Math.max(1, ...data.map((d) => d.value));
  const barColor = color ?? theme.accent;

  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 6, height: height + 24 }}>
      {data.map((d, i) => {
        const ratio = d.value / max;
        const isMax = d.value === max && d.value > 0;
        return (
          <View key={i} style={{ flex: 1, alignItems: 'center', gap: 4 }}>
            {isMax && formatValue ? (
              <Text style={{ fontSize: 10, color: theme.textSecondary, fontWeight: '700' }}>
                {formatValue(d.value)}
              </Text>
            ) : (
              <View style={{ height: 14 }} />
            )}
            <View
              style={{
                width: '70%',
                height: Math.max(2, ratio * height),
                backgroundColor: d.value > 0 ? barColor : theme.border,
                borderRadius: 6,
                borderCurve: 'continuous',
                opacity: d.value > 0 ? 1 : 0.5,
              }}
            />
            <Text style={{ fontSize: 10, color: theme.textSecondary }}>{d.label}</Text>
          </View>
        );
      })}
    </View>
  );
}
