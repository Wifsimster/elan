import { useEffect, useState } from 'react';
import { Alert, Switch, Text, View } from 'react-native';

import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import {
  disableHealthConnect,
  enableHealthConnect,
  getHealthConnectEnabled,
  isHealthConnectSupported,
} from '@/lib/health-connect';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte Réglages : export opt-in des séances vers Android Health Connect. Ne
 * s'affiche que sur les plateformes où Health Connect est supporté (Android) —
 * renvoie `null` ailleurs.
 */
export function HealthConnectCard() {
  const theme = useTheme();
  const [healthEnabled, setHealthEnabled] = useState(false);
  const [healthBusy, setHealthBusy] = useState(false);

  useEffect(() => {
    getHealthConnectEnabled().then(setHealthEnabled);
  }, []);

  if (!isHealthConnectSupported()) return null;

  // Health Connect : opt-in. Les permissions système ne sont demandées qu'à
  // l'activation du toggle — jamais au lancement de l'app.
  const toggleHealthConnect = async (on: boolean) => {
    if (!on) {
      setHealthEnabled(false);
      disableHealthConnect().catch(() => {});
      return;
    }
    setHealthBusy(true);
    try {
      const res = await enableHealthConnect();
      if (res === 'granted') {
        setHealthEnabled(true);
      } else if (res === 'denied') {
        Alert.alert(
          'Permissions refusées',
          "Élan n'a pas reçu les permissions d'écriture. Tu peux les accorder dans l'app Health Connect, puis réactiver l'export ici.",
        );
      } else {
        Alert.alert(
          'Health Connect indisponible',
          "Health Connect n'est pas disponible sur cet appareil. Il fait partie d'Android 14+, et s'installe depuis le Play Store sur Android 8 à 13.",
        );
      }
    } finally {
      setHealthBusy(false);
    }
  };

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="heart-plus-outline" color={theme.accent} title="Health Connect" />
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600', flex: 1 }}>
          Exporter les séances
        </Text>
        <Switch value={healthEnabled} onValueChange={toggleHealthConnect} disabled={healthBusy} />
      </View>
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {healthEnabled
          ? 'Chaque séance terminée (type, durée, distance, calories, fréquence cardiaque) est copiée dans Health Connect, la base santé locale d’Android. Tout reste sur l’appareil, aucun cloud.'
          : 'Désactivé : tes séances restent uniquement dans Élan. Active pour les partager avec tes autres apps santé via Health Connect — stockage local Android, sans cloud ni compte.'}
      </Text>
    </Card>
  );
}
