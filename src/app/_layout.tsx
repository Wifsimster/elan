import { MaterialCommunityIcons } from '@expo/vector-icons';
import { DarkTheme, DefaultTheme, type ErrorBoundaryProps, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Pressable, Text, useColorScheme, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { Radius, Type } from '@/constants/theme';
import { BackupProvider } from '@/hooks/use-backup';
import { CadenceSpeedProvider } from '@/hooks/use-cadence-speed';
import { HeartRateProvider } from '@/hooks/use-heart-rate';
import { useTheme } from '@/hooks/use-theme';
import { applyNotifications } from '@/lib/notifications';

export const unstable_settings = {
  anchor: '(tabs)',
};

/**
 * Filet de sécurité racine : expo-router rend ce composant à la place de l'arbre
 * de routes si un rendu lève, au lieu d'un écran blanc. Volontairement minimal —
 * `<Pressable>` brut plutôt que `<Button>`/`<PressableScale>` pour ne dépendre
 * d'aucun provider (le boundary peut se monter hors de GestureHandlerRootView).
 * `useTheme()` ne lit que `useColorScheme()`, donc reste sûr ici.
 */
export function ErrorBoundary({ error, retry }: ErrorBoundaryProps) {
  const theme = useTheme();
  return (
    <View
      style={{
        flex: 1,
        backgroundColor: theme.background,
        alignItems: 'center',
        justifyContent: 'center',
        padding: 32,
        gap: 14,
      }}>
      <MaterialCommunityIcons name="alert-circle-outline" size={56} color={theme.danger} />
      <Text style={{ ...Type.headline, color: theme.text, textAlign: 'center' }}>
        Une erreur est survenue
      </Text>
      <Text style={{ ...Type.body, color: theme.textSecondary, textAlign: 'center' }}>
        {"L'application a rencontré un problème inattendu. Vos données enregistrées sont intactes."}
      </Text>
      {__DEV__ ? (
        <Text selectable style={{ color: theme.textMuted, fontSize: 12, textAlign: 'center' }}>
          {error.message}
        </Text>
      ) : null}
      <Pressable
        onPress={retry}
        accessibilityRole="button"
        accessibilityLabel="Réessayer"
        style={{
          marginTop: 8,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 8,
          backgroundColor: theme.accent,
          paddingVertical: 14,
          paddingHorizontal: 24,
          borderRadius: Radius.lg,
          borderCurve: 'continuous',
        }}>
        <MaterialCommunityIcons name="refresh" size={20} color="#fff" />
        <Text style={{ color: '#fff', fontWeight: '800', fontSize: 16 }}>Réessayer</Text>
      </Pressable>
    </View>
  );
}

export default function RootLayout() {
  const scheme = useColorScheme();

  // Re-arme les rappels de séance au lancement : les notifications planifiées
  // sont effacées au redémarrage de l'appareil, donc on les reprogramme à
  // chaque ouverture pour que le programme reste rappelé de façon fiable.
  // Sans effet (et sans permission demandée) si les rappels sont désactivés.
  useEffect(() => {
    applyNotifications();
  }, []);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
        <HeartRateProvider>
          <CadenceSpeedProvider>
            <BackupProvider>
              <Stack>
                <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
                <Stack.Screen
                  name="velo"
                  options={{ headerShown: false, presentation: 'fullScreenModal', animation: 'fade' }}
                />
                <Stack.Screen
                  name="muscu"
                  options={{ headerShown: false, presentation: 'fullScreenModal', animation: 'fade' }}
                />
                <Stack.Screen name="session/[id]" options={{ title: 'Séance' }} />
                <Stack.Screen
                  name="session/map"
                  options={{ headerShown: false, presentation: 'fullScreenModal', animation: 'fade' }}
                />
                <Stack.Screen name="progression" options={{ title: 'Progression' }} />
                <Stack.Screen name="exercise/[name]" options={{ title: 'Progression' }} />
              </Stack>
              <StatusBar style="auto" />
            </BackupProvider>
          </CadenceSpeedProvider>
        </HeartRateProvider>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}
