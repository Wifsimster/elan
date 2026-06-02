import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ActivityIndicator, StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';

import { Gradient } from '@/components/gradient';
import { PressableScale } from '@/components/pressable-scale';
import { Elevation, type GradientName, Gradients, Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
type Size = 'md' | 'lg';

type Props = {
  title: string;
  icon?: keyof typeof MaterialCommunityIcons.glyphMap;
  variant?: Variant;
  size?: Size;
  /** Teinte de l'action (défaut : accent). Choisit aussi le dégradé `primary`. */
  color?: string;
  /** Dégradé explicite pour la variante `primary`. Sinon déduit de `color`. */
  gradient?: GradientName;
  loading?: boolean;
  disabled?: boolean;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
};

/** Déduit le dégradé correspondant à une couleur d'activité du thème. */
function gradientFor(color: string | undefined, theme: ReturnType<typeof useTheme>): GradientName {
  if (color === theme.velo) return 'velo';
  if (color === theme.muscu) return 'muscu';
  if (color === theme.heart) return 'heart';
  if (color === theme.warning) return 'fire';
  return 'accent';
}

/**
 * Bouton PULSE : remplissage en dégradé + ombre colorée pour l'action
 * principale, contour pour le secondaire. Appui « ressort » et haptique inclus.
 */
export function Button({
  title,
  icon,
  variant = 'primary',
  size = 'md',
  color,
  gradient,
  loading,
  disabled,
  onPress,
  style,
}: Props) {
  const theme = useTheme();
  const accent = color ?? theme.accent;
  const grad = gradient ?? (variant === 'danger' ? 'danger' : gradientFor(accent, theme));

  const fg =
    variant === 'primary' || variant === 'danger'
      ? '#FFFFFF'
      : variant === 'ghost'
        ? theme.textSecondary
        : accent;

  const padV = size === 'lg' ? 17 : 14;
  const fontSize = size === 'lg' ? 17 : 16;

  const inner = (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <>
          {icon ? <MaterialCommunityIcons name={icon} size={size === 'lg' ? 22 : 20} color={fg} /> : null}
          <Text style={{ color: fg, fontSize, fontWeight: '800', letterSpacing: -0.2 }}>{title}</Text>
        </>
      )}
    </View>
  );

  const base: ViewStyle = {
    borderRadius: Radius.lg,
    borderCurve: 'continuous',
    paddingVertical: padV,
    paddingHorizontal: 20,
    opacity: disabled ? 0.4 : 1,
  };

  // Action pleine : dégradé + ombre teintée à la couleur de l'action.
  //
  // L'ombre vit sur la vue extérieure (fond opaque + coins arrondis) : sur
  // Android, une vue élevée à fond transparent projette une ombre rectangulaire
  // noire et décalée. Le dégradé est isolé dans une vue intérieure
  // `overflow:'hidden'` qui le découpe aux coins arrondis.
  if (variant === 'primary' || variant === 'danger') {
    const shadowColor = variant === 'danger' ? theme.danger : accent;
    return (
      <PressableScale
        disabled={disabled || loading}
        haptic="light"
        onPress={onPress}
        style={[
          {
            borderRadius: Radius.lg,
            borderCurve: 'continuous',
            backgroundColor: accent,
            opacity: disabled ? 0.4 : 1,
            ...Elevation.md,
            shadowColor,
            shadowOpacity: 0.45,
          },
          style,
        ]}>
        <View
          style={{
            borderRadius: Radius.lg,
            borderCurve: 'continuous',
            overflow: 'hidden',
            paddingVertical: padV,
            paddingHorizontal: 20,
          }}>
          <Gradient colors={Gradients[grad]} style={StyleSheet.absoluteFill} />
          {inner}
        </View>
      </PressableScale>
    );
  }

  // Secondaire (contour) / ghost (plat).
  return (
    <PressableScale
      disabled={disabled || loading}
      haptic="selection"
      onPress={onPress}
      style={[
        base,
        variant === 'secondary'
          ? { borderWidth: 1.5, borderColor: accent, backgroundColor: 'transparent' }
          : { backgroundColor: 'transparent' },
        style,
      ]}>
      {inner}
    </PressableScale>
  );
}
