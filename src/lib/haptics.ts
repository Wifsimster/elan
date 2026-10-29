import * as Haptics from 'expo-haptics';

const fire = (p: Promise<void>) => {
  p.catch(() => {});
};

export const haptics = {
  selection: () => fire(Haptics.selectionAsync()),
  light: () => fire(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light)),
  medium: () => fire(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium)),
  heavy: () => fire(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy)),
  success: () => fire(Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)),
  error: () => fire(Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error)),
};
