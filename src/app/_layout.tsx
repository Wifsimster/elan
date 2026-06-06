import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { BackupProvider } from '@/hooks/use-backup';
import { CadenceSpeedProvider } from '@/hooks/use-cadence-speed';
import { HeartRateProvider } from '@/hooks/use-heart-rate';
import { applyNotifications } from '@/lib/notifications';

export const unstable_settings = {
  anchor: '(tabs)',
};

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
