import { Text, View } from 'react-native';

import { Gradient } from '@/components/gradient';
import { Radius, type GradientName } from '@/constants/theme';
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
        const hasValue = d.value > 0;
        const barHeight = Math.max(4, ratio * height);
        return (
          <View key={i} style={{ flex: 1, alignItems: 'center', gap: 6 }}>
            {/* Valeur au-dessus de chaque barre non nulle (réservation d'espace sinon). */}
            {hasValue && formatValue ? (
              <Text style={{ fontSize: 10, color: theme.textSecondary, fontWeight: '800' }}>
                {formatValue(d.value)}
              </Text>
            ) : (
              <View style={{ height: 14 }} />
            )}
            {/* Rail de fond pleine hauteur : chaque jour garde une présence visuelle
                (sans rail, une semaine creuse paraît « cassée »). La barre se pose
                par-dessus, alignée en bas. */}
            <View
              style={{
                width: '64%',
                height,
                borderRadius: Radius.sm,
                borderCurve: 'continuous',
                backgroundColor: theme.hairline,
                justifyContent: 'flex-end',
                overflow: 'hidden',
              }}>
              {hasValue ? (
                <Gradient
                  colors={gradient}
                  start={{ x: 0, y: 0 }}
                  end={{ x: 0, y: 1 }}
                  style={{ width: '100%', height: barHeight, borderRadius: Radius.sm, borderCurve: 'continuous' }}
                />
              ) : null}
            </View>
            <Text style={{ fontSize: 11, color: theme.textSecondary, fontWeight: '600' }}>
              {d.label}
            </Text>
          </View>
        );
      })}
    </View>
  );
}
