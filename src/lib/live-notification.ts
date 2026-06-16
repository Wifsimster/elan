// Notification persistante « séance en cours » (100 % locale, aucun réseau).
//
// Pendant une séance vélo ou muscu, une notification permanente s'affiche dans
// le volet : un appui ramène l'utilisateur à son écran de séance en un geste
// (utile après être sorti de l'app pour traiter une autre notification). Volet
// de réglages distinct des rappels planifiés (lib/notifications.ts) : ici on
// présente / efface immédiatement sur un canal silencieux dédié, pas de
// planification AlarmManager.
//
// Best-effort comme lib/notifications.ts : Android uniquement, try/catch
// silencieux, canal créé paresseusement. Ne lève jamais, ne bloque jamais le
// démarrage / l'arrêt d'une séance.

import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

const LIVE_CHANNEL_ID = 'session';
const LIVE_NOTIFICATION_ID = 'live-session';

export type LiveKind = 'velo' | 'muscu';

// Canal silencieux et discret (importance basse : reste dans le volet sans son
// ni vibration ni heads-up). Créé à la demande, Android uniquement.
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

/**
 * Présente (ou remplace) l'unique notification persistante de séance. Identifiant
 * stable : ré-appeler ne crée pas de doublon, il met simplement à jour celle qui
 * existe (utilisé par la reprise d'une séance muscu). `data.route` porte la cible
 * d'appui, résolue dans _layout.tsx. Best-effort : échec silencieux.
 */
export async function showLiveSessionNotification(kind: LiveKind): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    await ensureLiveChannel();
    await Notifications.scheduleNotificationAsync({
      identifier: LIVE_NOTIFICATION_ID,
      content: {
        title: kind === 'velo' ? 'Sortie vélo en cours' : 'Séance muscu en cours',
        body: 'Touche pour revenir à ta séance.',
        sticky: true,
        autoDismiss: false,
        data: { route: kind === 'velo' ? '/velo' : '/muscu' },
      },
      // Canal seul = présentation immédiate et persistante (pas de planification).
      trigger: { channelId: LIVE_CHANNEL_ID },
    });
  } catch {
    // Permission refusée, plateforme non supportée… : pas de notification, on
    // continue sans bloquer la séance.
  }
}

/**
 * Efface la notification persistante de séance, qu'elle soit présentée ou (par
 * sécurité) encore en file. À appeler à toutes les sorties de séance. Best-effort.
 */
export async function clearLiveSessionNotification(): Promise<void> {
  if (Platform.OS !== 'android') return;
  try {
    await Notifications.dismissNotificationAsync(LIVE_NOTIFICATION_ID);
    await Notifications.cancelScheduledNotificationAsync(LIVE_NOTIFICATION_ID);
  } catch {
    // Déjà effacée / jamais présentée : rien à faire.
  }
}
