import { MaterialCommunityIcons } from '@expo/vector-icons';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { useEffect, useState } from 'react';
import { Modal, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Colors, Radius, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  visible: boolean;
  title: string;
  hint: string;
  onCancel: () => void;
  onScanned: (data: string) => void;
};

function Scanner({ onScanned }: { onScanned: (data: string) => void }) {
  const [consumed, setConsumed] = useState(false);
  return (
    <CameraView
      style={{ flex: 1 }}
      facing="back"
      barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
      onBarcodeScanned={
        consumed
          ? undefined
          : ({ data }) => {
              setConsumed(true);
              onScanned(data);
            }
      }
    />
  );
}

export function QrScanSheet({ visible, title, hint, onCancel, onScanned }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();

  useEffect(() => {
    if (visible && permission && !permission.granted && permission.canAskAgain) {
      requestPermission();
    }
  }, [visible, permission, requestPermission]);

  const granted = permission?.granted ?? false;

  return (
    <Modal visible={visible} animationType="slide" statusBarTranslucent onRequestClose={onCancel}>
      <View style={{ flex: 1, backgroundColor: theme.background }}>
        {granted ? (
          <Scanner onScanned={onScanned} />
        ) : (
          <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 12 }}>
            <MaterialCommunityIcons name="camera-off-outline" size={40} color={theme.textMuted} />
            <Text style={{ ...Type.body, color: theme.textSecondary, textAlign: 'center' }}>
              {permission?.canAskAgain === false
                ? "L'accès à la caméra a été refusé. Autorise-le dans les paramètres Android de l'application."
                : 'Autorise la caméra pour lire le QR code.'}
            </Text>
            {permission?.canAskAgain !== false ? (
              <Button title="Autoriser la caméra" icon="camera" onPress={requestPermission} />
            ) : null}
          </View>
        )}

        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            paddingTop: insets.top + 16,
            paddingHorizontal: 20,
            paddingBottom: 16,
            backgroundColor: theme.scrim,
            gap: 4,
          }}>
          <Text style={{ ...Type.title, color: Colors.dark.text }}>{title}</Text>
          <Text style={{ fontSize: 13, lineHeight: 19, color: Colors.dark.textSecondary }}>{hint}</Text>
        </View>

        <View
          style={{
            position: 'absolute',
            left: 20,
            right: 20,
            bottom: insets.bottom + 20,
            borderRadius: Radius.lg,
            borderCurve: 'continuous',
          }}>
          <Button title="Annuler" variant="secondary" icon="close" onPress={onCancel} />
        </View>
      </View>
    </Modal>
  );
}
