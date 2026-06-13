import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Alert, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import { useStravaImport } from '@/hooks/use-strava-import';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : import de séances depuis un fichier Strava (GPX/TCX/FIT). */
export function StravaImportCard() {
  const theme = useTheme();
  const strava = useStravaImport();

  const runStravaImport = async () => {
    const res = await strava.pickAndImport();
    if (!res) return;
    Alert.alert(
      'Import Strava',
      `${res.imported} séance(s) importée(s)\n` +
        `${res.duplicates} doublon(s) ignoré(s)\n` +
        `${res.skipped} activité(s) ignorée(s)` +
        (res.errors ? `\n${res.errors} fichier(s) en erreur` : ''),
    );
  };

  return (
    <Card style={{ gap: 12 }}>
      <SettingCardHeader icon="cloud-download-outline" color={theme.velo} title="Import Strava" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {"Exporte depuis Strava (page de l'activité → « Exporter GPX », ou « Télécharger tes données » dans les réglages du compte), puis importe le fichier ici. Formats acceptés : GPX, TCX et FIT, y compris compressés (.gz) — comme dans l'export en masse. Tout est traité sur l'appareil."}
      </Text>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
        <MaterialCommunityIcons name="information-outline" size={18} color={theme.textMuted} />
        <Text style={{ color: theme.textMuted, fontSize: 12, flex: 1 }}>
          {"Pas de synchronisation automatique avec ton compte Strava : cela demanderait un serveur, incompatible avec le fonctionnement 100 % hors-ligne de l'app. La ré-importation d'un même fichier ne crée pas de doublon."}
        </Text>
      </View>

      <Button
        title="Importer un fichier (GPX/TCX/FIT)"
        icon="file-import-outline"
        color={theme.velo}
        loading={strava.importing}
        onPress={runStravaImport}
      />

      {strava.error ? <Text style={{ color: theme.danger, fontSize: 13 }}>{strava.error}</Text> : null}

      {strava.result ? (
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {`Dernier import : ${strava.result.imported} importée(s), ${strava.result.duplicates} doublon(s), ${strava.result.skipped} ignorée(s)` +
            (strava.result.errors ? `, ${strava.result.errors} erreur(s)` : '')}
        </Text>
      ) : null}
    </Card>
  );
}
