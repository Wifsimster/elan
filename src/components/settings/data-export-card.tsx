import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { SettingCardHeader } from '@/components/setting-card-header';
import { getPrivacyZoneM, PRIVACY_ZONE_OPTIONS, setPrivacyZoneM } from '@/lib/privacy';
import { useDataExport } from '@/hooks/use-data-export';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte Réglages : export du bilan (Markdown) / des données brutes (JSON) via la
 * feuille de partage système, et réglage de la zone de confidentialité des
 * tracés GPX exportés.
 */
export function DataExportCard() {
  const theme = useTheme();
  const dataExport = useDataExport();
  const [privacyZoneM, setPrivacyZone] = useState(0);

  useEffect(() => {
    getPrivacyZoneM().then(setPrivacyZone);
  }, []);

  const updatePrivacyZone = (meters: number) => {
    setPrivacyZone(meters);
    setPrivacyZoneM(meters).catch(() => {});
  };

  return (
    <Card style={{ gap: 12 }}>
      <SettingCardHeader icon="export-variant" color={theme.accent} title="Exporter mes données" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {"Génère un bilan lisible de ton programme, de ta progression et de ton historique, ou un export brut complet. À déposer dans l'outil de ton choix : coach IA, tableur, sauvegarde personnelle… Tout est généré sur l'appareil, le partage se fait via la feuille système (Drive, mail, fichier…)."}
      </Text>

      {/* Zone de confidentialité : rogne le départ/arrivée des exports GPS. */}
      <View style={{ gap: 8 }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>
          Zone de confidentialité
        </Text>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {"Masque le début et la fin de tes tracés (souvent ton domicile) dans l'export GPX (Strava), en retirant les points proches du départ et de l'arrivée."}
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {PRIVACY_ZONE_OPTIONS.map((m) => (
            <Chip
              key={m}
              label={m === 0 ? 'Désactivée' : `${m} m`}
              selected={privacyZoneM === m}
              color={theme.velo}
              onPress={() => updatePrivacyZone(m)}
            />
          ))}
        </View>
      </View>

      <Button
        title="Exporter le bilan (Markdown)"
        icon="file-document-outline"
        color={theme.accent}
        loading={dataExport.exporting === 'markdown'}
        onPress={dataExport.exportMarkdown}
      />
      <Button
        title="Exporter les données brutes (JSON)"
        icon="code-json"
        variant="secondary"
        color={theme.accent}
        loading={dataExport.exporting === 'json'}
        onPress={dataExport.exportJson}
      />
      {dataExport.error ? (
        <Text style={{ color: theme.danger, fontSize: 13 }}>{dataExport.error}</Text>
      ) : null}
    </Card>
  );
}
