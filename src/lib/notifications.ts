import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { getSetting, setSetting } from '@/lib/db';
import { getEffectiveWeekPlan, templateById, type PlannedSession } from '@/lib/program';

const SETTING_KEY = 'notifications';
const CHANNEL_ID = 'routine';
const PROGRESSION_CHANNEL_ID = 'progression';

export type NotificationConfig = {
  enabled: boolean;
  hour: number;
};

export const DEFAULT_NOTIFICATION_CONFIG: NotificationConfig = {
  enabled: false,
  hour: 12,
};

export async function getNotificationConfig(): Promise<NotificationConfig> {
  const raw = await getSetting(SETTING_KEY);
  if (!raw) return DEFAULT_NOTIFICATION_CONFIG;
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === 'object' && parsed != null) {
      return {
        enabled: typeof parsed.enabled === 'boolean' ? parsed.enabled : false,
        hour:
          typeof parsed.hour === 'number' && parsed.hour >= 0 && parsed.hour <= 23
            ? Math.floor(parsed.hour)
            : DEFAULT_NOTIFICATION_CONFIG.hour,
      };
    }
  } catch {
  }
  return DEFAULT_NOTIFICATION_CONFIG;
}

export async function saveNotificationConfig(cfg: NotificationConfig): Promise<void> {
  await setSetting(SETTING_KEY, JSON.stringify(cfg));
}

async function ensureChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
    name: 'Rappels de séance',
    importance: Notifications.AndroidImportance.DEFAULT,
    enableVibrate: true,
    showBadge: false,
  });
}

export async function requestNotificationPermission(): Promise<boolean> {
  const res = await Notifications.requestPermissionsAsync();
  return res.granted || res.status === 'granted';
}

async function ensureProgressionChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(PROGRESSION_CHANNEL_ID, {
    name: 'Progression du programme',
    importance: Notifications.AndroidImportance.DEFAULT,
    enableVibrate: true,
    showBadge: false,
  });
}

export async function notifyProgression(title: string, body: string): Promise<void> {
  if (Platform.OS === 'web') return;
  try {
    const perm = await Notifications.getPermissionsAsync();
    const granted = perm.granted || perm.status === 'granted';
    if (!granted) return;
    await ensureProgressionChannel();
    await Notifications.scheduleNotificationAsync({
      content: { title, body, data: { route: '/progression' } },
      trigger: null,
    });
  } catch {
  }
}

export function planIndexToWeekday(index: number): number {
  return index === 6 ? 1 : index + 2;
}

export function describeToday(plan: PlannedSession): { title: string; body: string } | null {
  if (plan.kind === 'repos') return null;
  if (plan.kind !== 'muscu') {
    return {
      title: `Aujourd'hui : ${plan.label}`,
      body:
        plan.kind === 'velo'
          ? 'Pense à préparer le vélo et la ceinture.'
          : 'Pense à préparer les chaussures et la ceinture.',
    };
  }
  const exercises = templateById(plan.templateId)?.exercises ?? [];
  const body =
    exercises.length > 0
      ? `Au programme : ${exercises.map((e) => e.name).join(', ')}.`
      : 'Pense à sortir les haltères.';
  return {
    title: `Aujourd'hui : ${plan.label}`,
    body,
  };
}

export async function applyNotifications(): Promise<void> {
  if (Platform.OS === 'web') return;
  try {
    await Notifications.cancelAllScheduledNotificationsAsync();
    const cfg = await getNotificationConfig();
    if (!cfg.enabled) return;

    const plan = await getEffectiveWeekPlan();
    await ensureChannel();

    for (let i = 0; i < 7; i++) {
      const today = plan[i];
      const content = describeToday(today);
      if (!content) continue;
      const weekday = planIndexToWeekday(i);
      await Notifications.scheduleNotificationAsync({
        content: {
          title: content.title,
          body: content.body,
        },
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.WEEKLY,
          weekday,
          hour: cfg.hour,
          minute: 0,
          channelId: CHANNEL_ID,
        },
      });
    }
  } catch {
  }
}
