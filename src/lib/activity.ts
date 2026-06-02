import { MaterialCommunityIcons } from '@expo/vector-icons';

import { Colors } from '@/constants/theme';
import type { ActivityType } from '@/lib/types';

type Meta = {
  label: string;
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  colorKey: 'velo' | 'muscu';
};

export const ACTIVITY_META: Record<ActivityType, Meta> = {
  velo: { label: 'Vélo', icon: 'bike', colorKey: 'velo' },
  muscu: { label: 'Musculation', icon: 'dumbbell', colorKey: 'muscu' },
};

export function activityColor(type: ActivityType, scheme: 'light' | 'dark') {
  return Colors[scheme][ACTIVITY_META[type].colorKey];
}
