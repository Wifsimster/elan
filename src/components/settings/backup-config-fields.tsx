import { View } from 'react-native';

import { SettingField } from '@/components/settings/setting-field';
import { useBackup } from '@/hooks/use-backup';

/**
 * Champs de connexion S3 (endpoint, bucket, région, clés, objet), liés au
 * contexte de sauvegarde. Partagés entre la carte Réglages et la feuille de
 * restauration du premier lancement pour que les deux saisies restent
 * identiques (mêmes libellés, mêmes placeholders, même persistance).
 */
export function BackupConfigFields() {
  const backup = useBackup();

  return (
    <View style={{ gap: 14 }}>
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
    </View>
  );
}
