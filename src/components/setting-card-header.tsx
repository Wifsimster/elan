import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { Text, View } from 'react-native';

import { Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

/**
 * En-tête d'une carte de réglages : icône colorée + titre de section. Uniformise
 * le motif répété dans l'écran Réglages (icône taille 22, titre `Type.sectionTitle`)
 * pour garder une présentation cohérente d'une carte à l'autre.
 */
export function SettingCardHeader({
  icon,
  color,
  title,
}: {
  icon: IconName;
  /** Couleur de l'icône (accent thématique de la section). */
  color: string;
  title: string;
}) {
  const theme = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <MaterialCommunityIcons name={icon} size={22} color={color} />
      <Text style={{ ...Type.sectionTitle, color: theme.text }}>{title}</Text>
    </View>
  );
}
