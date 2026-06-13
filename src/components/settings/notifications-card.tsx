import { useEffect, useState } from 'react';
import { Switch, Text, View } from 'react-native';

import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import { SettingStepper } from '@/components/settings/setting-stepper';
import {
  applyNotifications,
  DEFAULT_NOTIFICATION_CONFIG,
  getNotificationConfig,
  requestNotificationPermission,
  saveNotificationConfig,
  type NotificationConfig,
} from '@/lib/notifications';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : rappels locaux de séance (opt-in, jour + heure du planning). */
export function NotificationsCard() {
  const theme = useTheme();
  const [cfg, setCfg] = useState<NotificationConfig>(DEFAULT_NOTIFICATION_CONFIG);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getNotificationConfig().then(setCfg);
  }, []);

  const toggleEnabled = async (next: boolean) => {
    setError(null);
    if (next) {
      const granted = await requestNotificationPermission();
      if (!granted) {
        setError(
          "Permission refusée. Active les notifications de l'app dans les réglages système pour utiliser les rappels.",
        );
        return;
      }
    }
    const updated: NotificationConfig = { ...cfg, enabled: next };
    setCfg(updated);
    await saveNotificationConfig(updated);
    await applyNotifications();
  };

  const changeHour = async (hour: number) => {
    const updated: NotificationConfig = { ...cfg, hour };
    setCfg(updated);
    await saveNotificationConfig(updated);
    if (updated.enabled) await applyNotifications();
  };

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="bell-outline" color={theme.accent} title="Rappels de séance" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {"Un rappel local le jour même d'une séance prévue, à l'heure de ton choix (midi par défaut). Il liste les exercices du programme du jour pour t'aider à le respecter. Aucune notification les jours de repos, et aucune relance si la séance est manquée. 100 % local — aucune connexion réseau."}
      </Text>

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>
          Activer les rappels
        </Text>
        <Switch value={cfg.enabled} onValueChange={toggleEnabled} trackColor={{ true: theme.accent }} />
      </View>

      <SettingStepper
        label="Heure du rappel"
        value={cfg.hour}
        unit="h"
        step={1}
        min={0}
        max={23}
        onChange={changeHour}
      />

      {error ? <Text style={{ color: theme.danger, fontSize: 13 }}>{error}</Text> : null}
    </Card>
  );
}
