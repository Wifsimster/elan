import { Text } from 'react-native';

import { PressableScale } from '@/components/pressable-scale';
import { OnBright, Radius } from '@/constants/theme';
import { bestInk } from '@/lib/contrast';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  label: string;
  selected?: boolean;
  /** Teinte à l'état sélectionné (défaut : accent). */
  color?: string;
  onPress?: () => void;
};

/** Pastille de filtre / suggestion PULSE, sélectionnable, avec appui ressort. */
export function Chip({ label, selected, color, onPress }: Props) {
  const theme = useTheme();
  const tint = color ?? theme.accent;
  // Sélectionnée, la puce peint son fond avec la teinte : l'encre est choisie
  // pour rester lisible dessus (du blanc sur le lime de la marche ou le teal du
  // vélo tombe sous 2:1).
  const ink = bestInk(tint, [OnBright, '#FFFFFF']);

  return (
    <PressableScale
      onPress={onPress}
      scaleTo={0.94}
      style={{
        paddingHorizontal: 16,
        paddingVertical: 9,
        borderRadius: Radius.pill,
        borderWidth: 1,
        backgroundColor: selected ? tint : theme.backgroundElement,
        borderColor: selected ? tint : theme.border,
      }}>
      <Text
        style={{
          color: selected ? ink : theme.text,
          fontWeight: '700',
          fontSize: 14,
          letterSpacing: -0.2,
        }}>
        {label}
      </Text>
    </PressableScale>
  );
}
