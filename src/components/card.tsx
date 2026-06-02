import { View, type ViewProps } from 'react-native';

import { Elevation, Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = ViewProps & {
  /**
   * - `elevated` (défaut) : surface flottante avec ombre douce, sans bordure.
   * - `inset` : creux discret (champs, sous-blocs), bordure fine sans ombre.
   * - `plain` : surface simple sans ombre ni bordure.
   */
  variant?: 'elevated' | 'inset' | 'plain';
};

/** Surface de contenu PULSE : coins continus, profondeur par l'ombre (voir DESIGN.md). */
export function Card({ style, variant = 'elevated', ...rest }: Props) {
  const theme = useTheme();

  const surface =
    variant === 'inset'
      ? { backgroundColor: theme.background, borderWidth: 1, borderColor: theme.border }
      : variant === 'plain'
        ? { backgroundColor: theme.backgroundElement }
        : { backgroundColor: theme.backgroundElement, ...Elevation.sm };

  return (
    <View
      style={[
        {
          borderRadius: Radius.lg,
          borderCurve: 'continuous',
          padding: 16,
          gap: 12,
        },
        surface,
        style,
      ]}
      {...rest}
    />
  );
}
