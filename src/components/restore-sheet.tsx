import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Modal, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { BackupConfigFields } from '@/components/settings/backup-config-fields';
import { Radius, Type } from '@/constants/theme';
import { useBackup } from '@/hooks/use-backup';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  visible: boolean;
  /** Retour à l'accueil premier lancement sans restaurer. */
  onCancel: () => void;
  /** Restauration réussie : `count` séances rechargées depuis le serveur. */
  onRestored: (count: number) => void;
};

/**
 * Feuille « J'ai déjà une sauvegarde » du premier lancement : après une
 * réinstallation, la base est vide — y compris la config S3, qui vit dans la
 * même base. On redemande donc les identifiants du serveur, on récupère le
 * snapshot, et on active dans la foulée la sauvegarde automatique vers ce même
 * serveur (l'utilisateur vient de montrer qu'il veut ses données à l'abri).
 * Pas de confirmation destructive ici : il n'y a encore rien à écraser.
 */
export function RestoreSheet({ visible, onCancel, onRestored }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const backup = useBackup();

  const restore = async () => {
    const count = await backup.restore();
    if (count == null) return; // l'erreur est affichée sous les champs
    backup.update({ enabled: true });
    onRestored(count);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent>
      <ScrollView
        style={{ flex: 1, backgroundColor: theme.scrim }}
        contentContainerStyle={{
          flexGrow: 1,
          justifyContent: 'center',
          paddingVertical: insets.top + 24,
          paddingLeft: insets.left + 24,
          paddingRight: insets.right + 24,
        }}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled">
        <View
          style={{
            backgroundColor: theme.backgroundElement,
            borderRadius: Radius.xl,
            borderCurve: 'continuous',
            padding: 24,
            gap: 16,
            width: '100%',
            maxWidth: 480,
            alignSelf: 'center',
          }}>
          <View
            style={{
              width: 64,
              height: 64,
              borderRadius: Radius.lg,
              borderCurve: 'continuous',
              backgroundColor: theme.accent + '22',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <MaterialCommunityIcons name="cloud-download-outline" size={32} color={theme.accent} />
          </View>

          <View style={{ gap: 6 }}>
            <Text style={{ ...Type.title, color: theme.text }}>Restaurer une sauvegarde</Text>
            <Text style={{ ...Type.body, color: theme.textSecondary }}>
              Renseigne ton stockage S3 auto-hébergé : tes séances, ton profil et tes réglages
              seront rechargés depuis le serveur.
            </Text>
          </View>

          <BackupConfigFields />

          {backup.error ? (
            <Text style={{ color: theme.danger, fontSize: 13 }}>{backup.error}</Text>
          ) : null}

          <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
            <MaterialCommunityIcons name="autorenew" size={18} color={theme.success} />
            <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1, lineHeight: 19 }}>
              La sauvegarde automatique après chaque séance sera activée vers ce serveur
              (modifiable dans Réglages).
            </Text>
          </View>

          <Button
            title="Restaurer"
            icon="cloud-download"
            loading={backup.status === 'restoring'}
            disabled={!backup.ready}
            onPress={restore}
          />
          <Button
            title="Retour"
            variant="ghost"
            color={theme.accent}
            disabled={backup.status === 'restoring'}
            onPress={onCancel}
          />
        </View>
      </ScrollView>
    </Modal>
  );
}
