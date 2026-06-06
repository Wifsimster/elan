import { type ReactNode } from 'react';
import { Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { Motion } from '@/constants/theme';
import { haptics } from '@/lib/haptics';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type HapticKind = keyof typeof haptics;

type Props = Omit<PressableProps, 'style'> & {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  /** Échelle atteinte pendant l'appui (défaut : Motion.pressScale). */
  scaleTo?: number;
  /** Retour haptique au relâchement utile (défaut : 'selection', `false` pour aucun). */
  haptic?: HapticKind | false;
};

/**
 * Surface tactile « ressort » : se comprime sous le doigt et rebondit au
 * relâchement, avec un retour haptique. Brique de base de toutes les
 * interactions PULSE (voir DESIGN.md § Mouvement & haptique).
 */
export function PressableScale({
  children,
  style,
  scaleTo = Motion.pressScale,
  haptic = 'selection',
  onPress,
  disabled,
  ...rest
}: Props) {
  // Respecte « Réduire les animations » du système : on conserve le retour
  // haptique mais on supprime le ressort d'échelle (confort vestibulaire).
  const reduceMotion = useReducedMotion();
  const scale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));

  return (
    <AnimatedPressable
      accessibilityRole="button"
      disabled={disabled}
      onPressIn={() => {
        if (reduceMotion) return;
        // Les shared values Reanimated se mutent par `.value` ; la règle
        // d'immutabilité du React Compiler est un faux positif ici.
        // eslint-disable-next-line react-hooks/immutability
        scale.value = withSpring(scaleTo, Motion.spring.snappy);
      }}
      onPressOut={() => {
        if (reduceMotion) return;
        // eslint-disable-next-line react-hooks/immutability
        scale.value = withSpring(1, Motion.spring.bouncy);
      }}
      onPress={(e) => {
        if (haptic) haptics[haptic]();
        onPress?.(e);
      }}
      style={[animatedStyle, style]}
      {...rest}>
      {children}
    </AnimatedPressable>
  );
}
