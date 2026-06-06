import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Radius, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Action = {
  label: string;
  icon?: keyof typeof MaterialCommunityIcons.glyphMap;
  onPress: () => void;
};

type Props = {
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  title: string;
  subtitle?: string;
  /** Teinte de la pastille d'icône (défaut : accent). */
  tint?: string;
  /** Action proposée pour sortir de l'état vide (ex. « Importer depuis Strava »). */
  action?: Action;
};

/**
 * État vide PULSE : pastille d'icône teintée (comme les autres surfaces de repos),
 * titre/sous-titre sur l'échelle typographique, et une action facultative pour ne
 * jamais laisser l'utilisateur dans une impasse.
 */
export function EmptyState({ icon, title, subtitle, tint, action }: Props) {
  const theme = useTheme();
  const color = tint ?? theme.accent;
  return (
    <View style={{ alignItems: 'center', gap: 10, paddingVertical: 44, paddingHorizontal: 24 }}>
      <View
        style={{
          width: 64,
          height: 64,
          borderRadius: Radius.lg,
          borderCurve: 'continuous',
          backgroundColor: color + '22',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 2,
        }}>
        <MaterialCommunityIcons name={icon} size={32} color={color} />
      </View>
      <Text style={{ ...Type.headline, color: theme.text, textAlign: 'center' }}>{title}</Text>
      {subtitle ? (
        <Text style={{ ...Type.body, color: theme.textSecondary, textAlign: 'center' }}>
          {subtitle}
        </Text>
      ) : null}
      {action ? (
        <View style={{ marginTop: 8 }}>
          <Button
            title={action.label}
            icon={action.icon}
            variant="secondary"
            color={color}
            onPress={action.onPress}
          />
        </View>
      ) : null}
    </View>
  );
}
