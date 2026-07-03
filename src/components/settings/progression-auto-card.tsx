import { useEffect, useState } from 'react';
import { Switch, Text, View } from 'react-native';

import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import {
  DEFAULT_AUTO_PROGRESSION_CONFIG,
  getAutoProgressionConfig,
  saveAutoProgressionEnabled,
  type AutoProgressionConfig,
} from '@/lib/auto-progression';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte Réglages : progression automatique du programme muscu. Activée par
 * défaut — elle relève la charge des exercices notés « facile » et allège ceux
 * notés « dur », d'une semaine sur l'autre. L'utilisateur peut la couper ici.
 */
export function ProgressionAutoCard() {
  const theme = useTheme();
  const [cfg, setCfg] = useState<AutoProgressionConfig>(DEFAULT_AUTO_PROGRESSION_CONFIG);

  useEffect(() => {
    getAutoProgressionConfig().then(setCfg);
  }, []);

  const toggle = async (next: boolean) => {
    setCfg({ enabled: next });
    await saveAutoProgressionEnabled(next);
  };

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="trending-up" color={theme.muscu} title="Progression automatique" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {
          "Élan relève un peu la charge conseillée des exercices que tu notes « facile », et l'allège si tu notes « dur » — d'une semaine sur l'autre, d'après ton ressenti. Un cran de 2,5 kg au maximum par semaine, jamais sur les exercices de renfort doux (dos, cervicales). 100 % local. Tu gardes la main : chaque charge reste modifiable pendant la séance."
        }
      </Text>

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600', flex: 1, paddingRight: 12 }}>
          Ajuster le programme selon mon ressenti
        </Text>
        <Switch value={cfg.enabled} onValueChange={toggle} trackColor={{ true: theme.muscu }} />
      </View>
    </Card>
  );
}
