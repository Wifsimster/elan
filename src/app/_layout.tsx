import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useColorScheme } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { CadenceSpeedProvider } from '@/hooks/use-cadence-speed';
import { HeartRateProvider } from '@/hooks/use-heart-rate';

export const unstable_settings = {
  anchor: '(tabs)',
};

export default function RootLayout() {
  const scheme = useColorScheme();

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
        <HeartRateProvider>
          <CadenceSpeedProvider>
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
            </Stack>
            <StatusBar style="auto" />
          </CadenceSpeedProvider>
        </HeartRateProvider>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}
