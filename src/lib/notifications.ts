// Rappels du soir (opt-in) : une notification locale la veille au soir d'une
// séance planifiée (muscu ou vélo). Pas de relance, pas de nudge d'inactivité,
// pas de récap. La planification est 100 % locale (AlarmManager côté Android,
// aucune connexion réseau, aucun service push).

import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { getSetting, setSetting } from '@/lib/db';
import { getEffectiveWeekPlan, type PlannedSession } from '@/lib/program';

const SETTING_KEY = 'notifications';
const CHANNEL_ID = 'routine';

export type NotificationConfig = {
  enabled: boolean;
  /** Heure d'envoi du rappel, 0-23 (les minutes restent à 0). */
  hour: number;
};

export const DEFAULT_NOTIFICATION_CONFIG: NotificationConfig = {
  enabled: false,
  hour: 21,
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
    // valeur corrompue : on retombe sur le défaut
  }
  return DEFAULT_NOTIFICATION_CONFIG;
}

export async function saveNotificationConfig(cfg: NotificationConfig): Promise<void> {
  await setSetting(SETTING_KEY, JSON.stringify(cfg));
}

async function ensureChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
    name: 'Rappels du soir',
    importance: Notifications.AndroidImportance.DEFAULT,
    enableVibrate: true,
    showBadge: false,
  });
}

/** Demande la permission OS (Android 13+ POST_NOTIFICATIONS). */
export async function requestNotificationPermission(): Promise<boolean> {
  const res = await Notifications.requestPermissionsAsync();
  return res.granted || res.status === 'granted';
}

function describeTomorrow(plan: PlannedSession): { title: string; body: string } | null {
  if (plan.kind === 'repos') return null;
  if (plan.kind === 'velo') {
    return {
      title: `Demain : ${plan.label}`,
      body: 'Pense à préparer le vélo et la ceinture.',
    };
  }
  return {
    title: `Demain : ${plan.label}`,
    body: 'Pense à sortir les haltères.',
  };
}

/**
 * Re-planifie toutes les notifications de routine selon la config et le planning.
 * À appeler après tout changement de config ou de planning. Idempotent :
 * annule l'ensemble des notifications planifiées par l'app avant de re-créer.
 */
export async function applyNotifications(): Promise<void> {
  if (Platform.OS === 'web') return;
  try {
    await Notifications.cancelAllScheduledNotificationsAsync();
    const cfg = await getNotificationConfig();
    if (!cfg.enabled) return;

    const plan = await getEffectiveWeekPlan();
    await ensureChannel();

    // plan[0] = lundi ... plan[6] = dimanche.
    // Pour chaque jour i, si i+1 est une séance, on planifie un rappel le soir de i.
    // expo-notifications WEEKLY : weekday 1 = dimanche, 2 = lundi, ..., 7 = samedi.
    for (let i = 0; i < 7; i++) {
      const next = plan[(i + 1) % 7];
      const content = describeTomorrow(next);
      if (!content) continue;
      const weekday = i === 6 ? 1 : i + 2; // i = 6 (dimanche soir) -> dimanche
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
    // Échec silencieux (permission refusée, plateforme non supportée…).
    // Pas de notification = comportement par défaut acceptable.
  }
}
