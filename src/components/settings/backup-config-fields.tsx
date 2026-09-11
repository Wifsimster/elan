import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { QrScanSheet } from '@/components/qr-scan-sheet';
import { SettingField } from '@/components/settings/setting-field';
import { DEFAULT_OBJECT_KEY, DEFAULT_REGION } from '@/lib/backup';
import { describeQrPatch, parseBackupQr } from '@/lib/backup-qr';
import { useBackup } from '@/hooks/use-backup';
import { useTheme } from '@/hooks/use-theme';

/**
 * Champs de connexion S3, liés au contexte de sauvegarde. Partagés entre la
 * carte Réglages et la feuille de restauration du premier lancement pour que
 * les deux saisies restent identiques (mêmes libellés, mêmes placeholders,
 * même persistance).
 *
 * Quatre champs suffisent (endpoint, bucket, clés) : région et nom d'objet sont
 * pré-remplis avec des défauts qui conviennent à MinIO/SeaweedFS/Garage et
 * restent modifiables sous « Options avancées », dépliées d'office si la valeur
 * stockée s'écarte du défaut.
 */
export function BackupConfigFields() {
  const theme = useTheme();
  const backup = useBackup();
  const hasAdvanced = Boolean(
    backup.config &&
      ((backup.config.region && backup.config.region !== DEFAULT_REGION) ||
        (backup.config.objectKey && backup.config.objectKey !== DEFAULT_OBJECT_KEY)),
  );
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const showAdvanced = advancedOpen || hasAdvanced;
  const [scanning, setScanning] = useState(false);

  // QR lu : on applique le fragment (les champs absents du QR sont conservés)
  // et on dit lesquels ont été remplis — le secret reste masqué à l'écran.
  const onScanned = (data: string) => {
    setScanning(false);
    const patch = parseBackupQr(data);
    if (!patch) {
      Alert.alert(
        'QR code non reconnu',
        'Ce QR ne contient pas de configuration S3 (JSON ou s3://…). Voir docs/SAUVEGARDE.md pour le format.',
      );
      return;
    }
    backup.update(patch);
    Alert.alert('Configuration importée', `Champs remplis : ${describeQrPatch(patch)}.`);
  };

  return (
    <View style={{ gap: 14 }}>
      {/* Éviter de taper une clé secrète au clavier : QR généré sur le serveur. */}
      <Button
        title="Scanner un QR code de configuration"
        icon="qrcode-scan"
        variant="secondary"
        color={theme.accent}
        onPress={() => setScanning(true)}
      />
      <QrScanSheet
        visible={scanning}
        title="Configuration S3"
        hint="Vise le QR code généré pour Élan : endpoint, bucket et clés seront remplis automatiquement."
        onCancel={() => setScanning(false)}
        onScanned={onScanned}
      />
      <SettingField
        label="Endpoint"
        placeholder="https://s3.mon-homelab.tld"
        value={backup.config?.endpoint ?? ''}
        onChangeText={(t) => backup.update({ endpoint: t })}
        keyboardType="url"
      />
      <SettingField
        label="Bucket"
        placeholder="elan"
        value={backup.config?.bucket ?? ''}
        onChangeText={(t) => backup.update({ bucket: t })}
      />
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

      {showAdvanced ? (
        <>
          <View style={{ flexDirection: 'row', gap: 12 }}>
            <View style={{ flex: 1 }}>
              <SettingField
                label="Région"
                placeholder={DEFAULT_REGION}
                value={backup.config?.region ?? ''}
                onChangeText={(t) => backup.update({ region: t })}
              />
            </View>
            <View style={{ flex: 1 }}>
              <SettingField
                label="Nom de l'objet"
                placeholder={DEFAULT_OBJECT_KEY}
                value={backup.config?.objectKey ?? ''}
                onChangeText={(t) => backup.update({ objectKey: t })}
              />
            </View>
          </View>
          <Text style={{ color: theme.textMuted, fontSize: 12 }}>
            Vidés, ils reviennent aux défauts ({DEFAULT_REGION}, {DEFAULT_OBJECT_KEY}).
          </Text>
        </>
      ) : (
        <Pressable
          onPress={() => setAdvancedOpen(true)}
          hitSlop={8}
          accessibilityRole="button"
          style={{ flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start' }}>
          <MaterialCommunityIcons name="chevron-right" size={18} color={theme.accent} />
          <Text style={{ color: theme.accent, fontSize: 13, fontWeight: '600' }}>
            Options avancées (région, nom de l&apos;objet)
          </Text>
        </Pressable>
      )}
    </View>
  );
}
