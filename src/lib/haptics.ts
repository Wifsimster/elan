/**
 * Retours haptiques du design system PULSE. Toute interaction marquante en
 * déclenche un (voir DESIGN.md § Mouvement & haptique). Volontairement « fire
 * and forget » : un échec (web, appareil sans moteur haptique) est ignoré.
 */
import * as Haptics from 'expo-haptics';

const fire = (p: Promise<void>) => {
  p.catch(() => {});
};

export const haptics = {
  /** Appui sur un contrôle secondaire, changement de sélection (chips, steppers). */
  selection: () => fire(Haptics.selectionAsync()),
  /** Appui sur une action principale. */
  light: () => fire(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light)),
  /** Démarrage / bascule d'état d'effort (start, pause). */
  medium: () => fire(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium)),
  /** Action lourde ou destructrice confirmée. */
  heavy: () => fire(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy)),
  /** Séance enregistrée, objectif atteint. */
  success: () => fire(Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)),
  /** Erreur, action refusée. */
  error: () => fire(Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error)),
};
