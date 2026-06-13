import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Alert, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import { clearAllData, clearAllDataIncludingSettings } from '@/lib/db';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : gestion des données locales (effacement, réinitialisation). */
export function DataCard() {
  const theme = useTheme();

  const confirmClear = () => {
    Alert.alert(
      'Effacer toutes les séances ?',
      'Toutes les séances enregistrées seront supprimées définitivement. Le profil et la ceinture appairée sont conservés.',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Tout effacer',
          style: 'destructive',
          onPress: async () => {
            try {
              await clearAllData();
              Alert.alert('Données effacées', 'Toutes les séances ont été supprimées.');
            } catch {
              Alert.alert('Erreur', "L'effacement a échoué.");
            }
          },
        },
      ],
    );
  };

  const confirmClearEverything = () => {
    Alert.alert(
      'Tout réinitialiser ?',
      'Séances, profil, FC max, capteurs appairés, planning et réglages seront effacés définitivement. L’application repart de zéro.',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Tout réinitialiser',
          style: 'destructive',
          onPress: async () => {
            try {
              await clearAllDataIncludingSettings();
              Alert.alert('Réinitialisé', 'Toutes tes données et réglages ont été effacés.');
            } catch {
              Alert.alert('Erreur', 'La réinitialisation a échoué.');
            }
          },
        },
      ],
    );
  };

  return (
    <Card style={{ gap: 12 }}>
      <SettingCardHeader icon="database-outline" color={theme.accent} title="Données" />
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
        <MaterialCommunityIcons name="lock-outline" size={18} color={theme.success} />
        <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1 }}>
          {"Toutes tes données restent sur cet appareil (base SQLite locale). Rien n'est envoyé sur internet."}
        </Text>
      </View>
      <Button
        title="Effacer toutes les séances"
        icon="trash-can-outline"
        variant="danger"
        onPress={confirmClear}
      />
      <Button
        title="Tout réinitialiser (profil + réglages)"
        icon="delete-forever-outline"
        variant="secondary"
        color={theme.danger}
        onPress={confirmClearEverything}
      />
      <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
        {'« Tout réinitialiser » efface aussi le profil, les capteurs appairés et les réglages — l’app repart comme une nouvelle installation.'}
      </Text>
    </Card>
  );
}
