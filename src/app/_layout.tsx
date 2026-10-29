import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as Notifications from 'expo-notifications';
import { DarkTheme, DefaultTheme, type ErrorBoundaryProps, Stack, ThemeProvider, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Pressable, Text, useColorScheme, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { Radius, Type } from '@/constants/theme';
import { BackupProvider } from '@/hooks/use-backup';
import { CadenceSpeedProvider } from '@/hooks/use-cadence-speed';
import { HeartRateProvider } from '@/hooks/use-heart-rate';
import { useTheme } from '@/hooks/use-theme';
import { runWeeklyProgressionIfDue } from '@/lib/auto-progression';
import { reconcileOrphanGpsTask } from '@/lib/gps-task';
import { clearLiveSessionNotification } from '@/lib/live-notification';
import { applyNotifications } from '@/lib/notifications';
import { recoverOrphanSessions } from '@/lib/session-recovery';
import { nowMs } from '@/lib/time';

export const unstable_settings = {
  anchor: '(tabs)',
};

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: false,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

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
        {"L'application a rencontré un problème inattendu. Tes données enregistrées sont intactes."}
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
  const router = useRouter();

  useEffect(() => {
    applyNotifications();
    clearLiveSessionNotification();
    recoverOrphanSessions();
    reconcileOrphanGpsTask();
    runWeeklyProgressionIfDue(nowMs());
  }, []);

  useEffect(() => {
    const handle = (route: unknown) => {
      if (route === '/muscu' || route === '/progression') {
        router.navigate(route);
      }
    };
    const sub = Notifications.addNotificationResponseReceivedListener((resp) => {
      handle(resp.notification.request.content.data?.route);
    });
    const last = Notifications.getLastNotificationResponse();
    if (last) handle(last.notification.request.content.data?.route);
    return () => sub.remove();
  }, [router]);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
        <HeartRateProvider>
          <CadenceSpeedProvider>
            <BackupProvider>
              <Stack>
                <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
                <Stack.Screen
                  name="sortie"
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
                <Stack.Screen name="poids" options={{ title: 'Poids' }} />
                <Stack.Screen name="exercise/[name]" options={{ title: 'Progression' }} />
                <Stack.Screen name="exercises" options={{ title: 'Catalogue d’exercices' }} />
              </Stack>
              <StatusBar style="auto" />
            </BackupProvider>
          </CadenceSpeedProvider>
        </HeartRateProvider>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}
