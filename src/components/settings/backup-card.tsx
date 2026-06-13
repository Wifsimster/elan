import { Alert, Switch, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { SettingCardHeader } from '@/components/setting-card-header';
import { SettingField } from '@/components/settings/setting-field';
import { formatDateTime } from '@/lib/format';
import { useBackup } from '@/hooks/use-backup';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : sauvegarde/restauration vers un stockage S3 auto-hébergé (opt-in). */
export function BackupCard() {
  const theme = useTheme();
  const backup = useBackup();

  const confirmRestore = () => {
    Alert.alert(
      'Restaurer la sauvegarde ?',
      'Les données locales actuelles seront REMPLACÉES par celles du serveur. Cette action est irréversible.',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Restaurer',
          style: 'destructive',
          onPress: async () => {
            const count = await backup.restore();
            if (count != null) {
              Alert.alert('Restauration terminée', `${count} séance(s) restaurée(s) depuis le serveur.`);
            }
          },
        },
      ],
    );
  };

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="cloud-upload-outline" color={theme.accent} title="Sauvegarde homelab" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {'Backup automatique de toutes tes données vers ton stockage S3 (MinIO). Elles restent sur ton serveur — rien chez un tiers.'}
      </Text>

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>
          Sauvegarde automatique
        </Text>
        <Switch
          value={backup.config?.enabled ?? false}
          onValueChange={(v) => backup.update({ enabled: v })}
          trackColor={{ true: theme.accent }}
        />
      </View>

      <BackupStatusLine />

      <SettingField
        label="Endpoint"
        placeholder="https://minio.mon-homelab.tld"
        value={backup.config?.endpoint ?? ''}
        onChangeText={(t) => backup.update({ endpoint: t })}
        keyboardType="url"
      />
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 2 }}>
          <SettingField
            label="Bucket"
            placeholder="suivi-sport"
            value={backup.config?.bucket ?? ''}
            onChangeText={(t) => backup.update({ bucket: t })}
          />
        </View>
        <View style={{ flex: 1 }}>
          <SettingField
            label="Région"
            placeholder="us-east-1"
            value={backup.config?.region ?? ''}
            onChangeText={(t) => backup.update({ region: t })}
          />
        </View>
      </View>
      <SettingField
        label="Access key"
        placeholder="Clé d'accès"
        value={backup.config?.accessKeyId ?? ''}
        onChangeText={(t) => backup.update({ accessKeyId: t })}
      />
      <SettingField
        label="Secret key"
        placeholder="Clé secrète"
        value={backup.config?.secretAccessKey ?? ''}
        onChangeText={(t) => backup.update({ secretAccessKey: t })}
        secureTextEntry
      />
      <SettingField
        label="Nom de l'objet"
        placeholder="suivi-sport-backup.json"
        value={backup.config?.objectKey ?? ''}
        onChangeText={(t) => backup.update({ objectKey: t })}
      />

      {backup.error ? <Text style={{ color: theme.danger, fontSize: 13 }}>{backup.error}</Text> : null}

      <Button
        title="Sauvegarder maintenant"
        icon="cloud-upload"
        color={theme.accent}
        loading={backup.status === 'saving'}
        disabled={!backup.ready}
        onPress={backup.backupNow}
      />
      <Button
        title="Restaurer depuis le serveur"
        icon="cloud-download-outline"
        variant="secondary"
        color={theme.accent}
        loading={backup.status === 'restoring'}
        disabled={!backup.ready}
        onPress={confirmRestore}
      />
    </Card>
  );
}

/** Ligne d'état de la dernière sauvegarde : pastille colorée + libellé daté. */
function BackupStatusLine() {
  const theme = useTheme();
  const { last, status } = useBackup();

  let color: string = theme.textSecondary;
  let label = 'Aucune sauvegarde pour l’instant';
  if (status === 'saving') {
    color = theme.warning;
    label = 'Sauvegarde en cours…';
  } else if (status === 'restoring') {
    color = theme.warning;
    label = 'Restauration en cours…';
  } else if (last?.ok) {
    color = theme.success;
    label = `Dernière sauvegarde : ${formatDateTime(last.at)}`;
  } else if (last && !last.ok) {
    color = theme.danger;
    label = `Échec le ${formatDateTime(last.at)}`;
  }

  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: color }} />
      <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1 }}>{label}</Text>
    </View>
  );
}
