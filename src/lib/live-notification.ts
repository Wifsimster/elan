import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { ACTIVITY_META } from '@/lib/activity';
import type { ActivityType } from '@/lib/types';

const LIVE_CHANNEL_ID = 'session';
const LIVE_NOTIFICATION_ID = 'live-session';

export type LiveKind = ActivityType;

export async function ensureNotificationPermission(): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    const { status } = await Notifications.getPermissionsAsync();
    if (status !== 'granted') await Notifications.requestPermissionsAsync();
  } catch {
  }
}

async function ensureLiveChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(LIVE_CHANNEL_ID, {
    name: 'Séance en cours',
    importance: Notifications.AndroidImportance.LOW,
    enableVibrate: false,
    showBadge: false,
    sound: null,
  });
}

export async function showLiveSessionNotification(kind: LiveKind): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    await ensureLiveChannel();
    await Notifications.scheduleNotificationAsync({
      identifier: LIVE_NOTIFICATION_ID,
      content: {
        title: kind === 'muscu' ? 'Séance muscu en cours' : `${ACTIVITY_META[kind].label} en cours`,
        body: 'Touche pour revenir à ta séance.',
        sticky: true,
        autoDismiss: false,
        data: { route: kind === 'muscu' ? '/muscu' : '/sortie' },
      },
      trigger: { channelId: LIVE_CHANNEL_ID },
    });
  } catch {
  }
}

export async function clearLiveSessionNotification(): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    await Notifications.dismissNotificationAsync(LIVE_NOTIFICATION_ID);
    await Notifications.cancelScheduledNotificationAsync(LIVE_NOTIFICATION_ID);
  } catch {
  }
}
